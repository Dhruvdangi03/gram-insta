package com.instaclone.search.service;

import com.instaclone.post.entity.Post;
import com.instaclone.post.repository.PostRepository;
import com.instaclone.search.dto.PostSearchResult;
import com.instaclone.search.dto.UserSearchResult;
import com.instaclone.user.entity.User;
import com.instaclone.user.repository.UserRepository;
import com.instaclone.social.block.repository.UserBlockRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Plain Postgres-backed search (ILIKE over username/full name/caption), replacing the
 * former Meilisearch-indexed implementation. No separate index to keep in sync: everything here
 * queries the primary tables directly, so results are always consistent with the data (unlike the
 * old async Redis-stream-fed index, which had a short propagation delay).
 *
 * <p>Both queries always append the viewer's own id to the excluded-ids list, same convention as
 * PostRepository's explore queries — a native "NOT IN ()" with an empty list is invalid
 * Postgres syntax, and excluding the viewer from their own search results is harmless.
 */
@Service
public class SearchService {

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final UserBlockRepository blockRepository;

    public SearchService(
            UserRepository userRepository, PostRepository postRepository, UserBlockRepository blockRepository) {
        this.userRepository = userRepository;
        this.postRepository = postRepository;
        this.blockRepository = blockRepository;
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
        List<Long> ids = new ArrayList<>(blockRepository.findBlockRelatedUserIds(viewerId));
        ids.add(viewerId);
        return ids;
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
                post.getCreatedAt().toString());
    }
}
