package com.cakemate.cake_platform.domain.proposalForm.service;

import com.cakemate.cake_platform.common.dto.ApiResponse;
import com.cakemate.cake_platform.common.exception.*;
import com.cakemate.cake_platform.domain.auth.entity.Customer;
import com.cakemate.cake_platform.domain.auth.entity.Owner;
import com.cakemate.cake_platform.domain.auth.exception.OwnerNotFoundException;
import com.cakemate.cake_platform.domain.member.entity.Member;
import com.cakemate.cake_platform.domain.member.repository.MemberRepository;
import com.cakemate.cake_platform.domain.notification.service.NotificationService;
import com.cakemate.cake_platform.domain.proposalForm.dto.*;
import com.cakemate.cake_platform.domain.proposalForm.entity.ProposalForm;
import com.cakemate.cake_platform.common.commonEnum.CakeSize;
import com.cakemate.cake_platform.domain.proposalForm.enums.ProposalFormStatus;
import com.cakemate.cake_platform.domain.proposalForm.exception.*;
import com.cakemate.cake_platform.domain.proposalForm.exception.ResourceNotFoundException;
import com.cakemate.cake_platform.domain.proposalForm.repository.ProposalFormRepository;
import com.cakemate.cake_platform.domain.proposalFormChat.dto.ChatHistorySectionDto;
import com.cakemate.cake_platform.domain.proposalFormChat.dto.ChatMessageHistoryResponseDto;
import com.cakemate.cake_platform.domain.proposalFormChat.entity.ChatMessageEntity;
import com.cakemate.cake_platform.domain.proposalFormChat.entity.ChatRoomEntity;
import com.cakemate.cake_platform.domain.proposalFormChat.repository.ChatMessageRepository;
import com.cakemate.cake_platform.domain.proposalFormChat.repository.ChatRoomRepository;
import com.cakemate.cake_platform.domain.proposalFormChat.service.ChatService;
import com.cakemate.cake_platform.domain.proposalFormComment.entity.ProposalFormComment;
import com.cakemate.cake_platform.domain.proposalFormComment.repository.ProposalFormCommentRepository;
import com.cakemate.cake_platform.domain.requestForm.entity.RequestForm;
import com.cakemate.cake_platform.domain.requestForm.repository.RequestFormRepository;
import com.cakemate.cake_platform.domain.store.entity.Store;
import com.cakemate.cake_platform.domain.store.repository.StoreRepository;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Getter
@Service
public class ProposalFormService {

    private final ProposalFormRepository proposalFormRepository;
    private final RequestFormRepository requestFormRepository;
    private final ProposalFormCommentRepository proposalFormCommentRepository;
    private final StoreRepository storeRepository;
    private final MemberRepository memberRepository;
    private final ChatService chatService;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final NotificationService notificationService;

    public ProposalFormService(ProposalFormRepository proposalFormRepository, RequestFormRepository requestFormRepository, ProposalFormCommentRepository proposalFormCommentRepository, StoreRepository storeRepository, MemberRepository memberRepository, ChatService chatService, ChatRoomRepository chatRoomRepository, ChatMessageRepository chatMessageRepository, NotificationService notificationService) {
        this.proposalFormRepository = proposalFormRepository;
        this.requestFormRepository = requestFormRepository;
        this.proposalFormCommentRepository = proposalFormCommentRepository;
        this.storeRepository = storeRepository;
        this.memberRepository = memberRepository;
        this.chatService = chatService;
        this.chatRoomRepository = chatRoomRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.notificationService = notificationService;
    }

