package com.instaclone.messaging.repository;

import com.instaclone.messaging.entity.Conversation;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    boolean existsByIdAndParticipantsId(Long conversationId, Long userId);

    // Serializes concurrent getOrCreateConversation calls for the same pair so two callers can't
    // both pass the findOneToOneConversation check before either has committed its INSERT. Held
    // for the rest of the current transaction (pg_advisory_xact_lock), released automatically on
    // commit/rollback. The key packs both ids into one bigint (safe while user ids fit in 32 bits).
    @Query(value = "SELECT pg_advisory_xact_lock(((:lo)::bigint << 32) | ((:hi)::bigint & 4294967295))", nativeQuery = true)
    void acquireOneToOneConversationLock(@Param("lo") long lo, @Param("hi") long hi);

    // The conversation whose participant set is EXACTLY {userIdA, userIdB} and isn't a group —
    // the HAVING clause requires both target users present, the outer count requires no one else.
    @Query(
            value =
                    "SELECT c.* FROM conversations c WHERE c.is_group = false "
                            + "AND (SELECT COUNT(*) FROM conversation_participants cp2 WHERE cp2.conversation_id = c.id) = 2 "
                            + "AND c.id IN (SELECT cp.conversation_id FROM conversation_participants cp "
                            + "WHERE cp.user_id IN (:userIdA, :userIdB) GROUP BY cp.conversation_id HAVING COUNT(DISTINCT cp.user_id) = 2)",
            nativeQuery = true)
    Optional<Conversation> findOneToOneConversation(@Param("userIdA") Long userIdA, @Param("userIdB") Long userIdB);

    // Ordered by last_message_at (falling back to created_at for a conversation with no messages
    // yet), same keyset-pagination shape as every other list in this codebase — see MessageService.
    @Query(
            value =
                    "SELECT c.* FROM conversations c JOIN conversation_participants cp ON cp.conversation_id = c.id "
                            + "WHERE cp.user_id = :userId "
                            + "ORDER BY COALESCE(c.last_message_at, c.created_at) DESC, c.id DESC LIMIT :limit",
            nativeQuery = true)
    List<Conversation> findFirstPageByParticipantId(@Param("userId") Long userId, @Param("limit") int limit);

    @Query(
            value =
                    "SELECT c.* FROM conversations c JOIN conversation_participants cp ON cp.conversation_id = c.id "
                            + "WHERE cp.user_id = :userId "
                            + "AND (COALESCE(c.last_message_at, c.created_at), c.id) < (:cursorTimestamp, :cursorId) "
                            + "ORDER BY COALESCE(c.last_message_at, c.created_at) DESC, c.id DESC LIMIT :limit",
            nativeQuery = true)
    List<Conversation> findPageByParticipantIdAfterCursor(
            @Param("userId") Long userId,
            @Param("cursorTimestamp") Instant cursorTimestamp,
            @Param("cursorId") Long cursorId,
            @Param("limit") int limit);
}
