package com.cakemate.cake_platform.domain.proposalFormChat.controller;

import com.cakemate.cake_platform.common.dto.ApiResponse;
import com.cakemate.cake_platform.common.jwt.util.JwtUtil;
import com.cakemate.cake_platform.domain.proposalFormChat.dto.ChatMessageHistoryResponseDto;
import com.cakemate.cake_platform.domain.proposalFormChat.dto.RoomIdGetForHistoryRequestDto;
import com.cakemate.cake_platform.domain.proposalFormChat.service.ChatService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 특정 채팅방의 과거 채팅 메시지를 페이지 단위로 조회하는 API
 */
@RestController
@RequestMapping("/api")
public class ChatMessageGetController {
    private final ChatService chatService;
    private final JwtUtil jwtUtil;

    public ChatMessageGetController(ChatService chatService, JwtUtil jwtUtil) {
        this.chatService = chatService;
        this.jwtUtil = jwtUtil;
    }


    /**
     * [과거 채팅 기록 조회 API]
     * 클라이언트가 특정 채팅방의 메시지를 페이지 단위로 요청할 때 사용
     * @param requestDto       조회할 채팅방의 roomId를 담은 요청 바디
     * @param bearerJwtToken   JWT 토큰 (Authorization 헤더에서 추출)
     * @return                 채팅 메시지 목록 (1페이지당 30개)
     */
    @GetMapping("/chat/messages")
    public ApiResponse<List<ChatMessageHistoryResponseDto>> getHistory(
            @RequestBody RoomIdGetForHistoryRequestDto requestDto,
            @RequestHeader("Authorization") String bearerJwtToken
    ) {
        // JWT 에서 사용자 ID 추출
        //해당 채팅방의 참여자는 소비자(Customer) or 점주(Owner) 중
        // 한 명만 들어올 수 있게 보장되어 있기 때문에
        // 구분하여 검증할 필요 없음
        Long memberId = jwtUtil.extractMemberId(bearerJwtToken);

        //응답 반환
        return chatService.getHistory(memberId, requestDto.getRoomId());

    }
}
