package com.instaclone.messaging;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageRepository extends JpaRepository<Message, Long> {

    // Newest-first, like the post feeds — not oldest-first like comments — since opening a
    // conversation should show the latest messages, with older history loaded on scroll-up.
    @Query(
            value = "SELECT * FROM messages WHERE conversation_id = :conversationId ORDER BY created_at DESC, id DESC LIMIT :limit",
            nativeQuery = true)
    List<Message> findFirstPageByConversationId(@Param("conversationId") Long conversationId, @Param("limit") int limit);

    @Query(
            value =
                    "SELECT * FROM messages WHERE conversation_id = :conversationId "
                            + "AND (created_at, id) < (:cursorCreatedAt, :cursorId) "
                            + "ORDER BY created_at DESC, id DESC LIMIT :limit",
            nativeQuery = true)
    List<Message> findPageByConversationIdAfterCursor(
            @Param("conversationId") Long conversationId,
            @Param("cursorCreatedAt") Instant cursorCreatedAt,
            @Param("cursorId") Long cursorId,
            @Param("limit") int limit);
}
