package com.cakemate.cake_platform.domain.proposalFormChat.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.time.LocalDateTime;

@Getter
@Builder
public class BroadcastMessageResponseDto {
    private final String sender;
    private final String message;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm", timezone = "Asia/Seoul")
    private final LocalDateTime sentAt; //시간표시/정렬용 (DB createdAt 매핑)
}
