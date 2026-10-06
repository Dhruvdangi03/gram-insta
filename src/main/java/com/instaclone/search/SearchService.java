package com.instaclone.search;

import com.instaclone.post.Post;
import com.instaclone.post.PostRepository;
import com.instaclone.user.User;
import com.instaclone.user.UserRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Plain Postgres-backed search (ILIKE over username/full name/caption/hashtag), replacing the
 * former Meilisearch-indexed implementation. No separate index to keep in sync: everything here
 * queries the primary tables directly, so results are always consistent with the data (unlike the
 * old async Redis-stream-fed index, which had a short propagation delay).
 *
 * <p>Both queries always append the viewer's own id to the excluded-ids list, same convention as
 * PostRepository's explore/hashtag queries — a native "NOT IN ()" with an empty list is invalid
 * Postgres syntax, and excluding the viewer from their own search results is harmless.
 */
@Service
public class SearchService {

    private final UserRepository userRepository;
    private final PostRepository postRepository;

    public SearchService(UserRepository userRepository, PostRepository postRepository) {
        this.userRepository = userRepository;
        this.postRepository = postRepository;
    }

    @Transactional(readOnly = true)
    public List<UserSearchResult> searchUsers(String query, int limit, Long viewerId) {
        List<Long> excludedIds = excludedIds(viewerId);
        return userRepository.searchByQuery(query, excludedIds, limit).stream()
                .map(SearchService::toUserResult)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PostSearchResult> searchPosts(String query, int limit, Long viewerId) {
        List<Long> excludedIds = excludedIds(viewerId);
        return postRepository.searchByQuery(query, excludedIds, limit).stream()
                .map(SearchService::toPostResult)
                .toList();
    }

    private List<Long> excludedIds(Long viewerId) {
        return List.of(viewerId);
    }

    private static UserSearchResult toUserResult(User user) {
        return new UserSearchResult(
                user.getId(), user.getUsername(), user.getFullName(), user.getProfilePictureUrl(), user.isVerified());
    }

    private static PostSearchResult toPostResult(Post post) {
        return new PostSearchResult(
                post.getId(),
                post.getCaption(),
                post.getUser().getId(),
                post.getUser().getUsername(),
                List.of(),
                post.getCreatedAt().toString());
    }
}
