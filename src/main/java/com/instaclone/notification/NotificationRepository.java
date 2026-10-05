package com.instaclone.notification;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @Query(
            value = "SELECT * FROM notifications WHERE recipient_id = :recipientId ORDER BY created_at DESC, id DESC LIMIT :limit",
            nativeQuery = true)
    List<Notification> findFirstPageByRecipientId(@Param("recipientId") Long recipientId, @Param("limit") int limit);

    @Query(
            value =
                    "SELECT * FROM notifications WHERE recipient_id = :recipientId "
                            + "AND (created_at, id) < (:cursorCreatedAt, :cursorId) "
                            + "ORDER BY created_at DESC, id DESC LIMIT :limit",
            nativeQuery = true)
    List<Notification> findPageByRecipientIdAfterCursor(
            @Param("recipientId") Long recipientId,
            @Param("cursorCreatedAt") Instant cursorCreatedAt,
            @Param("cursorId") Long cursorId,
            @Param("limit") int limit);

    @Modifying
    @Query("update Notification n set n.read = true where n.id = :id and n.recipient.id = :recipientId")
    int markRead(@Param("id") Long id, @Param("recipientId") Long recipientId);

    // Bulk JPQL like deleteByTarget below — a derived delete would SELECT every matching row and
    // then DELETE them one by one.
    @Modifying
    @Query("delete from Notification n where n.recipient.id = :recipientId and n.actor.id = :actorId and n.type = :type")
    void deleteByRecipientIdAndActorIdAndType(
            @Param("recipientId") Long recipientId, @Param("actorId") Long actorId, @Param("type") NotificationType type);

    @Modifying
    @Query("delete from Notification n where n.recipient.id = :recipientId and n.type = :type")
    void deleteByRecipientIdAndType(@Param("recipientId") Long recipientId, @Param("type") NotificationType type);

    boolean existsByRecipientIdAndActorIdAndType(Long recipientId, Long actorId, NotificationType type);

    @Modifying
    @Query("delete from Notification n where n.targetType = :targetType and n.targetId = :targetId")
    void deleteByTarget(@Param("targetType") String targetType, @Param("targetId") Long targetId);
}
