package com.instaclone.user.service;

import com.instaclone.social.follow.enums.FollowStatus;
import com.instaclone.social.follow.repository.FollowRepository;
import com.instaclone.user.entity.User;
import org.springframework.stereotype.Service;

/**
 * The one place the private-account visibility rule is enforced — every path that reads a
 * user's content (posts, comments, likes, followers/following) must go through this instead of
 * re-deriving the rule locally.
 */
@Service
public class ProfileVisibilityService {

    private final FollowRepository followRepository;

    public ProfileVisibilityService(FollowRepository followRepository) {
        this.followRepository = followRepository;
    }

    public boolean isVisible(User author, User viewer) {
        boolean viewerFollowsAuthor = viewer != null
                && followRepository.existsByFollowerIdAndFolloweeIdAndStatus(
                        viewer.getId(), author.getId(), FollowStatus.ACCEPTED);
        return author.isVisibleTo(viewer, viewerFollowsAuthor);
    }
}
