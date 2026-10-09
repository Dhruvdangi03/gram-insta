package com.instaclone.user.service;

import com.instaclone.social.block.repository.UserBlockRepository;
import com.instaclone.social.follow.enums.FollowStatus;
import com.instaclone.social.follow.repository.FollowRepository;
import com.instaclone.user.entity.User;
import org.springframework.stereotype.Service;

/**
 * The one place the private-account visibility rule is enforced — every path that reads a
 * user's content (posts, comments, likes, followers/following) must go through this instead of
 * re-deriving the rule locally. A block in either direction hides the account entirely, public or
 * private, so every one of those paths also honours blocks through here.
 */
@Service
public class ProfileVisibilityService {

    private final FollowRepository followRepository;
    private final UserBlockRepository blockRepository;

    public ProfileVisibilityService(FollowRepository followRepository, UserBlockRepository blockRepository) {
        this.followRepository = followRepository;
        this.blockRepository = blockRepository;
    }

    public boolean isVisible(User author, User viewer) {
        if (viewer != null
                && !viewer.getId().equals(author.getId())
                && blockRepository.existsEitherWay(viewer.getId(), author.getId())) {
            return false;
        }
        boolean viewerFollowsAuthor = viewer != null
                && followRepository.existsByFollowerIdAndFolloweeIdAndStatus(
                        viewer.getId(), author.getId(), FollowStatus.ACCEPTED);
        return author.isVisibleTo(viewer, viewerFollowsAuthor);
    }
}
