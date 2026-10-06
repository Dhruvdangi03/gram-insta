package com.instaclone.user;

import com.instaclone.social.follow.FollowRepository;
import com.instaclone.social.follow.FollowStatus;
import org.springframework.stereotype.Service;

/**
 * The one place the private-account visibility rule is enforced — every path that reads a
 * user's content (posts, comments, likes, followers/following) must go through this instead of
 * re-deriving the rule locally. Also the one place a block denies content access, for the same reason.
 */
@Service
public class ProfileVisibilityService {

    private final FollowRepository followRepository;

    public ProfileVisibilityService(FollowRepository followRepository) {
        this.followRepository = followRepository;
    }

    public boolean isVisible(User author, User viewer) {
        // Directional: a user who blocks someone keeps full read access to that person's content
        // (so they can still find the Unblock action) — only the blocked party loses access.
        // if (viewer != null && moderationService.isBlockedBy(author.getId(), viewer.getId())) {
        //     return false;
        // }
        boolean viewerFollowsAuthor = viewer != null
                && followRepository.existsByFollowerIdAndFolloweeIdAndStatus(
                        viewer.getId(), author.getId(), FollowStatus.ACCEPTED);
        return author.isVisibleTo(viewer, viewerFollowsAuthor);
    }
}
