package com.cakemate.cake_platform.domain.proposalFormChat.dto;

import com.cakemate.cake_platform.domain.proposalFormChat.entity.ChatMessageEntity;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;

import java.time.LocalDateTime;
/**
 * 과거 채팅 기록을 클라이언트에게 전달할 때 사용
 */
@Getter
public class ChatMessageHistoryResponseDto {
    private final Long id;
    private final String senderName;
    private final String content;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm", timezone = "Asia/Seoul")
    private final LocalDateTime createdAt;

    public ChatMessageHistoryResponseDto(Long id, String senderName, String content, LocalDateTime createdAt) {
        this.id = id;
        this.senderName = senderName;
        this.content = content;
        this.createdAt = createdAt;
    }

    //ChatMessageEntity 를 ChatMessageHistoryDto 로 변환하여
    // 클라이언트 응답에 사용
    public static ChatMessageHistoryResponseDto from(ChatMessageEntity e) {
        return new ChatMessageHistoryResponseDto(
                e.getId(),
                e.getSenderName(),
                e.getContent(),
                e.getCreatedAt()
        );
    }
}
