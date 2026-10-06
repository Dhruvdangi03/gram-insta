package com.instaclone.social.saved.service;

import com.instaclone.common.exception.ForbiddenException;
import com.instaclone.common.exception.NotFoundException;
import com.instaclone.common.pagination.Cursor;
import com.instaclone.common.pagination.CursorPage;
import com.instaclone.post.dto.PostResponse;
import com.instaclone.post.entity.Post;
import com.instaclone.post.repository.PostRepository;
import com.instaclone.post.service.PostService;
import com.instaclone.social.saved.entity.SavedPost;
import com.instaclone.social.saved.repository.SavedPostRepository;
import com.instaclone.user.entity.User;
import com.instaclone.user.repository.UserRepository;
import com.instaclone.user.service.ProfileVisibilityService;
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
    private final PostService postService;
    private final SavedPostInserter savedPostInserter;

    public SavedPostService(
            SavedPostRepository savedPostRepository,
            PostRepository postRepository,
            UserRepository userRepository,
            ProfileVisibilityService profileVisibilityService,
            PostService postService,
            SavedPostInserter savedPostInserter) {
        this.savedPostRepository = savedPostRepository;
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.profileVisibilityService = profileVisibilityService;
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
