package com.cakemate.cake_platform.domain.proposalFormChat.dto;

import com.cakemate.cake_platform.domain.proposalFormChat.enums.MessageType;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
/**
 * WebSocket 실시간 채팅에서 요청 바디에쓰는 Dto
 * -> 클라이언트 ↔ 서버 간에 주고받는 단일 메시지를 표현.
 */
@Getter
@Setter
@Builder
public class ChatMessageRequestDto {

    // 메시지 타입(ENTER, TALK)
    private MessageType type;

    // 메시지 본문
    private String message;
}