    /**
     * proposalForm 생성 서비스
     */
    @Transactional
    public ApiResponse<ProposalFormDataDto> createProposal(Long ownerId, ProposalFormCreateRequestDto requestDto) {
        //데이터 준비
        //멤버 조회, owner 조회
        Member member = memberRepository.findByOwnerId(ownerId)
                .orElseThrow(() -> new OwnerNotFoundException("해당 점주를 찾을 수 없습니다."));
        Owner owner = member.getOwner();
        if (owner == null) {
            throw new ResourceNotFoundException("해당하는 점주 정보가 없습니다.");
        }
        //owner가 가진 store 가져오기
        Store store = storeRepository.findByOwnerId(ownerId)
                .orElseThrow(() -> new StoreNotFoundException("해당 점주의 가게를 찾을 수 없습니다."));
        String storeName = store.getName();

        RequestForm requestForm = requestFormRepository.findById(requestDto.getRequestFormId())
                .orElseThrow(() -> new RequestFormNotFoundException("해당 의뢰서를 찾을 수 없습니다."));

        //검증 로직(견적서 중복 생성 방지)
        boolean exists = proposalFormRepository.existsByRequestFormAndOwner(requestForm, owner);
        if (exists) {
            throw new ProposalFormAlreadyExistsException("해당 의뢰서에 대한 견적서가 이미 존재합니다.");
        }
        //케이크 사이즈, 수량, 가격, 픽업 날짜 등 제한 사항
        CakeSize cakeSize = requestDto.getCakeSize();
        int quantity = requestDto.getQuantity();

        if (quantity < 1 || quantity > 5) {
            throw new InvalidQuantityException("수량은 1개 이상 5개 이하만 가능합니다. 6개 이상은 가게로 문의 주세요.");
        }

        int minTotalPrice = cakeSize.getMinPrice() * quantity;
        if (requestDto.getProposedPrice() < minTotalPrice) {
            throw new InvalidPriceException("케이크 사이즈(" + cakeSize.name() + ") 및 수량(" + quantity + "개)에 따른 최소 가격은 " + minTotalPrice + "원입니다.");
        }

        if (requestDto.getProposedPickupDate().isBefore(LocalDateTime.now())) {
            throw new InvalidProposedPickupDateException("픽업일은 현재 시간보다 이후여야 합니다.");
        }

        //엔티티 만들기
        ProposalForm proposalForm = new ProposalForm(
                requestForm,
                store,
                owner,
                store.getName(),
                store.getAddress(),
                requestDto.getTitle(),
                requestDto.getCakeSize(),
                requestDto.getQuantity(),
                requestDto.getContent(),
                requestDto.getManagerName(),
                requestDto.getProposedPrice(),
                requestDto.getProposedPickupDate(),
                requestDto.getImage(),
                ProposalFormStatus.AWAITING  //기본 상태
        );

        //최초 견적서 등록 시 의뢰서 상태 변경
        long count = proposalFormRepository.countByRequestForm(requestForm);
        if (count == 0) {
            requestForm.updateStatusToHasProposal();
        }

        //저장
        ProposalForm savedProposalForm = proposalFormRepository.save(proposalForm);

        //DTO 만들기
        ProposalFormDataDto dataDto = new ProposalFormDataDto(
                savedProposalForm.getId(),
                savedProposalForm.getRequestForm().getId(),
                savedProposalForm.getStoreName(),
                savedProposalForm.getStoreAddress(),
                savedProposalForm.getTitle(),
                savedProposalForm.getCakeSize(),
                savedProposalForm.getQuantity(),
                savedProposalForm.getContent(),
                savedProposalForm.getManagerName(),
                savedProposalForm.getProposedPrice(),
                savedProposalForm.getProposedPickupDate(),
                savedProposalForm.getCreatedAt(),
                savedProposalForm.getModifiedAt(),
                savedProposalForm.getStatus().name(),
                savedProposalForm.getImage()
        );

        //응답 DTO 만들기
        ApiResponse<ProposalFormDataDto> response = ApiResponse.success(HttpStatus.CREATED, "created", dataDto);

        //견적서 등록 알림 보내기(점주->소비자)
        Long customerId = savedProposalForm.getRequestForm().getCustomer().getId();
        notificationService.sendNotification(customerId, "새로운 견적서가 도착했습니다.", "customer");

        return response;
    }

