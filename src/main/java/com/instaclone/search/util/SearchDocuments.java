package com.instaclone.search.util;

import com.instaclone.post.entity.Post;
import com.instaclone.user.entity.User;
import java.util.HashMap;
import java.util.Map;

/** Builds the field maps indexed into Meilisearch — the one place that shape is defined, shared by every caller that indexes a user or post. */
public final class SearchDocuments {

    private SearchDocuments() {}

    public static Map<String, Object> forUser(User user) {
        Map<String, Object> fields = new HashMap<>();
        fields.put("id", String.valueOf(user.getId()));
        fields.put("username", user.getUsername());
        fields.put("fullName", user.getFullName());
        fields.put("profilePictureUrl", user.getProfilePictureUrl());
        fields.put("isVerified", user.isVerified());
        return fields;
    }

    public static Map<String, Object> forPost(Post post) {
        Map<String, Object> fields = new HashMap<>();
        fields.put("id", String.valueOf(post.getId()));
        fields.put("caption", post.getCaption());
        fields.put("authorId", String.valueOf(post.getUser().getId()));
        fields.put("authorUsername", post.getUser().getUsername());
        fields.put("createdAt", post.getCreatedAt().toString());
        return fields;
    }
}
