package com.instaclone.feed.service;

import com.instaclone.common.pagination.Cursor;
import com.instaclone.common.pagination.CursorPage;
import com.instaclone.common.pagination.RankCursor;
import com.instaclone.post.dto.PostResponse;
import com.instaclone.post.entity.Post;
import com.instaclone.post.repository.PostRepository;
import com.instaclone.post.service.PostService;
import com.instaclone.social.follow.repository.FollowRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import com.instaclone.social.block.repository.UserBlockRepository;
import com.instaclone.social.mute.repository.UserMuteRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Plain DB query for now, matching the build plan's Phase 1 scope — the home feed only moves
 * onto a Redis-backed fan-out-on-write/read hybrid in Phase 6, once there's real volume to
 * justify it.
 */
@Service
public class FeedService {

    private static final int EXPLORE_WINDOW_DAYS = 7;

    private final FollowRepository followRepository;
    private final PostRepository postRepository;
    private final PostService postService;
    private final UserBlockRepository blockRepository;
    private final UserMuteRepository muteRepository;

    public FeedService(
            FollowRepository followRepository,
            PostRepository postRepository,
            PostService postService,
            UserBlockRepository blockRepository,
            UserMuteRepository muteRepository) {
        this.followRepository = followRepository;
        this.postRepository = postRepository;
        this.postService = postService;
        this.blockRepository = blockRepository;
        this.muteRepository = muteRepository;
    }

    /** Accounts whose content must stay out of the viewer's feeds: blocks in either direction plus
     * the viewer's own mutes. */
    private Set<Long> hiddenUserIds(Long viewerId) {
        Set<Long> hidden = new HashSet<>(blockRepository.findBlockRelatedUserIds(viewerId));
        hidden.addAll(muteRepository.findMutedIds(viewerId));
        return hidden;
    }

    @Transactional(readOnly = true)
    public CursorPage<PostResponse> getHomeFeed(Long viewerId, String cursor, int limit) {
        List<Long> followedIds = new ArrayList<>(followRepository.findAcceptedFolloweeIds(viewerId));
        followedIds.removeAll(hiddenUserIds(viewerId));
        if (followedIds.isEmpty()) {
            return new CursorPage<>(List.of(), null, false);
        }

        List<Long> excludedPostIds = excludedPostIds(viewerId);
        Cursor decoded = cursor == null ? null : Cursor.decode(cursor);
        List<Post> rows = decoded == null
                ? postRepository.findFirstPageByUserIds(followedIds, excludedPostIds, limit + 1)
                : postRepository.findPageByUserIdsAfterCursor(
                        followedIds, excludedPostIds, decoded.createdAt(), decoded.id(), limit + 1);

        return postService.toPage(rows, limit, viewerId);
    }

    /** Post ids to keep out of the home/explore feeds. Nothing is excluded at the moment, but the
     * feed queries keep the NOT IN hook (applied inside the native query, so it can't shrink the
     * limit+1 lookahead window CursorPage.of relies on). Always non-empty: see the sentinel. */
    private List<Long> excludedPostIds(Long viewerId) {
        List<Long> ids = new ArrayList<>();
        ids.add(-1L); // sentinel: a native "NOT IN ()" with an empty list is invalid SQL
        return ids;
    }

    /**
     * Most-liked posts from public accounts the viewer doesn't already follow, over the trailing
     * window — the one global/algorithmic surface in Phase 2. Deliberately checks is_private
     * directly rather than "not followed": a private account must never leak into a discovery
     * feed for a non-follower, the same class of bug fixed twice in the Phase 1 review.
     */
    @Transactional(readOnly = true)
    public CursorPage<PostResponse> getExploreFeed(Long viewerId, String cursor, int limit) {
        List<Long> excludedIds = new ArrayList<>(followRepository.findAcceptedFolloweeIds(viewerId));
        excludedIds.add(viewerId);
        excludedIds.addAll(hiddenUserIds(viewerId));
        List<Long> excludedPostIds = excludedPostIds(viewerId);
        Instant since = Instant.now().minus(EXPLORE_WINDOW_DAYS, ChronoUnit.DAYS);

        RankCursor decoded = cursor == null ? null : RankCursor.decode(cursor);
        List<Post> rows = decoded == null
                ? postRepository.findExploreFirstPage(excludedIds, excludedPostIds, since, limit + 1)
                : postRepository.findExploreAfterCursor(
                        excludedIds, excludedPostIds, since, decoded.rank(), decoded.id(), limit + 1);

        CursorPage<Post> page =
                CursorPage.of(rows, limit, p -> new RankCursor(p.getLikeCount(), p.getId()).encode());
        List<PostResponse> items = postService.enrich(page.items(), viewerId);
        return new CursorPage<>(items, page.nextCursor(), page.hasMore());
    }
}
