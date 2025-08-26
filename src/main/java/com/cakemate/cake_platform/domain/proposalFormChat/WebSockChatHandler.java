package com.cakemate.cake_platform.domain.proposalFormChat;

import com.cakemate.cake_platform.domain.proposalFormChat.dto.ChatMessageRequestDto;
import com.cakemate.cake_platform.domain.proposalFormChat.dto.ChatMessageResponseDto;
import com.cakemate.cake_platform.domain.proposalFormChat.enums.MessageType;
import com.cakemate.cake_platform.domain.proposalFormChat.service.ChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;

@Slf4j
@RequiredArgsConstructor
@Component
public class WebSockChatHandler extends TextWebSocketHandler {
    // JSON → DTO 변환
    private final ObjectMapper objectMapper;
    // 채팅 로직을 위임받는 서비스 클래스
    private final ChatService chatService;

    /**
     * 클라이언트가 WebSocket 연결을 맺으면 실행됨
     * → 연결 시 자동 입장(ENTER) 처리
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // 인터셉터에서 이미 검증 된 결과 가져오기
        String roomId = (String) session.getAttributes().get("roomId");
        Long memberId = (Long) session.getAttributes().get("memberId");
        String memberName = (String) session.getAttributes().getOrDefault("memberName", "UNKNOWN");

        //roomId나 memberId가 없다면 종료
        if (roomId == null || roomId.isBlank() || memberId == null) {
            safeClose(session, CloseStatus.BAD_DATA.withReason("missing attributes"));
            return;
        }

        // 최초 1화만 세션 등록
        // ->같은 roomId를 가진 세션끼리만 메시지를 주고받도록 하기 위함
        chatService.registerSession(roomId, session);

        // 입장 시스템 메시지 브로드캐스트
        ChatMessageResponseDto enter = ChatMessageResponseDto.builder()
                .type(MessageType.ENTER)
                .roomId(roomId)
                .sender(memberName)
                .message(memberName + "님이 입장했습니다.")
                .build();
        chatService.saveAndBroadcast(enter);
    }

    /**
     * 클라이언트가 메시지를 보내면 실행됨
     * → TALK (채팅) 메시지 처리 = 메세지 요청(send) 때마다 호출
     */
    @Override
    protected void handleTextMessage(
            WebSocketSession session,
            TextMessage message
    ) throws Exception {

        // 세션에서 토큰 만료 시간을 꺼냄
        Long expirationTime = (Long) session.getAttributes().get("expirationTime");

        // 만료된 토큰 트레픽 지우기 및 세션 종료
        // 1. 토큰이 없거나(expirationTime == null) 만료 시간 < 현재 시간 이면
        // -> 세션에 저장된 사용자 정보 제거(인터셉터가 넣어둔 expirationTime(밀리초)로 현재 시간과 비교)
        // -> "token expired(토큰 만료)" 사유로 WebSocket 세션 종료
        // (만료된 토큰인데도 세션에 memberId, memberName 같은 식별 정보가 그대로 남아 있으면,
        //이후에 서버 로직이 잘못 참조해서 만료된 사용자를 유효한 사용자처럼 처리할 위험이 있습니다.)
        if (expirationTime == null || expirationTime < System.currentTimeMillis()) {
            clearSessionAttributes(session);
            safeClose(session, new CloseStatus(4401, "token expired"));
            return;
        }

        // 2. 세션에서 사용자 이름 꺼냄 (없으면 "UNKNOWN"으로 대체)
        String memberName = (String) session.getAttributes().get("memberName");
        if (memberName == null) {
            memberName = "UNKNOWN";
        }

        String sessionRoomId = (String) session.getAttributes().get("roomId");
        Long memberId = (Long) session.getAttributes().get("memberId");

        //핵심 속성이 비어있으면 더 진행하지 않음
        if (sessionRoomId == null || memberId == null) {
            safeClose(session, CloseStatus.BAD_DATA.withReason("missing attributes"));
            return;
        }

        // JSON 문자열을 ChatMessage DTO 로 변환
        ChatMessageRequestDto dto = objectMapper.readValue(message.getPayload(), ChatMessageRequestDto.class);

        // TALK 만 허용 (ENTER 는 자동 처리)
        if (dto.getType() != MessageType.TALK) {
            return;
        }

        // 내용 공백 방지
        if (dto.getMessage() == null || dto.getMessage().isBlank()) {
            safeClose(session, CloseStatus.BAD_DATA.withReason("내용을 입력하세요."));
            return;
        }

        //메시지 처리할 때 roomId는 클라이언트가 보낸 값(dto) 말고,
        // ‘이미 인증된 세션에 저장된 값’을 써야 다른 사용자가 접근하는 걸 막을 수 있음
        ChatMessageResponseDto messageResponseDto = ChatMessageResponseDto.builder()
                .type(MessageType.TALK)
                .roomId(sessionRoomId)
                .sender(memberName)
                .message(dto.getMessage())
                .build();

        //시스템 메시지 전송
        chatService.saveAndBroadcast(messageResponseDto);
    }
    /** 연결 종료 시 방에서 세션 해제 + 속성 정리 */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String roomId = (String) session.getAttributes().get("roomId");
        String memberName = (String) session.getAttributes().getOrDefault("memberName", "UNKNOWN");
        if (roomId != null && !roomId.isBlank()) {
            // 세션 해제
            chatService.unregisterSession(roomId, session);

            // 퇴장 시스템 메시지 브로드캐스트
            ChatMessageResponseDto closed = ChatMessageResponseDto.builder()
                    .type(MessageType.CLOSED) // 퇴장
                    .roomId(roomId)
                    .sender(memberName)
                    .message(memberName + "님이 퇴장했습니다.")
                    .build();
            chatService.saveAndBroadcast(closed);
        } else {
            log.warn("afterConnectionClosed: roomId missing. uri={}", session.getUri());
        }
        clearSessionAttributes(session);
    }

    private void clearSessionAttributes(WebSocketSession session) {
        Map<String, Object> attrs = session.getAttributes();
        attrs.remove("roomId");
        attrs.remove("memberId");
        attrs.remove("memberName");
        attrs.remove("memberType");
        attrs.remove("expirationTime");
    }

    private void safeClose(WebSocketSession session, CloseStatus status) {
        try {
            session.close(status);
        } catch (Exception ignore) {
            //예기치 못한 연결 종료을 위한 로직입니다.-> 추후에 로그를 더 세분화 할 수 있습니다.
        }
    }
}