    /**
     * proposalForm 단건 상세 조회 서비스
     */
    @Transactional(readOnly = true)
    public ApiResponse<ProposalFormContainsRequestFormDataDto> getProposalFormDetail(Long proposalFormId, Long ownerId) {
        //데이터 준비

        //조회
        ProposalForm foundProposalForm = proposalFormRepository.findById(proposalFormId)
                .orElseThrow(() -> new ProposalFormNotFoundException("해당 견적서가 존재하지 않습니다."));


        //권한 검증 추가
        if (!foundProposalForm.getStore().getOwner().getId().equals(ownerId)) {
            throw new UnauthorizedAccessException("조회 권한이 없습니다.");
        }

        //RequestForm 조회
        RequestForm requestForm = foundProposalForm.getRequestForm();

        //comment List 조회
        List<ProposalFormComment> commentList = proposalFormCommentRepository.findByProposalForm_IdOrderByCreatedAtAsc(proposalFormId);

        String roomId = chatService.findRoomId(foundProposalForm.getId())
                .orElse(null);



        //DTO 만들기(ProposalForm, RequestForm, Comment 데이터를 합쳐 DTO 생성)
        //ProposalForm  Dto 만들기
        ProposalFormDataDto proposalFormDataDto = new ProposalFormDataDto(
                foundProposalForm.getId(),
                requestForm.getId(),
                foundProposalForm.getStore().getName(),
                foundProposalForm.getStore().getAddress(),
                foundProposalForm.getTitle(),
                foundProposalForm.getCakeSize(),
                foundProposalForm.getQuantity(),
                foundProposalForm.getContent(),
                foundProposalForm.getManagerName(),
                foundProposalForm.getProposedPrice(),
                foundProposalForm.getProposedPickupDate(),
                foundProposalForm.getCreatedAt(),
                foundProposalForm.getModifiedAt(),
                foundProposalForm.getStatus().name(),
                foundProposalForm.getImage(),
                roomId
        );

        //RequestFormDto 만들기
        RequestFormDataDto requestFormDataDto = new RequestFormDataDto(
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

        //commentFormDto 리스트 만들기
        List<CommentDataDto> commentDtoList = commentList.stream()
                .map(comment -> {
                    Long customerId = null;
                    //comment에 연결된 customer가 있으면 ID 추출
                    if (comment.getCustomer() != null) {
                        customerId = comment.getCustomer().getId();
                    }
                    //comment에 연결된 owner가 있으면 ID 추출
                    Long commentOwnerId = null;
                    if (comment.getOwner() != null) {
                        commentOwnerId = comment.getOwner().getId();
                    }

                    //CommentDataDto 생성 및 반환
                    return new CommentDataDto(
                            comment.getId(),
                            customerId,
                            commentOwnerId,
                            comment.getContent(),
                            comment.getCreatedAt()
                    );
                })
                .collect(Collectors.toList());


        //응답 DTO 만들기
        ProposalFormContainsRequestFormDataDto responseDto
                = new ProposalFormContainsRequestFormDataDto(
                        requestFormDataDto, proposalFormDataDto, commentDtoList
        );

        //반환
        ApiResponse<ProposalFormContainsRequestFormDataDto> response = ApiResponse.success(HttpStatus.OK, "success", responseDto);
        return response;
    }

    /**
     * proposalForm 목록 조회 서비스
     */
    @Transactional(readOnly = true)
    public ApiResponse<List<ProposalFormDataDto>> getProposalFormList(Long ownerId) {
        //조회 권한 확인(점주 본인이 작성한 견적서 목록 조회)
        List<ProposalForm> proposalFormList = proposalFormRepository.findByStore_Owner_Id(ownerId);

        if (proposalFormList.isEmpty()) {
            return ApiResponse.success(HttpStatus.OK, "등록된 견적서가 없습니다.", Collections.emptyList());
        }

        //DTO 만들기 (ProposalForm과 RequestForm 데이터를 합쳐 DTO 생성)
        List<ProposalFormDataDto> dataList = proposalFormList.stream()
                .map(proposalForm -> new ProposalFormDataDto(
                        proposalForm.getId(),
                        proposalForm.getRequestForm().getId(),
                        proposalForm.getStore().getName(),
                        proposalForm.getStore().getAddress(),
                        proposalForm.getTitle(),
                        proposalForm.getCakeSize(),
                        proposalForm.getQuantity(),
                        proposalForm.getContent(),
                        proposalForm.getManagerName(),
                        proposalForm.getProposedPrice(),
                        proposalForm.getProposedPickupDate(),
                        proposalForm.getCreatedAt(),
                        proposalForm.getModifiedAt(),
                        proposalForm.getStatus().name(),
                        proposalForm.getImage()
                ))
                .collect(Collectors.toList());

        return ApiResponse.success(HttpStatus.OK, "success", dataList);
    }

    /**
     * proposalForm 수정 서비스
     */
    @Transactional
    public ApiResponse<ProposalFormDataDto> updateProposalForm(Long proposalFormId, Long ownerId, ProposalFormUpdateRequestDto requestDto) {
        //데이터 준비
        //조회
        Member foundOwner = memberRepository.findByOwnerId(ownerId)
                .orElseThrow(() -> new OwnerNotFoundException("해당 점주를 찾을 수 없습니다."));
        Owner owner = foundOwner.getOwner();
        if (owner == null) {
            throw new OwnerNotFoundException("해당 회원은 점주 정보가 없습니다.");
        }
        ProposalForm foundProposalForm = proposalFormRepository.findById(proposalFormId)
                .orElseThrow(() -> new ProposalFormNotFoundException("해당 견적서가 존재하지 않습니다."));
        //검증 로직(해당 점주가 작성한 견적서인지 확인)
        if (!foundProposalForm.getOwner().getId().equals(owner.getId())) {
            throw new ProposalFormUpdateAccessDeniedException("견적서에 대한 수정 권한이 없습니다.");
        }
        //status 검증(AWAITING,ACCEPTED일 때만 수정 가능)
        if (!(foundProposalForm.getStatus() == ProposalFormStatus.AWAITING
                || foundProposalForm.getStatus() == ProposalFormStatus.ACCEPTED)) {
            throw new ProposalFormUpdateInvalidStatusException("AWAITING 또는 ACCEPTED 상태일 때만 수정할 수 있습니다.");
        }

        //날짜, 가격 제한(과거 날짜, 마이너스 값 불가)
        if (requestDto.getProposedPickupDate().isBefore(LocalDateTime.now())) {
            throw new InvalidProposedPickupDateException("픽업일은 현재 시간보다 이후여야 합니다.");
        }
        if (requestDto.getProposedPrice() < 0) {
            throw new InvalidProposedPriceException("올바르지 않은 입력값입니다.");
        }

        //수정
        foundProposalForm.update(
                requestDto.getTitle(),
                requestDto.getCakeSize(),
                requestDto.getQuantity(),
                requestDto.getContent(),
                requestDto.getManagerName(),
                requestDto.getProposedPrice(),
                requestDto.getProposedPickupDate(),
                requestDto.getImage()
        );

        //저장
        ProposalForm updatedProposalForm = proposalFormRepository.save(foundProposalForm);

        //견적서 수정 알림 보내기(점주->소비자)
        Customer customer = updatedProposalForm.getRequestForm().getCustomer();
        String message = "견적서의 내용이 수정되었습니다. 변동 사항을 확인 해주세요.";
        notificationService.sendNotification(customer.getId(), message, "customer");

        //응답 DTO 만들기
        ProposalFormDataDto responseDto = new ProposalFormDataDto(
                updatedProposalForm.getId(),
                updatedProposalForm.getRequestForm().getId(),
                updatedProposalForm.getStoreName(),
                updatedProposalForm.getStoreAddress(),
                updatedProposalForm.getTitle(),
                updatedProposalForm.getCakeSize(),
                updatedProposalForm.getQuantity(),
                updatedProposalForm.getContent(),
                updatedProposalForm.getManagerName(),
                updatedProposalForm.getProposedPrice(),
                updatedProposalForm.getProposedPickupDate(),
                updatedProposalForm.getCreatedAt(),
                updatedProposalForm.getModifiedAt(),
                updatedProposalForm.getStatus().name(),
                updatedProposalForm.getImage()
                );
        //응답 반환
        ApiResponse<ProposalFormDataDto> response = ApiResponse.success(HttpStatus.OK, "updated", responseDto);
        return response;
    }

    /**
     * proposalForm 삭제 서비스
     */
    @Transactional
    public ApiResponse<String> deleteProposalForm(Long proposalFormId, Long ownerId) {
        //조회
        Member foundOwner = memberRepository.findById(ownerId)
                .orElseThrow(() -> new OwnerNotFoundException("해당 점주를 찾을 수 없습니다."));

        ProposalForm foundProposalForm = proposalFormRepository.findById(proposalFormId)
                .orElseThrow(() -> new ProposalFormNotFoundException("해당 제안서가 존재하지 않습니다."));

        //권한 확인
        if (!foundProposalForm.getOwner().getId().equals(foundOwner.getId())) {
            throw new ProposalFormDeleteAccessDeniedException("삭제 권한이 없습니다.");
        }

        //status 확인 (예: AWAITING 상태만 삭제 가능하도록 제한)
        if (foundProposalForm.getStatus() != ProposalFormStatus.AWAITING) {
            throw new ProposalFormDeleteInvalidStatusException("AWAITING 상태만 삭제할 수 있습니다.");
        }

        //삭제(논리 삭제)
        foundProposalForm.delete();

        //응답 반환
        ApiResponse<String> response = ApiResponse.success(HttpStatus.OK, "deleted", null);
        return response;
    }

    /**
     * proposalForm 최종 확정 서비스
     */
    @Transactional
    public OwnerProposalFormConfirmResponseDto confirmProposalForm(Long proposalFormId, Long ownerId) {
        ProposalForm proposalForm = proposalFormRepository.findByIdAndIsDeletedFalse(proposalFormId)
                .orElseThrow(() -> new ProposalFormNotFoundException("견적서를 찾을 수 없습니다."));

        if (!proposalForm.getOwner().getId().equals(ownerId)) {
            throw new UnauthorizedAccessException("접근 권한이 없습니다.");
        }

        proposalForm.confirmStatus(ProposalFormStatus.CONFIRMED);
        ProposalFormStatus proposalFormStatus = proposalForm.getStatus();

        chatService.getRoomIdOrThrow(proposalFormId, ownerId);

        //견적서 확정 알림 보내기(점주->소비자)
        if (proposalFormStatus == ProposalFormStatus.CONFIRMED) {
            Customer customer = proposalForm.getRequestForm().getCustomer();
            String message = "견적서의 최종 내용을 확인 해주세요.";
            notificationService.sendNotification(customer.getId(), message, "customer");
        }

        OwnerProposalFormConfirmResponseDto responseDto = new OwnerProposalFormConfirmResponseDto(proposalForm.getId(), proposalForm.getStatus());
        return responseDto;
    }

    @Transactional(readOnly = true)
    public Optional<String> findRoomId(Long proposalFormId) {
        return chatRoomRepository.findByProposalForm_Id(proposalFormId)
                .map(ChatRoomEntity::getId);
    }
}