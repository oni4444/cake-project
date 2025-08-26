package com.cakemate.cake_platform.domain.proposalForm.service;

import com.cakemate.cake_platform.common.dto.ApiResponse;
import com.cakemate.cake_platform.common.exception.ProposalFormNotFoundException;
import com.cakemate.cake_platform.common.exception.UnauthorizedAccessException;

import com.cakemate.cake_platform.domain.auth.entity.Customer;
import com.cakemate.cake_platform.domain.auth.entity.Owner;
import com.cakemate.cake_platform.domain.notification.service.NotificationService;
import com.cakemate.cake_platform.domain.proposalForm.dto.*;
import com.cakemate.cake_platform.domain.proposalForm.entity.ProposalForm;
import com.cakemate.cake_platform.domain.proposalForm.enums.ProposalFormStatus;
import com.cakemate.cake_platform.domain.proposalForm.exception.InvalidProposalStatusException;
import com.cakemate.cake_platform.domain.proposalForm.exception.ProposalAlreadyAcceptedException;
import com.cakemate.cake_platform.domain.proposalForm.repository.ProposalFormRepository;
import com.cakemate.cake_platform.domain.proposalFormChat.dto.ChatHistorySectionDto;
import com.cakemate.cake_platform.domain.proposalFormChat.dto.ChatMessageHistoryResponseDto;
import com.cakemate.cake_platform.domain.proposalFormChat.dto.ChatRoomResponseDto;
import com.cakemate.cake_platform.domain.proposalFormChat.entity.ChatMessageEntity;
import com.cakemate.cake_platform.domain.proposalFormChat.repository.ChatMessageRepository;
import com.cakemate.cake_platform.domain.proposalFormChat.service.ChatService;
import com.cakemate.cake_platform.domain.proposalFormComment.entity.ProposalFormComment;
import com.cakemate.cake_platform.domain.proposalFormComment.repository.ProposalFormCommentRepository;
import com.cakemate.cake_platform.domain.requestForm.entity.RequestForm;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class CustomerProposalFormService {

    private final ProposalFormRepository proposalFormRepository;
    private final ProposalFormCommentRepository proposalFormCommentRepository;
    private final ChatService chatService;
    private final ChatMessageRepository chatMessageRepository;
    private final NotificationService notificationService;

    public CustomerProposalFormService(ProposalFormRepository proposalFormRepository,
                                       ProposalFormCommentRepository proposalFormCommentRepository, ChatService chatService, ChatMessageRepository chatMessageRepository, NotificationService notificationService) {
        this.proposalFormRepository = proposalFormRepository;
        this.proposalFormCommentRepository = proposalFormCommentRepository;
        this.chatService = chatService;
        this.chatMessageRepository = chatMessageRepository;
        this.notificationService = notificationService;
    }

    /**
     * customer 전용 견적서 상세 조회
     */

    @Transactional(readOnly = true)
    public ApiResponse<CustomerProposalFormDetailDto> getProposalFormDetailForCustomer(Long proposalFormId, Long customerId) {
        ProposalForm proposalForm = proposalFormRepository.findById(proposalFormId)
                .orElseThrow(() -> new ProposalFormNotFoundException("해당 견적서가 존재하지 않습니다."));

        RequestForm requestForm = proposalForm.getRequestForm();

        //검증 로직
        if (!requestForm.getCustomer().getId().equals(customerId)) {
            throw new UnauthorizedAccessException("조회 권한이 없습니다.");
        }

        String roomId = chatService.findRoomId(proposalForm.getId())
                .orElse(null);
        // 의뢰서 DTO 만들기
        RequestFormDataDto requestFormDto = new RequestFormDataDto(
                requestForm.getId(),
                requestForm.getTitle(),
                requestForm.getRegion(),
                requestForm.getCakeSize(),
                requestForm.getQuantity(),
                requestForm.getContent(),
                requestForm.getDesiredPrice(),
                requestForm.getImage(),
                requestForm.getDesiredPickupDate(),
                requestForm.getStatus().name(),
                requestForm.getCreatedAt()
        );

        //견적서 DTO 만들기
        CustomerProposalFormDataDto proposalFormDto = new CustomerProposalFormDataDto(
                proposalForm.getId(),
                proposalForm.getRequestForm().getId(),
                proposalForm.getStoreName(),
                proposalForm.getStoreAddress(),
                proposalForm.getTitle(),
                proposalForm.getCakeSize(),
                proposalForm.getQuantity(),
                proposalForm.getContent(),
                proposalForm.getProposedPrice(),
                proposalForm.getProposedPickupDate(),
                proposalForm.getCreatedAt(),
                proposalForm.getModifiedAt(),
                proposalForm.getStatus().name(),
                proposalForm.getImage(),
                roomId
        );

        //댓글 리스트 DTO 만들기
        List<ProposalFormComment> comments = proposalFormCommentRepository.findByProposalForm(proposalForm);

        List<CommentDataDto> commentDataDtos = comments.stream()
                .map(comment -> {
                    Long writerCustomerId = null;
                    if (comment.getCustomer() != null) {
                        writerCustomerId = comment.getCustomer().getId();
                    }
                    Long ownerId = null;
                    if (comment.getOwner() != null) {
                        ownerId = comment.getOwner().getId();
                    }
                    return new CommentDataDto(
                            comment.getId(),
                            writerCustomerId,
                            ownerId,
                            comment.getContent(),
                            comment.getCreatedAt()
                    );
                })
                .collect(Collectors.toList());


        //chatSection 추가
        CustomerProposalFormDetailDto responseDto = new CustomerProposalFormDetailDto(requestFormDto, proposalFormDto, commentDataDtos);

        return ApiResponse.success(HttpStatus.OK, "success", responseDto);
    }

    /**
     * 소비자 -> 채팅방 생성 기능
     */
    @Transactional
    public ApiResponse<CreateRoomByCustomerResponseDto> createRoomByCustomer(Long proposalFormId, Long customerId) {
        /* 1) 소유권 + 삭제 여부 한 번에 검증 */
        boolean myForm = proposalFormRepository
                .existsByIdAndRequestForm_Customer_IdAndIsDeletedFalse(
                        proposalFormId, customerId);
        if (!myForm) {
            throw new UnauthorizedAccessException("본인의 의뢰서가 아닙니다.");
        }

        //견적서마다 최초로 한 번만 채팅방 생성
        ChatRoomResponseDto chatDto = chatService
                .createRoomIfAbsent(proposalFormId, customerId)
                .getData();

        // 5) DTO 에 채팅방 ID 추가
        CreateRoomByCustomerResponseDto createRoomByCustomerResponseDto
                = new CreateRoomByCustomerResponseDto(
                chatDto.getRoomId()
        );

        return ApiResponse.success(
                HttpStatus.OK, "채팅 생성이 완료되었습니다.", createRoomByCustomerResponseDto
        );
    }

    /**
     * 소비자 -> 견적서 선택 기능
     */
    @Transactional
    public ApiResponse<CustomerProposalFormAcceptResponseDto> acceptProposalFormByCustomer(Long proposalFormId, Long customerId) {
        /* 1) 소유권 + 삭제 여부 한 번에 검증 */
        boolean myForm = proposalFormRepository
                .existsByIdAndRequestForm_Customer_IdAndIsDeletedFalse(
                        proposalFormId, customerId);
        if (!myForm) {
            throw new UnauthorizedAccessException("본인의 의뢰서가 아닙니다.");
        }

        /* 2) 이미 다른 견적서를 ACCEPTED 했는지 검사 */
        Long requestFormId = proposalFormRepository.getReferenceById(proposalFormId)
                .getRequestForm().getId();

        boolean alreadyAccepted = proposalFormRepository
                .existsByRequestForm_IdAndStatusAndIsDeletedFalse(
                        requestFormId, ProposalFormStatus.ACCEPTED);

        if (alreadyAccepted) {
            throw new ProposalAlreadyAcceptedException("이미 선택한 견적서가 있습니다.");
        }

        /* 3) 상태 변경 → ACCEPTED */
        ProposalForm proposalForm = proposalFormRepository.getReferenceById(proposalFormId);
        proposalForm.acceptStatus(ProposalFormStatus.ACCEPTED);
        ProposalFormStatus proposalFormStatus = proposalForm.getStatus();

        //견적서 수락 알림 보내기(소비자->점주)
        String storeName = proposalForm.getStoreName();
        String message;
        Owner owner = proposalForm.getOwner();
        Customer customer = proposalForm.getRequestForm().getCustomer();

        if (proposalFormStatus == ProposalFormStatus.ACCEPTED) {
            message = String.format("의뢰인이 %s님의 견적서를 수락했습니다.", storeName);
            notificationService.sendNotification(owner.getId(), message, "owner");
        }

        // 5) DTO 에 채팅방 ID 추가
        CustomerProposalFormAcceptResponseDto customerProposalFormAcceptResponseDto
                = new CustomerProposalFormAcceptResponseDto(
                proposalForm.getId(),
                proposalForm.getStatus()
        );

        return ApiResponse.success(
                HttpStatus.OK, "견적서 선택이 완료되었습니다.", customerProposalFormAcceptResponseDto
        );
    }
}