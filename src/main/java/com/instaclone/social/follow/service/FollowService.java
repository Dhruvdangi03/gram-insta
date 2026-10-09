package com.instaclone.social.follow.service;

import com.instaclone.common.exception.BadRequestException;
import com.instaclone.common.exception.ConflictException;
import com.instaclone.common.exception.ForbiddenException;
import com.instaclone.common.exception.NotFoundException;
import com.instaclone.notification.enums.NotificationType;
import com.instaclone.notification.event.NotificationEvent;
import com.instaclone.notification.service.FollowRequestNotificationCleaner;
import com.instaclone.social.block.repository.UserBlockRepository;
import com.instaclone.social.follow.dto.FollowStatusResponse;
import com.instaclone.social.follow.entity.Follow;
import com.instaclone.social.follow.enums.FollowStatus;
import com.instaclone.social.follow.repository.FollowRepository;
import com.instaclone.user.entity.User;
import com.instaclone.user.repository.UserRepository;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FollowService {

    private final FollowRepository followRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final FollowRequestNotificationCleaner followRequestNotifications;
    private final UserBlockRepository blockRepository;

    public FollowService(
            FollowRepository followRepository,
            UserRepository userRepository,
            ApplicationEventPublisher eventPublisher,
            FollowRequestNotificationCleaner followRequestNotifications,
            UserBlockRepository blockRepository) {
        this.followRepository = followRepository;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
        this.followRequestNotifications = followRequestNotifications;
        this.blockRepository = blockRepository;
    }

    @Transactional
    public FollowStatusResponse follow(Long followerId, String followeeUsername) {
        User followee = userRepository.findByUsername(followeeUsername).orElseThrow(() -> new NotFoundException("User not found"));
        if (followee.getId().equals(followerId)) {
            throw new BadRequestException("You cannot follow yourself");
        }
        // Reported as not-found rather than forbidden so a blocked user can't tell they're blocked.
        if (blockRepository.existsEitherWay(followerId, followee.getId())) {
            throw new NotFoundException("User not found");
        }
        if (followRepository.findByFollowerIdAndFolloweeId(followerId, followee.getId()).isPresent()) {
            throw new ConflictException("Already following, or a follow request is already pending");
        }

        User follower = userRepository.findById(followerId).orElseThrow(() -> new NotFoundException("User not found"));

        Follow follow = new Follow();
        follow.setFollower(follower);
        follow.setFollowee(followee);
        follow.setStatus(followee.isPrivate() ? FollowStatus.PENDING : FollowStatus.ACCEPTED);
        follow.setCreatedAt(Instant.now());
        followRepository.save(follow);

        // A public account's follow takes effect immediately ("followed you"); a private account's
        // request is merely PENDING until approved ("requested to follow you") — the two need
        // distinct notification types so the recipient (and the frontend copy) can tell them apart.
        NotificationType notificationType =
                follow.getStatus() == FollowStatus.ACCEPTED ? NotificationType.FOLLOW : NotificationType.FOLLOW_REQUEST;
        eventPublisher.publishEvent(
                new NotificationEvent(followee.getId(), followerId, notificationType, "USER", followerId));

        return new FollowStatusResponse(follow.getStatus());
    }

    @Transactional
    public void unfollow(Long followerId, String followeeUsername) {
        User followee = userRepository.findByUsername(followeeUsername).orElseThrow(() -> new NotFoundException("User not found"));
        followRepository.findByFollowerIdAndFolloweeId(followerId, followee.getId()).ifPresent(follow -> {
            followRepository.delete(follow);
            if (follow.getStatus() == FollowStatus.PENDING) {
                // Withdrawing a request shouldn't leave the owner a "requested to follow you"
                // notification that links to a requests list it's no longer in.
                followRequestNotifications.clear(followee.getId(), followerId);
            }
        });
    }

    /** The account owner removes someone who currently follows them (an ACCEPTED follow only —
     * declining a still-PENDING request is rejectFollowRequest). Silent to the removed user. */
    @Transactional
    public void removeFollower(Long ownerId, String followerUsername) {
        User follower = userRepository.findByUsername(followerUsername).orElseThrow(() -> new NotFoundException("User not found"));
        Follow follow = followRepository
                .findByFollowerIdAndFolloweeId(follower.getId(), ownerId)
                .filter(f -> f.getStatus() == FollowStatus.ACCEPTED)
                .orElseThrow(() -> new NotFoundException("This user isn't following you"));
        followRepository.delete(follow);
    }

    @Transactional
    public FollowStatusResponse acceptFollowRequest(Long approverId, String followerUsername) {
        User follower = userRepository.findByUsername(followerUsername).orElseThrow(() -> new NotFoundException("User not found"));
        Follow follow = followRepository
                .findByFollowerIdAndFolloweeId(follower.getId(), approverId)
                .filter(f -> f.getStatus() == FollowStatus.PENDING)
                .orElseThrow(() -> new NotFoundException("No pending follow request from this user"));
        follow.accept();
        followRequestNotifications.clear(approverId, follower.getId());

        // Distinct from NotificationType.FOLLOW: the approver didn't follow the requester back
        // (no reciprocal Follow row is created here), they just approved an existing request.
        eventPublisher.publishEvent(new NotificationEvent(
                follower.getId(), approverId, NotificationType.FOLLOW_REQUEST_ACCEPTED, "USER", approverId));

        return new FollowStatusResponse(follow.getStatus());
    }

    @Transactional
    public void rejectFollowRequest(Long approverId, String followerUsername) {
        User follower = userRepository.findByUsername(followerUsername).orElseThrow(() -> new NotFoundException("User not found"));
        Follow follow = followRepository
                .findByFollowerIdAndFolloweeId(follower.getId(), approverId)
                .filter(f -> f.getStatus() == FollowStatus.PENDING)
                .orElseThrow(() -> new NotFoundException("No pending follow request from this user"));
        followRepository.delete(follow);
        followRequestNotifications.clear(approverId, follower.getId());
    }
}
