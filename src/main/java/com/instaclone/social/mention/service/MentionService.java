package com.instaclone.social.mention.service;

import com.instaclone.common.util.TextEntities;
import com.instaclone.notification.enums.NotificationType;
import com.instaclone.notification.event.NotificationEvent;
import com.instaclone.post.entity.Post;
import com.instaclone.social.block.repository.UserBlockRepository;
import com.instaclone.user.entity.User;
import com.instaclone.user.repository.UserRepository;
import com.instaclone.user.service.ProfileVisibilityService;
import java.util.HashSet;
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns @mentions in captions and comments into notifications. A mention is never stored — only
 * its notification is — so editing text later can't "un-mention" anyone, but a caption edit only
 * notifies people newly added by the edit, not everyone already in it.
 *
 * <p>Nobody is notified who couldn't open the post (private account they don't follow, or blocked
 * by either the author or the post owner), and never the author themselves.
 */
@Service
public class MentionService {

    private final UserRepository userRepository;
    private final ProfileVisibilityService profileVisibilityService;
    private final UserBlockRepository blockRepository;
    private final ApplicationEventPublisher eventPublisher;

    public MentionService(
            UserRepository userRepository,
            ProfileVisibilityService profileVisibilityService,
            UserBlockRepository blockRepository,
            ApplicationEventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.profileVisibilityService = profileVisibilityService;
        this.blockRepository = blockRepository;
        this.eventPublisher = eventPublisher;
    }

    /** @param previousCaption the caption before an edit, or null for a brand-new post */
    @Transactional
    public void notifyPostMentions(User author, Post post, String previousCaption) {
        Set<String> mentioned = new HashSet<>(TextEntities.extractMentions(post.getCaption()));
        mentioned.removeAll(TextEntities.extractMentions(previousCaption));
        notifyAll(author, post, mentioned, NotificationType.MENTION_POST, Set.of());
    }

    /** @param alreadyNotified users who already get a COMMENT notification for this comment (the
     * post owner, a replied-to author) — mentioning them shouldn't send a second one. */
    @Transactional
    public void notifyCommentMentions(User author, Post post, String text, Set<Long> alreadyNotified) {
        notifyAll(author, post, TextEntities.extractMentions(text), NotificationType.MENTION_COMMENT, alreadyNotified);
    }

    private void notifyAll(User author, Post post, Set<String> usernames, NotificationType type, Set<Long> skip) {
        if (usernames.isEmpty()) {
            return;
        }
        for (User mentioned : userRepository.findAllByUsernameIn(usernames)) {
            Long id = mentioned.getId();
            if (id.equals(author.getId()) || skip.contains(id)) {
                continue;
            }
            if (blockRepository.existsEitherWay(author.getId(), id)
                    || !profileVisibilityService.isVisible(post.getUser(), mentioned)) {
                continue;
            }
            // Targets the post for both kinds, so the notification links to /p/{postId}.
            eventPublisher.publishEvent(new NotificationEvent(id, author.getId(), type, "POST", post.getId()));
        }
    }
}
