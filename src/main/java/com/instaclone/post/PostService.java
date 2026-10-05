package com.instaclone.post;

import com.instaclone.common.BadRequestException;
import com.instaclone.common.Cursor;
import com.instaclone.common.CursorPage;
import com.instaclone.common.ForbiddenException;
import com.instaclone.common.NotFoundException;
import com.instaclone.config.StorageProperties;
import com.instaclone.hashtag.Hashtag;
import com.instaclone.hashtag.HashtagService;
import com.instaclone.notification.NotificationRepository;
import com.instaclone.social.comment.CommentRepository;
import com.instaclone.social.like.LikeRepository;
import com.instaclone.social.like.LikeableType;
import com.instaclone.social.moderation.ModerationService;
import com.instaclone.social.saved.SavedPostRepository;
import com.instaclone.user.ProfileVisibilityService;
import com.instaclone.user.User;
import com.instaclone.user.UserRepository;
import com.instaclone.user.UserSummary;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PostService {

    private final PostRepository postRepository;
    private final MediaRepository mediaRepository;
    private final UserRepository userRepository;
    private final CommentRepository commentRepository;
    private final LikeRepository likeRepository;
    private final SavedPostRepository savedPostRepository;
    private final ProfileVisibilityService profileVisibilityService;
    private final StorageProperties storageProperties;
    private final HashtagService hashtagService;
    private final ModerationService moderationService;
    private final NotificationRepository notificationRepository;

    public PostService(
            PostRepository postRepository,
            MediaRepository mediaRepository,
            UserRepository userRepository,
            CommentRepository commentRepository,
            LikeRepository likeRepository,
            SavedPostRepository savedPostRepository,
            ProfileVisibilityService profileVisibilityService,
            StorageProperties storageProperties,
            HashtagService hashtagService,
            ModerationService moderationService,
            NotificationRepository notificationRepository) {
        this.postRepository = postRepository;
        this.mediaRepository = mediaRepository;
        this.userRepository = userRepository;
        this.commentRepository = commentRepository;
        this.likeRepository = likeRepository;
        this.savedPostRepository = savedPostRepository;
        this.profileVisibilityService = profileVisibilityService;
        this.storageProperties = storageProperties;
        this.hashtagService = hashtagService;
        this.moderationService = moderationService;
        this.notificationRepository = notificationRepository;
    }

    @Transactional
    public PostResponse createPost(Long userId, CreatePostRequest request) {
        User author = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));

        for (CreatePostRequest.MediaItem item : request.media()) {
            if (!storageProperties.isOwnedUrl(item.url(), userId)) {
                throw new BadRequestException("Media url must reference an object uploaded via /posts/upload-url");
            }
        }

        Post post = new Post();
        post.setUser(author);
        post.setCaption(request.caption());
        post.setLocation(request.location());
        post.setType(request.media().size() > 1 ? PostType.CAROUSEL : PostType.PHOTO);
        post.setMediaCount(request.media().size());
        post.setCreatedAt(Instant.now());
        hashtagService.parseAndAttach(post, request.caption());
        post = postRepository.save(post);

        List<Media> media = new ArrayList<>();
        int position = 0;
        for (CreatePostRequest.MediaItem item : request.media()) {
            Media m = new Media();
            m.setPost(post);
            m.setUrl(item.url());
            m.setMediaType(MediaType.IMAGE);
            m.setWidth(item.width());
            m.setHeight(item.height());
            m.setPosition(position++);
            media.add(mediaRepository.save(m));
        }

        return toResponse(post, UserSummary.from(author), media, false, false);
    }

    @Transactional(readOnly = true)
    public PostResponse getPost(Long postId, Long viewerId) {
        Post post = postRepository.findById(postId).orElseThrow(() -> new NotFoundException("Post not found"));
        User viewer = userRepository.findById(viewerId).orElseThrow(() -> new NotFoundException("User not found"));
        assertVisible(post, viewer);

        List<Media> media = mediaRepository.findByPostIdOrderByPosition(postId);
        boolean liked = likeRepository.existsByUserIdAndLikeableTypeAndLikeableId(viewerId, LikeableType.POST, postId);
        boolean saved = savedPostRepository.existsByUserIdAndPostId(viewerId, postId);
        return toResponse(post, UserSummary.from(post.getUser()), media, liked, saved);
    }

    @Transactional
    public PostResponse updatePost(Long postId, Long requesterId, UpdatePostRequest request) {
        Post post = postRepository.findById(postId).orElseThrow(() -> new NotFoundException("Post not found"));
        if (!post.getUser().getId().equals(requesterId)) {
            throw new ForbiddenException("You can only edit your own posts");
        }

        // A null field means "not part of this patch" (omitted); an explicit "" clears it. Without
        // this distinction, a caller that only means to update location would null out the caption
        // (and wipe its hashtags) by simply not mentioning it.
        if (request.caption() != null) {
            post.setCaption(request.caption());
            if (request.caption().isBlank()) {
                post.getHashtags().clear();
            } else {
                hashtagService.parseAndAttach(post, request.caption());
            }
        }
        if (request.location() != null) {
            post.setLocation(request.location());
        }
        post = postRepository.save(post);

        List<Media> media = mediaRepository.findByPostIdOrderByPosition(postId);
        boolean liked = likeRepository.existsByUserIdAndLikeableTypeAndLikeableId(requesterId, LikeableType.POST, postId);
        boolean saved = savedPostRepository.existsByUserIdAndPostId(requesterId, postId);
        return toResponse(post, UserSummary.from(post.getUser()), media, liked, saved);
    }

    @Transactional
    public void deletePost(Long postId, Long requesterId) {
        Post post = postRepository.findById(postId).orElseThrow(() -> new NotFoundException("Post not found"));
        if (!post.getUser().getId().equals(requesterId)) {
            throw new ForbiddenException("You can only delete your own posts");
        }

        List<Long> commentIds = commentRepository.findIdsByPostId(postId);
        if (!commentIds.isEmpty()) {
            likeRepository.deleteByLikeableTypeAndLikeableIdIn(LikeableType.COMMENT, commentIds);
        }
        likeRepository.deleteByLikeableTypeAndLikeableId(LikeableType.POST, postId);
        notificationRepository.deleteByTarget("POST", postId);
        postRepository.delete(post);
    }

    @Transactional(readOnly = true)
    public CursorPage<PostResponse> getUserPosts(String username, Long viewerId, String cursor, int limit) {
        User author = userRepository.findByUsername(username).orElseThrow(() -> new NotFoundException("User not found"));
        User viewer = userRepository.findById(viewerId).orElseThrow(() -> new NotFoundException("User not found"));
        if (!profileVisibilityService.isVisible(author, viewer)) {
            throw new ForbiddenException("This account is private");
        }

        // The owner viewing their own grid should see a still-transcoding or failed reel too —
        // otherwise a freshly uploaded reel is invisible even to the person who just posted it.
        boolean isOwnProfile = viewerId.equals(author.getId());
        Cursor decoded = cursor == null ? null : Cursor.decode(cursor);
        List<Post> rows = decoded == null
                ? (isOwnProfile
                        ? postRepository.findFirstPageByUserIdIncludingPending(author.getId(), limit + 1)
                        : postRepository.findFirstPageByUserId(author.getId(), limit + 1))
                : (isOwnProfile
                        ? postRepository.findPageByUserIdAfterCursorIncludingPending(
                                author.getId(), decoded.createdAt(), decoded.id(), limit + 1)
                        : postRepository.findPageByUserIdAfterCursor(
                                author.getId(), decoded.createdAt(), decoded.id(), limit + 1));

        return toPage(rows, limit, viewerId);
    }

    @Transactional(readOnly = true)
    public CursorPage<PostResponse> getPostsByHashtag(String tag, Long viewerId, String cursor, int limit) {
        // A discovery surface like Explore — blocked-either-direction users must be excluded here
        // too, not just the viewer themselves.
        List<Long> excludedIds = new ArrayList<>(moderationService.getBlockedEitherDirectionIds(viewerId));
        excludedIds.add(viewerId);

        Cursor decoded = cursor == null ? null : Cursor.decode(cursor);
        List<Post> rows = decoded == null
                ? postRepository.findFirstPageByHashtag(tag, excludedIds, limit + 1)
                : postRepository.findPageByHashtagAfterCursor(tag, excludedIds, decoded.createdAt(), decoded.id(), limit + 1);

        return toPage(rows, limit, viewerId);
    }

    /** Used by FeedService, which already knows the raw (limit+1)-sized, keyset-ordered post rows. */
    @Transactional(readOnly = true)
    public CursorPage<PostResponse> toPage(List<Post> rowsWithLookahead, int limit, Long viewerId) {
        CursorPage<Post> page =
                CursorPage.of(rowsWithLookahead, limit, p -> new Cursor(p.getCreatedAt(), p.getId()).encode());
        List<PostResponse> items = enrich(page.items(), viewerId);
        return new CursorPage<>(items, page.nextCursor(), page.hasMore());
    }

    /** Batch-enriches an already-trimmed row set (author/media/likedByViewer) without re-paginating. */
    @Transactional(readOnly = true)
    public List<PostResponse> enrich(List<Post> posts, Long viewerId) {
        if (posts.isEmpty()) {
            return List.of();
        }
        List<Long> postIds = posts.stream().map(Post::getId).toList();
        Set<Long> authorIds = posts.stream().map(p -> p.getUser().getId()).collect(Collectors.toSet());

        Map<Long, List<Media>> mediaByPost = mediaRepository.findByPostIdInOrderByPostIdAscPositionAsc(postIds).stream()
                .collect(Collectors.groupingBy(m -> m.getPost().getId()));
        Map<Long, UserSummary> authorsById = userRepository.findAllById(authorIds).stream()
                .collect(Collectors.toMap(User::getId, UserSummary::from));
        Set<Long> likedPostIds = new HashSet<>(likeRepository.findLikedIds(viewerId, LikeableType.POST, postIds));
        Set<Long> savedPostIds = new HashSet<>(savedPostRepository.findSavedPostIds(viewerId, postIds));

        return posts.stream()
                .map(p -> toResponse(
                        p,
                        authorsById.get(p.getUser().getId()),
                        mediaByPost.getOrDefault(p.getId(), List.of()),
                        likedPostIds.contains(p.getId()),
                        savedPostIds.contains(p.getId())))
                .toList();
    }

    private void assertVisible(Post post, User viewer) {
        if (!profileVisibilityService.isVisible(post.getUser(), viewer)) {
            throw new ForbiddenException("This account is private");
        }
    }

    /** Public so ReelService (a video-specific Post variant) can reuse the same response shape. */
    public PostResponse toResponse(
            Post post, UserSummary author, List<Media> media, boolean likedByViewer, boolean savedByViewer) {
        return new PostResponse(
                post.getId(),
                author,
                post.getCaption(),
                post.getLocation(),
                post.getType(),
                post.getMediaCount(),
                post.getLikeCount(),
                post.getCommentCount(),
                likedByViewer,
                savedByViewer,
                post.getCreatedAt(),
                media.stream().map(MediaResponse::from).toList(),
                post.getHashtags().stream().map(Hashtag::getTag).sorted().toList());
    }
}
