package com.cakemate.cake_platform.domain.notification.repository;

import com.cakemate.cake_platform.domain.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    //특정 사용자 3일 이내 알림 조회
    List<Notification> findByReceiverIdAndCreatedAtAfterOrderByIdAsc(Long receiverId, LocalDateTime after);

    //Last-Event-ID 또는 LastSentEvent 기준 누락 알림 조회용
    List<Notification> findByReceiverIdAndIdGreaterThanOrderByIdAsc(Long receiverId, Long id);

    //LastSentEvent 기준 최근 3일 이내 알림 조회
    List<Notification> findByReceiverIdAndIdGreaterThanAndCreatedAtAfterOrderByIdAsc(Long receiverId, Long id, LocalDateTime after);

    //헤더 없거나 LastSentEvent 기준으로 누락된 알림(최근 3일치) 조회
    @Query("SELECT n FROM Notification n " +
            "WHERE n.receiverId = :receiverId " +
            "AND (n.id > :lastSentId OR n.createdAt > :threeDaysAgoUtc) " +
            "ORDER BY n.id ASC")
    List<Notification> findMissedNotifications(@Param("receiverId") Long receiverId,
                                               @Param("lastSentId") Long lastSentId,
                                               @Param("threeDaysAgoUtc") LocalDateTime threeDaysAgoUtc);

}
