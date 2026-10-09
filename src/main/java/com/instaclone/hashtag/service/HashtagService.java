package com.instaclone.hashtag.service;

import com.instaclone.common.exception.BadRequestException;
import com.instaclone.common.pagination.Cursor;
import com.instaclone.common.pagination.CursorPage;
import com.instaclone.hashtag.dto.HashtagSummary;
import com.instaclone.post.dto.PostResponse;
import com.instaclone.post.entity.Post;
import com.instaclone.post.repository.PostRepository;
import com.instaclone.post.service.PostService;
import com.instaclone.social.block.repository.UserBlockRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hashtag pages and search. Like Explore, this is a discovery surface: only public accounts' posts
 * appear (a private account's tagged post is never listed, regardless of who follows whom), and
 * accounts on either side of a block with the viewer are left out.
 */
@Service
public class HashtagService {

    private static final Pattern VALID_TAG = Pattern.compile("[\\p{L}\\p{N}_]{1,50}");

    private final PostRepository postRepository;
    private final PostService postService;
    private final UserBlockRepository blockRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public HashtagService(PostRepository postRepository, PostService postService, UserBlockRepository blockRepository) {
        this.postRepository = postRepository;
        this.postService = postService;
        this.blockRepository = blockRepository;
    }

    @Transactional(readOnly = true)
    public HashtagSummary getHashtag(String rawTag, Long viewerId) {
        String tag = normalize(rawTag);
        Number count = (Number) entityManager
                .createNativeQuery("SELECT COUNT(*) FROM post_hashtags h "
                        + "JOIN posts p ON p.id = h.post_id JOIN users u ON u.id = p.user_id "
                        + "WHERE h.tag = :tag AND u.is_private = false AND p.user_id NOT IN (:excludedIds) AND "
                        + PostRepository.READY_FILTER_P)
                .setParameter("tag", tag)
                .setParameter("excludedIds", excludedUserIds(viewerId))
                .getSingleResult();
        return new HashtagSummary(tag, count.longValue());
    }

    @Transactional(readOnly = true)
    public CursorPage<PostResponse> getPosts(String rawTag, Long viewerId, String cursor, int limit) {
        String tag = normalize(rawTag);
        List<Long> excluded = excludedUserIds(viewerId);
        Cursor decoded = cursor == null ? null : Cursor.decode(cursor);
        List<Post> rows = decoded == null
                ? postRepository.findByHashtagFirstPage(tag, excluded, limit + 1)
                : postRepository.findByHashtagAfterCursor(tag, excluded, decoded.createdAt(), decoded.id(), limit + 1);
        return postService.toPage(rows, limit, viewerId);
    }

    /** Tags starting with the query, most-used first. A leading '#' in the query is ignored. */
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<HashtagSummary> search(String query, int limit, Long viewerId) {
        String prefix = query == null ? "" : query.trim().replaceFirst("^#", "").toLowerCase(Locale.ROOT);
        if (prefix.isEmpty() || prefix.length() > 50) {
            return List.of();
        }
        // Escape LIKE wildcards so a search for "100%" or "a_b" is literal.
        String likePattern = prefix.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        List<Object[]> rows = entityManager
                .createNativeQuery("SELECT h.tag, COUNT(*) AS c FROM post_hashtags h "
                        + "JOIN posts p ON p.id = h.post_id JOIN users u ON u.id = p.user_id "
                        + "WHERE h.tag LIKE :pattern ESCAPE '\\' AND u.is_private = false "
                        + "AND p.user_id NOT IN (:excludedIds) AND " + PostRepository.READY_FILTER_P
                        + " GROUP BY h.tag ORDER BY c DESC, h.tag ASC LIMIT :limit")
                .setParameter("pattern", likePattern)
                .setParameter("excludedIds", excludedUserIds(viewerId))
                .setParameter("limit", limit)
                .getResultList();
        return rows.stream()
                .map(r -> new HashtagSummary((String) r[0], ((Number) r[1]).longValue()))
                .toList();
    }

    // Never empty, so the native NOT IN stays valid SQL (same sentinel as FeedService).
    private List<Long> excludedUserIds(Long viewerId) {
        List<Long> ids = new ArrayList<>(blockRepository.findBlockRelatedUserIds(viewerId));
        ids.add(-1L);
        return ids;
    }

    private static String normalize(String rawTag) {
        String tag = rawTag == null ? "" : rawTag.trim().replaceFirst("^#", "").toLowerCase(Locale.ROOT);
        if (!VALID_TAG.matcher(tag).matches()) {
            throw new BadRequestException("Invalid hashtag");
        }
        return tag;
    }
}
