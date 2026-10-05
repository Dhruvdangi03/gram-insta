package com.instaclone.story;

import com.instaclone.common.BadRequestException;
import com.instaclone.common.Cursor;
import com.instaclone.common.CursorPage;
import com.instaclone.common.ForbiddenException;
import com.instaclone.common.NotFoundException;
import com.instaclone.config.StorageProperties;
import com.instaclone.config.StoryProperties;
import com.instaclone.social.follow.FollowRepository;
import com.instaclone.user.ProfileVisibilityService;
import com.instaclone.user.User;
import com.instaclone.user.UserRepository;
import com.instaclone.user.UserSummary;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StoryService {

    private final StoryRepository storyRepository;
    private final UserRepository userRepository;
    private final FollowRepository followRepository;
    private final ProfileVisibilityService profileVisibilityService;
    private final StorageProperties storageProperties;
    private final StoryProperties storyProperties;
    private final StoryViewRepository storyViewRepository;
    private final StoryViewInserter storyViewInserter;

    public StoryService(
            StoryRepository storyRepository,
            UserRepository userRepository,
            FollowRepository followRepository,
            ProfileVisibilityService profileVisibilityService,
            StorageProperties storageProperties,
            StoryProperties storyProperties,
            StoryViewRepository storyViewRepository,
            StoryViewInserter storyViewInserter) {
        this.storyRepository = storyRepository;
        this.userRepository = userRepository;
        this.followRepository = followRepository;
        this.profileVisibilityService = profileVisibilityService;
        this.storageProperties = storageProperties;
        this.storyProperties = storyProperties;
        this.storyViewRepository = storyViewRepository;
        this.storyViewInserter = storyViewInserter;
    }

    @Transactional
    public StoryResponse createStory(Long userId, CreateStoryRequest request) {
        User author = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));

        if (!storageProperties.isOwnedUrl(request.mediaUrl(), userId)) {
            throw new BadRequestException("Media url must reference an object uploaded via /posts/upload-url");
        }

        Duration ttl = request.expiresInSeconds() != null
                ? Duration.ofSeconds(request.expiresInSeconds())
                : storyProperties.defaultTtl();
        if (ttl.compareTo(storyProperties.maxTtl()) > 0) {
            throw new BadRequestException("expiresInSeconds must be at most " + storyProperties.maxTtl().toSeconds());
        }

        Instant now = Instant.now();
        Story story = new Story();
        story.setUser(author);
        story.setMediaUrl(request.mediaUrl());
        story.setCreatedAt(now);
        story.setExpiresAt(now.plus(ttl));
        story = storyRepository.save(story);

        // The author has trivially "seen" the story they just created.
        return toResponse(story, UserSummary.from(author), true);
    }

    @Transactional
    public void markViewed(Long storyId, Long viewerId) {
        Story story = storyRepository.findById(storyId).orElseThrow(() -> new NotFoundException("Story not found"));
        if (storyViewRepository.existsByStoryIdAndViewerId(storyId, viewerId)) {
            return;
        }
        User viewer = userRepository.getReferenceById(viewerId);
        StoryView view = new StoryView();
        view.setStory(story);
        view.setViewer(viewer);
        view.setCreatedAt(Instant.now());
        try {
            storyViewInserter.insert(view);
        } catch (DataIntegrityViolationException e) {
            // Lost a race against a concurrent view record of the same story by the same viewer —
            // treat it as the idempotent no-op it was meant to be rather than surfacing an error.
        }
    }

    @Transactional(readOnly = true)
    public List<StoryResponse> getUserStories(String username, Long viewerId) {
        User author = userRepository.findByUsername(username).orElseThrow(() -> new NotFoundException("User not found"));
        User viewer = userRepository.findById(viewerId).orElseThrow(() -> new NotFoundException("User not found"));
        if (!profileVisibilityService.isVisible(author, viewer)) {
            throw new ForbiddenException("This account is private");
        }

        UserSummary authorSummary = UserSummary.from(author);
        List<Story> stories = storyRepository.findActiveByUserId(author.getId(), Instant.now());
        Set<Long> viewedIds = viewedStoryIds(viewerId, stories);
        return stories.stream()
                .map(s -> toResponse(s, authorSummary, viewedIds.contains(s.getId())))
                .toList();
    }

    /** Follows-based, mirroring FeedService.getHomeFeed — if you're an accepted follower you can already see their content. */
    @Transactional(readOnly = true)
    public CursorPage<StoryResponse> getStoriesFeed(Long viewerId, String cursor, int limit) {
        List<Long> followedIds = followRepository.findAcceptedFolloweeIds(viewerId);
        if (followedIds.isEmpty()) {
            return new CursorPage<>(List.of(), null, false);
        }

        Instant now = Instant.now();
        Cursor decoded = cursor == null ? null : Cursor.decode(cursor);
        List<Story> rows = decoded == null
                ? storyRepository.findFirstActivePageByUserIds(followedIds, now, limit + 1)
                : storyRepository.findActivePageByUserIdsAfterCursor(
                        followedIds, now, decoded.createdAt(), decoded.id(), limit + 1);

        CursorPage<Story> page = CursorPage.of(rows, limit, s -> new Cursor(s.getCreatedAt(), s.getId()).encode());

        Set<Long> authorIds = page.items().stream().map(s -> s.getUser().getId()).collect(Collectors.toSet());
        Map<Long, UserSummary> authorsById = userRepository.findAllById(authorIds).stream()
                .collect(Collectors.toMap(User::getId, UserSummary::from));
        Set<Long> viewedIds = viewedStoryIds(viewerId, page.items());

        List<StoryResponse> items = page.items().stream()
                .map(s -> toResponse(s, authorsById.get(s.getUser().getId()), viewedIds.contains(s.getId())))
                .toList();
        return new CursorPage<>(items, page.nextCursor(), page.hasMore());
    }

    @Transactional
    public void deleteStory(Long storyId, Long requesterId) {
        // Matches PostService.deletePost's convention: 404 if the story truly doesn't exist, 403
        // if it exists but isn't yours — not the messaging package's "collapse both to 403"
        // approach, which exists specifically to avoid confirming a private conversation's
        // existence. A story's existence isn't similarly sensitive.
        Story story = storyRepository.findById(storyId).orElseThrow(() -> new NotFoundException("Story not found"));
        if (!story.getUser().getId().equals(requesterId)) {
            throw new ForbiddenException("You can only delete your own stories");
        }
        storyRepository.delete(story);
    }

    private Set<Long> viewedStoryIds(Long viewerId, List<Story> stories) {
        if (stories.isEmpty()) {
            return Set.of();
        }
        List<Long> storyIds = stories.stream().map(Story::getId).toList();
        return new HashSet<>(storyViewRepository.findViewedStoryIds(viewerId, storyIds));
    }

    private StoryResponse toResponse(Story story, UserSummary author, boolean seenByViewer) {
        return new StoryResponse(
                story.getId(), author, story.getMediaUrl(), story.getExpiresAt(), story.getCreatedAt(), seenByViewer);
    }
}
