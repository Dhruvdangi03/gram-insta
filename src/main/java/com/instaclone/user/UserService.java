package com.instaclone.user;

import com.instaclone.common.Cursor;
import com.instaclone.common.CursorPage;
import com.instaclone.common.ForbiddenException;
import com.instaclone.common.NotFoundException;
import com.instaclone.notification.FollowRequestNotificationCleaner;
import com.instaclone.post.PostRepository;
import com.instaclone.social.follow.FollowRepository;
import com.instaclone.social.follow.FollowStatus;
import com.instaclone.social.follow.FollowUserRow;
import com.instaclone.social.moderation.ModerationService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final FollowRepository followRepository;
    private final PostRepository postRepository;
    private final ProfileVisibilityService profileVisibilityService;
    private final ModerationService moderationService;
    private final FollowRequestNotificationCleaner followRequestNotifications;

    public UserService(
            UserRepository userRepository,
            FollowRepository followRepository,
            PostRepository postRepository,
            ProfileVisibilityService profileVisibilityService,
            ModerationService moderationService,
            FollowRequestNotificationCleaner followRequestNotifications) {
        this.userRepository = userRepository;
        this.followRepository = followRepository;
        this.postRepository = postRepository;
        this.profileVisibilityService = profileVisibilityService;
        this.moderationService = moderationService;
        this.followRequestNotifications = followRequestNotifications;
    }

    public User findByUsernameOrThrow(String username) {
        return userRepository.findByUsername(username).orElseThrow(() -> new NotFoundException("User not found"));
    }

    public User findByIdOrThrow(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new NotFoundException("User not found"));
    }

    public UserProfileResponse getProfile(String username, Long viewerId) {
        User target = findByUsernameOrThrow(username);
        // A block hides the account entirely (looks like it doesn't exist) for the blocked party,
        // unlike a private account (which still shows its header + a gate) — but directional, not
        // mutual: the person who did the blocking keeps full profile access so they can still
        // reach the Unblock action.
        if (viewerId != null && moderationService.isBlockedBy(target.getId(), viewerId)) {
            throw new NotFoundException("User not found");
        }
        return toProfileResponse(target, viewerId);
    }

    @Transactional
    public UserProfileResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = findByIdOrThrow(userId);
        boolean wasPrivate = user.isPrivate();
        if (request.fullName() != null) {
            user.setFullName(request.fullName());
        }
        if (request.bio() != null) {
            user.setBio(request.bio());
        }
        if (request.profilePictureUrl() != null) {
            user.setProfilePictureUrl(request.profilePictureUrl());
        }
        if (request.isPrivate() != null) {
            user.setPrivate(request.isPrivate());
        }
        if (request.isBusiness() != null) {
            user.setBusiness(request.isBusiness());
        }
        user.setUpdatedAt(Instant.now());
        // Search is plain Postgres ILIKE now (SearchService), filtered at query time by
        // u.is_private — so going private instantly drops this user's posts out of search results
        // with no separate retraction step needed, unlike the old Meilisearch-indexed approach.
        // Account just went public: any still-PENDING follow requests are no longer gating
        // anything (the content is visible to everyone now), so auto-approve them rather than
        // stranding the requesters on "Requested" forever.
        if (wasPrivate && !user.isPrivate()) {
            followRepository.acceptAllPendingForFollowee(user.getId());
            followRequestNotifications.clearAll(user.getId());
        }
        return toProfileResponse(user, userId);
    }

    @Transactional(readOnly = true)
    public InsightsResponse getInsights(Long userId) {
        User user = findByIdOrThrow(userId);
        if (!user.isBusiness()) {
            throw new ForbiddenException("Insights are only available for business accounts");
        }
        long postCount = postRepository.countByUserId(userId);
        long followerCount = followRepository.countByFolloweeIdAndStatus(userId, FollowStatus.ACCEPTED);
        long followingCount = followRepository.countByFollowerIdAndStatus(userId, FollowStatus.ACCEPTED);
        long totalLikes = postRepository.sumLikeCountByUserId(userId);
        long totalComments = postRepository.sumCommentCountByUserId(userId);
        return new InsightsResponse(postCount, followerCount, followingCount, totalLikes, totalComments);
    }

    public CursorPage<UserSummary> getFollowers(String username, Long viewerId, String cursor, int limit) {
        User target = findByUsernameOrThrow(username);
        assertVisible(target, viewerId);
        Cursor decoded = cursor == null ? null : Cursor.decode(cursor);
        List<FollowUserRow> rows = decoded == null
                ? followRepository.findFirstPageFollowers(target.getId(), limit + 1)
                : followRepository.findPageFollowersAfterCursor(
                        target.getId(), decoded.createdAt(), decoded.id(), limit + 1);
        return toUserSummaryPage(rows, limit);
    }

    /** Incoming pending follow requests on the caller's own (private) account — see
     * FollowRepository.findPendingFollowRequests. */
    @Transactional(readOnly = true)
    public List<UserSummary> getFollowRequests(Long userId, int limit) {
        return followRepository.findPendingFollowRequests(userId, limit).stream()
                .map(r -> new UserSummary(
                        r.getUserId(), r.getUsername(), r.getFullName(), r.getProfilePictureUrl(), r.getIsVerified()))
                .toList();
    }

    /** "Suggested for you" — public accounts the viewer doesn't already follow (or has a pending
     * request to), isn't blocked with, ranked by follower count. No pagination (a short, static
     * list for a sidebar), unlike every other listing here. */
    @Transactional(readOnly = true)
    public List<UserSummary> getSuggestions(Long viewerId, int limit) {
        List<Long> excludedIds = new ArrayList<>(followRepository.findAllFolloweeIds(viewerId));
        excludedIds.add(viewerId);
        excludedIds.addAll(moderationService.getBlockedEitherDirectionIds(viewerId));
        return userRepository.findSuggestions(excludedIds, limit).stream()
                .map(UserSummary::from)
                .toList();
    }

    public CursorPage<UserSummary> getFollowing(String username, Long viewerId, String cursor, int limit) {
        User target = findByUsernameOrThrow(username);
        assertVisible(target, viewerId);
        Cursor decoded = cursor == null ? null : Cursor.decode(cursor);
        List<FollowUserRow> rows = decoded == null
                ? followRepository.findFirstPageFollowing(target.getId(), limit + 1)
                : followRepository.findPageFollowingAfterCursor(
                        target.getId(), decoded.createdAt(), decoded.id(), limit + 1);
        return toUserSummaryPage(rows, limit);
    }

    private void assertVisible(User target, Long viewerId) {
        User viewer = findByIdOrThrow(viewerId);
        if (!profileVisibilityService.isVisible(target, viewer)) {
            throw new ForbiddenException("This account is private");
        }
    }

    private CursorPage<UserSummary> toUserSummaryPage(List<FollowUserRow> rows, int limit) {
        CursorPage<FollowUserRow> page =
                CursorPage.of(rows, limit, r -> new Cursor(r.getFollowCreatedAt(), r.getFollowId()).encode());
        List<UserSummary> items = page.items().stream()
                .map(r -> new UserSummary(
                        r.getUserId(), r.getUsername(), r.getFullName(), r.getProfilePictureUrl(), r.getIsVerified()))
                .toList();
        return new CursorPage<>(items, page.nextCursor(), page.hasMore());
    }

    private UserProfileResponse toProfileResponse(User target, Long viewerId) {
        long postCount = postRepository.countByUserId(target.getId());
        long followerCount = followRepository.countByFolloweeIdAndStatus(target.getId(), FollowStatus.ACCEPTED);
        long followingCount = followRepository.countByFollowerIdAndStatus(target.getId(), FollowStatus.ACCEPTED);

        ViewerRelationship relationship = ViewerRelationship.NOT_FOLLOWING;
        boolean viewerHasBlocked = false;
        boolean viewerHasRestricted = false;
        if (viewerId != null) {
            if (viewerId.equals(target.getId())) {
                relationship = ViewerRelationship.SELF;
            } else {
                relationship = followRepository
                        .findByFollowerIdAndFolloweeId(viewerId, target.getId())
                        .map(f -> f.getStatus() == FollowStatus.ACCEPTED
                                ? ViewerRelationship.FOLLOWING
                                : ViewerRelationship.REQUESTED)
                        .orElse(ViewerRelationship.NOT_FOLLOWING);
                viewerHasBlocked = moderationService.isBlockedEitherDirection(viewerId, target.getId());
                viewerHasRestricted = moderationService.isRestrictedBy(viewerId, target.getId());
            }
        }

        return new UserProfileResponse(
                target.getId(),
                target.getUsername(),
                target.getFullName(),
                target.getBio(),
                target.getProfilePictureUrl(),
                target.isPrivate(),
                target.isVerified(),
                target.isBusiness(),
                postCount,
                followerCount,
                followingCount,
                relationship,
                viewerHasBlocked,
                viewerHasRestricted);
    }
}
