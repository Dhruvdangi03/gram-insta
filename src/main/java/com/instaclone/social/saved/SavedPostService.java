package com.instaclone.social.saved;

import com.instaclone.common.Cursor;
import com.instaclone.common.CursorPage;
import com.instaclone.common.ForbiddenException;
import com.instaclone.common.NotFoundException;
import com.instaclone.post.Post;
import com.instaclone.post.PostRepository;
import com.instaclone.post.PostResponse;
import com.instaclone.post.PostService;
import com.instaclone.social.moderation.ModerationService;
import com.instaclone.user.ProfileVisibilityService;
import com.instaclone.user.User;
import com.instaclone.user.UserRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SavedPostService {

    private final SavedPostRepository savedPostRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final ProfileVisibilityService profileVisibilityService;
    private final ModerationService moderationService;
    private final PostService postService;
    private final SavedPostInserter savedPostInserter;

    public SavedPostService(
            SavedPostRepository savedPostRepository,
            PostRepository postRepository,
            UserRepository userRepository,
            ProfileVisibilityService profileVisibilityService,
            ModerationService moderationService,
            PostService postService,
            SavedPostInserter savedPostInserter) {
        this.savedPostRepository = savedPostRepository;
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.profileVisibilityService = profileVisibilityService;
        this.moderationService = moderationService;
        this.postService = postService;
        this.savedPostInserter = savedPostInserter;
    }

    @Transactional
    public void savePost(Long userId, Long postId) {
        Post post = postRepository.findById(postId).orElseThrow(() -> new NotFoundException("Post not found"));
        User viewer = userRepository.getReferenceById(userId);
        if (!profileVisibilityService.isVisible(post.getUser(), viewer)) {
            throw new ForbiddenException("This account is private");
        }
        // isVisible is deliberately directional (lets a blocker still view a blocked account's
        // posts) — but saving is a write, so it must also check the saver's own side of the block,
        // matching the same guard LikeService/CommentService/FollowService enforce on their writes.
        if (moderationService.isBlockedEitherDirection(userId, post.getUser().getId())) {
            throw new ForbiddenException("You can't interact with this account");
        }
        if (savedPostRepository.existsByUserIdAndPostId(userId, postId)) {
            return;
        }
        SavedPost saved = new SavedPost();
        saved.setUser(viewer);
        saved.setPost(post);
        saved.setCreatedAt(Instant.now());
        try {
            savedPostInserter.insert(saved);
        } catch (DataIntegrityViolationException e) {
            // Lost a race against a concurrent save of the same post by the same user — the
            // unique(user_id, post_id) constraint caught it in the inserter's own transaction, so
            // this call's save already exists; treat it as the idempotent no-op it was meant to be
            // rather than surfacing a 409.
        }
    }

    @Transactional
    public void unsavePost(Long userId, Long postId) {
        savedPostRepository.findByUserIdAndPostId(userId, postId).ifPresent(savedPostRepository::delete);
    }

    /** Saved posts are always private to the viewer (like real Instagram) — the caller must pass
     * the requester's own id as both userId and viewerId; there is no "view someone else's saved
     * posts" path. */
    @Transactional(readOnly = true)
    public CursorPage<PostResponse> getSavedPosts(Long userId, String cursor, int limit) {
        Cursor decoded = cursor == null ? null : Cursor.decode(cursor);
        List<SavedPost> rows = decoded == null
                ? savedPostRepository.findFirstPage(userId, limit + 1)
                : savedPostRepository.findPageAfterCursor(userId, decoded.createdAt(), decoded.id(), limit + 1);

        CursorPage<SavedPost> page =
                CursorPage.of(rows, limit, sp -> new Cursor(sp.getCreatedAt(), sp.getId()).encode());

        List<Post> posts = page.items().stream().map(SavedPost::getPost).toList();
        List<PostResponse> items = postService.enrich(posts, userId);
        return new CursorPage<>(items, page.nextCursor(), page.hasMore());
    }
}
