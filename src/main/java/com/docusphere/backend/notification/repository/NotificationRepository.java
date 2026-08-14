package com.docusphere.backend.notification.repository;

import com.docusphere.backend.notification.entity.Notification;
import com.docusphere.backend.notification.entity.NotificationAudience;
import com.docusphere.backend.notification.entity.NotificationStatus;
import com.docusphere.backend.notification.entity.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Repository interface for Notification entity.
 * Provides database operations for managing notifications.
 */
@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    /**
     * Find all notifications for a specific user and audience, paginated.
     * IMPORTANT: Must always filter by audience to prevent mixing admin and user notifications.
     * Never use without the audience parameter to maintain role-based separation.
     */
    Page<Notification> findByUserIdAndAudienceOrderByCreatedAtDesc(
        UUID userId, NotificationAudience audience, Pageable pageable);

    /**
     * Find notifications for a user with specific status and audience, paginated.
     * IMPORTANT: Must include audience to prevent mixing.
     */
    Page<Notification> findByUserIdAndAudienceAndStatusOrderByCreatedAtDesc(
        UUID userId, NotificationAudience audience, NotificationStatus status, Pageable pageable);

    /**
     * Find notifications by type for a user
     */
    Page<Notification> findByUserIdAndTypeOrderByCreatedAtDesc(UUID userId, NotificationType type, Pageable pageable);

    /**
     * Count unread notifications for a user
     */
    long countByUserIdAndStatus(UUID userId, NotificationStatus status);

    long countByUserIdAndAudienceAndStatus(UUID userId, NotificationAudience audience, NotificationStatus status);

    long countByUserIdAndAudience(UUID userId, NotificationAudience audience);

    /**
     * Find notifications related to a specific entity
     */
    List<Notification> findByRelatedEntityIdAndRelatedEntityType(UUID relatedEntityId, String relatedEntityType);

    /**
     * Find recent notifications for a user (created in last N minutes)
     */
    List<Notification> findByUserIdAndCreatedAtAfterOrderByCreatedAtDesc(UUID userId, LocalDateTime dateTime);

    /**
     * Mark all unread notifications as read for a user
     */
    @Modifying
    @Transactional
    @Query("UPDATE Notification n SET n.status = 'READ', n.readAt = :readAt " +
           "WHERE n.userId = :userId AND n.audience = :audience AND n.status = 'UNREAD'")
    void markAllAsRead(@Param("userId") UUID userId, @Param("audience") NotificationAudience audience,
                       @Param("readAt") LocalDateTime readAt);

    /**
     * Delete old archived notifications (older than specified date)
     */
    @Modifying
    @Transactional
    void deleteByStatusAndArchivedAtBefore(NotificationStatus status, LocalDateTime dateTime);

    /**
     * Delete notifications older than specified date
     */
    @Modifying
    @Transactional
    void deleteByCreatedAtBefore(LocalDateTime dateTime);

    /**
     * Count total notifications for a user
     */
    long countByUserId(UUID userId);

    /**
     * Delete all notifications for a user filtered by audience (for clear-all feature)
     */
    @Modifying
    @Transactional
    void deleteByUserIdAndAudience(UUID userId, NotificationAudience audience);

    /**
     * Delete all notifications for a user regardless of audience (admin-only full clear)
     */
    @Modifying
    @Transactional
    void deleteByUserId(UUID userId);


    /**
     * Find notifications by multiple statuses
     */
    List<Notification> findByUserIdAndStatusInOrderByCreatedAtDesc(UUID userId, List<NotificationStatus> statuses);

    /**
     * Find notifications by metadata matching
     */
    @Query("SELECT n FROM Notification n WHERE n.userId = :userId AND n.type = :type AND n.metadata LIKE CONCAT('%', :invitationId, '%')")
    List<Notification> findByUserIdAndTypeAndInvitationId(@Param("userId") UUID userId, @Param("type") NotificationType type, @Param("invitationId") String invitationId);
}
