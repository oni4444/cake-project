package com.cakemate.cake_platform.domain.proposalFormChat.service;


import com.cakemate.cake_platform.common.dto.ApiResponse;
import com.cakemate.cake_platform.common.exception.UnauthorizedAccessException;
import com.cakemate.cake_platform.domain.auth.exception.BadRequestException;
import com.cakemate.cake_platform.domain.proposalForm.entity.ProposalForm;
import com.cakemate.cake_platform.domain.proposalForm.repository.ProposalFormRepository;
import com.cakemate.cake_platform.domain.proposalFormChat.dto.BroadcastMessageResponseDto;
import com.cakemate.cake_platform.domain.proposalFormChat.dto.ChatMessageHistoryResponseDto;
import com.cakemate.cake_platform.domain.proposalFormChat.dto.ChatMessageResponseDto;
import com.cakemate.cake_platform.domain.proposalFormChat.dto.ChatRoomResponseDto;
import com.cakemate.cake_platform.domain.proposalFormChat.entity.ChatMessageEntity;
import com.cakemate.cake_platform.domain.proposalFormChat.entity.ChatRoomEntity;
import com.cakemate.cake_platform.domain.proposalFormChat.repository.ChatMessageRepository;
import com.cakemate.cake_platform.domain.proposalFormChat.repository.ChatRoomRepository;
import com.cakemate.cake_platform.domain.requestForm.exception.NotFoundProposalFormException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;


@Slf4j
@RequiredArgsConstructor
@Service
public class ChatService {

    private final ChatRoomRepository chatRoomRepository;
    private final ProposalFormRepository proposalFormRepository;
    private final ChatMessageRepository chatMessageRepository;

    //객체 → JSON 직렬화에 사용하는 Jackson Mapper
    private final ObjectMapper objectMapper;
    //중복 세션 방지( 해당 유저들한테만 메시지를 실시간 전송 )
    private final Map<String, Set<WebSocketSession>> sessionsByRoom
            = new ConcurrentHashMap<>();


    /**
     * 소비자가 견적서를 ACCEPT(선택) 했을 때, 해당 견적서에 대한 채팅방을 생성하거나 반환
     * (동일 견적서로 중복 생성되지 않도록 보장)
     */
    @Transactional
    public ApiResponse<ChatRoomResponseDto> createRoomIfAbsent(Long proposalFormId, Long customerId) {
        // 현재 로그인한 사용자가 해당 견적서, 의뢰서의 고객인지 확인 후 예외처리
        boolean myProposalForm = proposalFormRepository
                .existsByIdAndRequestForm_Customer_IdAndIsDeletedFalse(proposalFormId, customerId);

        if (!myProposalForm) {
            throw new UnauthorizedAccessException("본인의 견적서가 아닙니다.");
        }

        // 이미 존재하는 채팅방이 있는지 확인
        Optional<ChatRoomEntity> already
                = chatRoomRepository.findByProposalForm_Id(proposalFormId);

        return chatRoomRepository.findByProposalForm_Id(proposalFormId)
                .map(room -> ApiResponse.success(
                        HttpStatus.OK,
                        "채팅방이 이미 존재합니다.",
                        ChatRoomResponseDto.from(room)
                ))
                .orElseGet(() -> {
                    // 필요한 경우에만 proposalForm 조회
                    ProposalForm proposal = proposalFormRepository.getReferenceById(proposalFormId);
                    ChatRoomEntity created = chatRoomRepository.save(ChatRoomEntity.create(proposal));
                    return ApiResponse.success(
                            HttpStatus.CREATED,
                            "채팅방이 새로 생성되었습니다.",
                            ChatRoomResponseDto.from(created)
                    );
                });
    }


    /**
     * 점주가 본인의 견적서에 해당하는 채팅방 roomId를 조회
     * → 없으면 예외 발생
     */
    @Transactional(readOnly = true)
    public ApiResponse<ChatRoomResponseDto> getRoomIdOrThrow(Long proposalFormId, Long ownerId) {
        // 해당 견적서가 로그인한 점주의 것인지 확인
        boolean myProposalForm = proposalFormRepository
                .existsByIdAndOwner_IdAndIsDeletedFalse(proposalFormId, ownerId);
        if (!myProposalForm) {
            throw new UnauthorizedAccessException("본인의 견적서가 아닙니다.");
        }

        // 채팅방 ID 조회 (없으면 예외)
        String roomId = chatRoomRepository.findByProposalForm_Id(proposalFormId)
                .map(ChatRoomEntity::getId)
                .orElseThrow(() -> new BadRequestException("채팅방이 아직 없습니다."));

        return ApiResponse.success(
                HttpStatus.OK,
                "채팅방 ID 조회 성공",
                ChatRoomResponseDto.of(roomId)
        );
    }

    /**
     * 견적서 ID로 채팅방 ID(roomId)를 Optional 로 조회
     * → 내부적으로 사용되는 확안용 메서드
     */
    @Transactional(readOnly = true)
    public Optional<String> findRoomId(Long proposalFormId) {
        return chatRoomRepository
                .findByProposalForm_Id(proposalFormId)
                .map(ChatRoomEntity::getId);
    }

