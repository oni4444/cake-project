package com.cakemate.cake_platform.domain.notification.controller;

import com.cakemate.cake_platform.common.jwt.util.JwtUtil;
import com.cakemate.cake_platform.domain.notification.service.NotificationService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Optional;

@RestController
@RequestMapping("/notifications/stream")
public class NotificationController {
    private final  NotificationService notificationService;
    private final JwtUtil jwtUtil;

    public NotificationController(NotificationService notificationService, JwtUtil jwtUtil) {
        this.notificationService = notificationService;
        this.jwtUtil = jwtUtil;
    }

    @GetMapping(value = "/customer", produces = "text/event-stream")
    public SseEmitter subscribeCustomerAPI(
            @RequestHeader("Authorization") String bearerToken,
            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventIdHeader) {
        Long customerId = jwtUtil.extractCustomerId(bearerToken);
        return notificationService.subscribeCustomer(customerId, Optional.ofNullable(lastEventIdHeader));
    }

    @GetMapping(value = "/owner", produces = "text/event-stream")
    public SseEmitter subscribeOwnerAPI(
            @RequestHeader("Authorization") String bearerToken,
            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventIdHeader) {
        Long ownerId = jwtUtil.extractOwnerId(bearerToken);
        return notificationService.subscribeOwner(ownerId, Optional.ofNullable(lastEventIdHeader));
    }

    @PostMapping("/test")
    public void sendTestAPI(@RequestParam Long memberId, @RequestParam String memberType) {
        notificationService.sendNotification(memberId, "테스트 알림입니다! memberType : " + memberType, memberType);
    }
}