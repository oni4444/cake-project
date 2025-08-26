    package com.cakemate.cake_platform.domain.notification.service;

    import com.cakemate.cake_platform.domain.notification.dto.NotificationDto;
    import com.cakemate.cake_platform.domain.notification.entity.LastSentEvent;
    import com.cakemate.cake_platform.domain.notification.entity.Notification;
    import com.cakemate.cake_platform.domain.notification.repository.EmitterRepository;
    import com.cakemate.cake_platform.domain.notification.repository.LastSentEventRepository;
    import com.cakemate.cake_platform.domain.notification.repository.NotificationRepository;
    import org.springframework.stereotype.Service;
    import org.springframework.transaction.annotation.Transactional;
    import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

    import java.io.IOException;
    import java.time.LocalDateTime;
    import java.time.ZoneOffset;
    import java.util.List;
    import java.util.Optional;

    @Service
    public class NotificationService {
        private static final Long DEFAULT_TIMEOUT = 60L * 1000 * 60; //1시간 타임아웃
        private final EmitterRepository emitterRepository;
        private final NotificationRepository notificationRepository;
        private final LastSentEventRepository lastSentEventRepository;

        public NotificationService(EmitterRepository emitterRepository, NotificationRepository notificationRepository, LastSentEventRepository lastSentEventRepository) {
            this.emitterRepository = emitterRepository;
            this.notificationRepository = notificationRepository;
            this.lastSentEventRepository = lastSentEventRepository;
        }

        private NotificationDto createDummyNotification(String memberName) {
            return new NotificationDto(null, "[" + memberName + "]님 SSE 연결 성공", null);
        }

        /**
         * 소비자용 SSE 연결(구독)
         */
        @Transactional
        public SseEmitter subscribeCustomer(Long memberId, Optional<String> lastEventIdHeader) {
            String emitterId = "customer_" + memberId;

            //고유 emitterId 만들기(사용자 이름+시간 조합)
            SseEmitter emitter = new SseEmitter(DEFAULT_TIMEOUT);

            //emitter 저장
            emitterRepository.save(emitterId, emitter);

            //emitter 생명주기 설정
            emitter.onCompletion(() -> emitterRepository.deleteAllEmitterStartWithId(emitterId));
            emitter.onTimeout(() -> emitterRepository.deleteAllEmitterStartWithId(emitterId));
            emitter.onError((e) -> emitterRepository.deleteAllEmitterStartWithId(emitterId));

            //더미 알림 전송 (연결 확인용)
            sendToClient(emitter, emitterId + "_" + System.currentTimeMillis(), "message",
                    new NotificationDto(null, "[소비자 " + memberId + "] SSE 연결 성공", null));

            //Last-Event-ID 조회
            List<Notification> missedNotifications;
            LocalDateTime threeDaysAgoUtc = LocalDateTime.now(ZoneOffset.UTC).minusDays(3);

            Long lastEventId = lastEventIdHeader
                    .filter(s -> !s.isEmpty())
                    .map(Long::parseLong)
                    .orElse(null);

            if (lastEventId != null) {
                //Last-Event-ID 기준 조회
                missedNotifications = notificationRepository
                        .findByReceiverIdAndIdGreaterThanOrderByIdAsc(memberId, lastEventId);
            } else {
                Optional<LastSentEvent> lastSent = lastSentEventRepository.findById(memberId);
                Long lastSentId = lastSent.map(LastSentEvent::getLastEventId).orElse(0L);

                //LastSentEvent 기준 조회 + 최근 3일 OR 조건
                missedNotifications = notificationRepository.findMissedNotifications(
                        memberId,
                        lastSentId,
                        threeDaysAgoUtc
                );
            }

            //누락 알림 전송
            for (Notification notification : missedNotifications) {
                NotificationDto dto = new NotificationDto(
                        String.valueOf(notification.getId()),
                        notification.getMessage(),
                        notification.getCreatedAt()
                );
                sendToClient(emitter, String.valueOf(notification.getId()), "message", dto);
            }

            //LastSentEvent(마지막 알림 전송 ID) 갱신
            if (!missedNotifications.isEmpty()) {
                Notification lastNotification = missedNotifications.get(missedNotifications.size() - 1);
                lastSentEventRepository.save(new LastSentEvent(memberId, lastNotification.getId()));
            }

            //emitter 반환 및 연결 유지
            return emitter;
        }

        /**
         * 점주용 SSE 연결(구독)
         */
        @Transactional
        public SseEmitter subscribeOwner(Long memberId, Optional<String> lastEventIdHeader) {
            String emitterId = "owner_" + memberId;
            SseEmitter emitter = new SseEmitter(DEFAULT_TIMEOUT);

            emitterRepository.save(emitterId, emitter);
            emitter.onCompletion(() -> emitterRepository.deleteAllEmitterStartWithId(emitterId));
            emitter.onTimeout(() -> emitterRepository.deleteAllEmitterStartWithId(emitterId));
            emitter.onError((e) -> emitterRepository.deleteAllEmitterStartWithId(emitterId));

            sendToClient(emitter, emitterId + "_" + System.currentTimeMillis(), "message",
                    new NotificationDto(null, "[점주 " + memberId + "] SSE 연결 성공", null));

            //Last-Event-ID 조회
            List<Notification> missedNotifications;
            LocalDateTime threeDaysAgoUtc = LocalDateTime.now(ZoneOffset.UTC).minusDays(3);

            Long lastEventId = lastEventIdHeader
                    .filter(s -> !s.isEmpty())
                    .map(Long::parseLong)
                    .orElse(null);

            if (lastEventId != null) {
                //Last-Event-ID 기준 조회
                missedNotifications = notificationRepository
                        .findByReceiverIdAndIdGreaterThanOrderByIdAsc(memberId, lastEventId);
            } else {
                Optional<LastSentEvent> lastSent = lastSentEventRepository.findById(memberId);
                Long lastSentId = lastSent.map(LastSentEvent::getLastEventId).orElse(0L);

                //LastSentEvent 기준 조회 + 최근 3일 OR 조건
                missedNotifications = notificationRepository.findMissedNotifications(
                        memberId,
                        lastSentId,
                        threeDaysAgoUtc
                );
            }

            //누락 알림 전송
            for (Notification notification : missedNotifications) {
                NotificationDto dto = new NotificationDto(
                        String.valueOf(notification.getId()),
                        notification.getMessage(),
                        notification.getCreatedAt()
                );
                sendToClient(emitter, String.valueOf(notification.getId()), "message", dto);
            }

            //마지막 알림 전송 ID 갱신
            if (!missedNotifications.isEmpty()) {
                Notification lastNotification = missedNotifications.get(missedNotifications.size() - 1);
                lastSentEventRepository.save(new LastSentEvent(memberId, lastNotification.getId()));
            }

            //emitter 반환 및 연결 유지
            return emitter;
        }

        /**
         * 알림 저장 및 실시간 전송(연결 없으면 저장만)
         */
        public void sendNotification(Long receiverId, String message, String memberType) {
            //emitterId 생성
            String emitterId = memberType + "_" + receiverId;

            //알림 저장
            Notification notification = notificationRepository.save(new Notification(receiverId, message));

            Optional<SseEmitter> emitterOpt = emitterRepository.findById(emitterId);
            if (emitterOpt.isPresent()) {
                NotificationDto dto = new NotificationDto(
                        String.valueOf(notification.getId()),
                        notification.getMessage(),
                        notification.getCreatedAt()
                );
                sendToClient(emitterOpt.get(), String.valueOf(notification.getId()), "message", dto);
            }

            //DB에 Last-Event-Id 저장
            lastSentEventRepository.save(new LastSentEvent(receiverId, notification.getId()));
        }

        /**
         *  SSE 이벤트 전송
         */
        private void sendToClient(SseEmitter emitter, String eventId, String eventName, Object data) {
            try {
                emitter.send(SseEmitter.event()
                        .id(eventId)
                        .name(eventName)
                        .data(data));
            } catch (IOException e) {
                emitter.completeWithError(e);
            }
        }
    }