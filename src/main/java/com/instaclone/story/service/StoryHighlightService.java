package com.instaclone.story.service;

import com.instaclone.common.exception.BadRequestException;
import com.instaclone.common.exception.ForbiddenException;
import com.instaclone.common.exception.NotFoundException;
import com.instaclone.config.properties.StorageProperties;
import com.instaclone.story.dto.AddHighlightItemRequest;
import com.instaclone.story.dto.CreateHighlightRequest;
import com.instaclone.story.dto.StoryHighlightDetailResponse;
import com.instaclone.story.dto.StoryHighlightItemResponse;
import com.instaclone.story.dto.StoryHighlightResponse;
import com.instaclone.story.entity.Story;
import com.instaclone.story.entity.StoryHighlight;
import com.instaclone.story.entity.StoryHighlightItem;
import com.instaclone.story.repository.StoryHighlightItemRepository;
import com.instaclone.story.repository.StoryHighlightRepository;
import com.instaclone.story.repository.StoryRepository;
import com.instaclone.user.entity.User;
import com.instaclone.user.repository.UserRepository;
import com.instaclone.user.service.ProfileVisibilityService;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StoryHighlightService {

    private final StoryHighlightRepository highlightRepository;
    private final StoryHighlightItemRepository itemRepository;
    private final StoryRepository storyRepository;
    private final UserRepository userRepository;
    private final ProfileVisibilityService profileVisibilityService;
    private final StorageProperties storageProperties;

    public StoryHighlightService(
            StoryHighlightRepository highlightRepository,
            StoryHighlightItemRepository itemRepository,
            StoryRepository storyRepository,
            UserRepository userRepository,
            ProfileVisibilityService profileVisibilityService,
            StorageProperties storageProperties) {
        this.highlightRepository = highlightRepository;
        this.itemRepository = itemRepository;
        this.storyRepository = storyRepository;
        this.userRepository = userRepository;
        this.profileVisibilityService = profileVisibilityService;
        this.storageProperties = storageProperties;
    }

    @Transactional
    public StoryHighlightResponse createHighlight(Long userId, CreateHighlightRequest request) {
        User user = userRepository.getReferenceById(userId);
        if (request.coverUrl() != null && !storageProperties.isOwnedUrl(request.coverUrl(), userId)) {
            throw new BadRequestException("coverUrl must reference an object uploaded via /posts/upload-url");
        }
        StoryHighlight highlight = new StoryHighlight();
        highlight.setUser(user);
        highlight.setTitle(request.title());
        highlight.setCoverUrl(request.coverUrl());
        highlight.setCreatedAt(Instant.now());
        highlight = highlightRepository.save(highlight);
        return toResponse(highlight);
    }

    @Transactional
    public void addItem(Long userId, Long highlightId, AddHighlightItemRequest request) {
        StoryHighlight highlight = highlightRepository
                .findByIdForUpdate(highlightId)
                .orElseThrow(() -> new NotFoundException("Highlight not found"));
        if (!highlight.getUser().getId().equals(userId)) {
            throw new ForbiddenException("You can only add to your own highlights");
        }
        Story story =
                storyRepository.findById(request.storyId()).orElseThrow(() -> new NotFoundException("Story not found"));
        if (!story.getUser().getId().equals(userId)) {
            throw new ForbiddenException("You can only add your own stories to a highlight");
        }
        if (story.getExpiresAt().isBefore(Instant.now())) {
            throw new BadRequestException("This story has already expired");
        }

        int position = itemRepository.countByHighlightId(highlightId);
        StoryHighlightItem item = new StoryHighlightItem();
        item.setHighlight(highlight);
        // A denormalized copy of the media url — deliberately not a FK to the story row, which
        // StoryCleanupJob hard-deletes a day after it expires (see the migration's comment).
        item.setMediaUrl(story.getMediaUrl());
        item.setPosition(position);
        item.setCreatedAt(Instant.now());
        itemRepository.save(item);

        if (highlight.getCoverUrl() == null) {
            highlight.setCoverUrl(story.getMediaUrl());
            highlightRepository.save(highlight);
        }
    }

    @Transactional(readOnly = true)
    public List<StoryHighlightResponse> getHighlights(String username, Long viewerId) {
        User author = userRepository.findByUsername(username).orElseThrow(() -> new NotFoundException("User not found"));
        User viewer = userRepository.findById(viewerId).orElseThrow(() -> new NotFoundException("User not found"));
        if (!profileVisibilityService.isVisible(author, viewer)) {
            throw new ForbiddenException("This account is private");
        }
        return highlightRepository.findByUserIdOrderByCreatedAtDesc(author.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public StoryHighlightDetailResponse getHighlightDetail(Long highlightId, Long viewerId) {
        StoryHighlight highlight =
                highlightRepository.findById(highlightId).orElseThrow(() -> new NotFoundException("Highlight not found"));
        User viewer = userRepository.findById(viewerId).orElseThrow(() -> new NotFoundException("User not found"));
        if (!profileVisibilityService.isVisible(highlight.getUser(), viewer)) {
            throw new ForbiddenException("This account is private");
        }
        List<StoryHighlightItemResponse> items = itemRepository.findByHighlightIdOrderByPosition(highlightId).stream()
                .map(i -> new StoryHighlightItemResponse(i.getId(), i.getMediaUrl(), i.getCreatedAt()))
                .toList();
        return new StoryHighlightDetailResponse(
                highlight.getId(), highlight.getTitle(), highlight.getCoverUrl(), highlight.getCreatedAt(), items);
    }

    @Transactional
    public void deleteHighlight(Long highlightId, Long userId) {
        StoryHighlight highlight =
                highlightRepository.findById(highlightId).orElseThrow(() -> new NotFoundException("Highlight not found"));
        if (!highlight.getUser().getId().equals(userId)) {
            throw new ForbiddenException("You can only delete your own highlights");
        }
        highlightRepository.delete(highlight);
    }

    private StoryHighlightResponse toResponse(StoryHighlight highlight) {
        return new StoryHighlightResponse(highlight.getId(), highlight.getTitle(), highlight.getCoverUrl(), highlight.getCreatedAt());
    }
}