    /**
     * 해당 채팅방에 WebSocketSession 을 등록
     * → 클라이언트가 채팅방에 "입장" 시 호출
     */
    public void registerSession(String roomId, WebSocketSession session) {
        Set<WebSocketSession> sessionRoom = sessionsByRoom.computeIfAbsent(
                roomId, k -> ConcurrentHashMap.newKeySet()
        );
        // 이미 등록된 세션이면 재등록 스킵 (안전성)
        if (!sessionRoom.contains(session)) {
            sessionRoom.add(session);
        }
        session.getAttributes().put("roomId", roomId);
    }

    /**
     * 채팅방에서 WebSocketSession 을 제거
     * → 클라이언트가 연결을 끊었거나 퇴장했을 때 호출
     */
    @Transactional
    public void unregisterSession(String roomId, WebSocketSession session) {
        if (roomId == null) return;
        Optional.ofNullable(sessionsByRoom.get(roomId)).ifPresent(set -> {
            set.remove(session);
            if (set.isEmpty()) sessionsByRoom.remove(roomId);
        });
    }

    /**
     * 특정 채팅방에서 실시간 메시지를 전송하는 메서드
     * → 서버가 메시지를 해당 방에 보내줄 때 호출됨
     */
    @Transactional
    public <T> void broadcast(String roomId, T payload) {
        final String json;
        try {
            // 1. 객체를 JSON 문자열로 변환
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.error("직렬화 실패 roomId={}", roomId, e);
            return;
        }

        // JSON 문자열을 TextMessage(WebSocket 전송용)로 변환
        final TextMessage msg = new TextMessage(json);
        final Set<WebSocketSession> sessions = sessionsByRoom.get(roomId);
        if (sessions == null || sessions.isEmpty()) return;

        List<WebSocketSession> toRemove = new ArrayList<>();

        // 3. 각 세션에 전송 시도

        for (WebSocketSession s : sessions) {
            // 닫힌 세션은 제거
            if (s == null || !s.isOpen()) {
                toRemove.add(s);
                continue;
            }
            try {
                // 메시지 전송
                s.sendMessage(msg);
            } catch (Exception e) { // IOException 포함
                log.warn("WS 전송 실패 -> 제거 roomId={}, sessionId={}", roomId, s.getId(), e);
                toRemove.add(s);
            }
        }

        // 4. 전송 실패/닫힌 세션 제거
        if (!toRemove.isEmpty()) {
            sessions.removeAll(toRemove); // ConcurrentHashMap.newKeySet() 과 안전하게 동작
        }
    }

    /**
     * 채팅 메시지를 DB에 저장하고 해당 채팅방의 점주(Owner)와 소비자(Customer) 에게 실시간 전송
     * → 사용자가 채팅을 보냈을 때 호출됨
     */
    //채팅 메시지를 먼저 DB에 저장해야 나중에 메시지 내역을 조회하거나 복구할 수 있습니다.
    //-> 만약 전송을 먼저 하고 DB 저장이 실패하면, 상대방은 메시지를 받았지만 서버에는 기록이 없어 대화 히스토리가 불일치하게 됩니다.
    //반대로, DB 저장이 성공한 뒤 전송이 실패하면(네트워크 문제 등) 재전송 로직을 통해 동일한 데이터를 다시 보낼 수 있습니다.
    @Transactional
    public void saveAndBroadcast(ChatMessageResponseDto dto) {
        // DB 저장 (roomId 포함 -> 과거 내역 조회시 필요)
        ChatMessageEntity saved = chatMessageRepository.save(ChatMessageEntity.from(dto));

        //외부로는 sender + message +sentAt 만 보냄
        BroadcastMessageResponseDto broadcastMessageResponseDto = BroadcastMessageResponseDto.builder()
                .sender(dto.getSender())
                .message(dto.getMessage())
                // dto.getSender()가 null 이면 "UNKNOWN"
                // null 아니면 그대로 반환
                .sentAt(Objects.requireNonNullElse(saved.getCreatedAt(), LocalDateTime.now()))
                .build();

        //실시간 전송
        broadcast(dto.getRoomId(), broadcastMessageResponseDto);
    }

    /**
     * 사용자가 해당 채팅방에 접근 가능한지 확인
     * → 점주(Owner) 또는 소비자(Customer)일 경우 true, 그 외에는 false
     */
    @Transactional(readOnly = true)
    public boolean canAccessRoom(Long memberId, String roomId) {
        ChatRoomEntity room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new NotFoundProposalFormException("채팅방이 존재하지 않습니다."));

        ProposalForm form = room.getProposalForm();

        // 점주 또는 소비자인 경우만 반환
        return form.getRequestForm().getCustomer().getId().equals(memberId)
                || form.getOwner().getId().equals(memberId);
    }

    /**
     * 과거 채팅 메시지 페이징 조회 (오름차순 정렬)
     * → 클라이언트에서 이전 채팅 내역 조회 요청 시 호출됨
     */
    @Transactional(readOnly = true)
    public ApiResponse<List<ChatMessageHistoryResponseDto>> getHistory(
            Long memberId, String roomId) {

        // 해당 채팅방에 접근 가능한 사용자인지 검증
        if (!canAccessRoom(memberId, roomId)) {
            throw new UnauthorizedAccessException("해당 채팅방에 접근 권한이 없습니다.");
        }

            List<ChatMessageHistoryResponseDto> history =
                chatMessageRepository.findByRoomIdOrderByCreatedAtAsc(roomId).stream()
                        .map(ChatMessageHistoryResponseDto::from)
                        .collect(Collectors.toList());

        return ApiResponse.success(HttpStatus.OK, "메시지 조회 성공", history);
    }
}
