package com.cakemate.cake_platform.domain.proposalForm.dto;

import com.cakemate.cake_platform.common.commonEnum.CakeSize;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * CustomerProposalFormDetailResponseDto에서 활용하는 객체입니다!!
 * 소비자용 단건 상세 조회 시 사용
 */

@Getter
public class CustomerProposalFormDataDto {
    //속성
    private Long proposalFormId;
    private Long requestFormId;
    private String storeName;
    private String storeAddress;
    private String title;
    private CakeSize cakeSize;
    private int quantity;
    private String content;
    private int proposedPrice;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm", timezone = "Asia/Seoul")
    private LocalDateTime proposedPickupDate;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm", timezone = "Asia/Seoul")
    private LocalDateTime createdAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm", timezone = "Asia/Seoul")
    private LocalDateTime modifiedAt;

    private String status;
    private String image;

    //값이 null 일 때 응답에서 안 보이게 하는 어노테이션
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String chatRoomId;

    //customer 전용 생성자(담당자 정보 제외)
    public CustomerProposalFormDataDto(Long proposalFormId, Long requestFormId, String storeName, String storeAddress, String title, CakeSize cakeSize, int quantity, String content, int proposedPrice, LocalDateTime proposedPickupDate, LocalDateTime createdAt, LocalDateTime modifiedAt, String status, String image) {
        this.proposalFormId = proposalFormId;
        this.requestFormId = requestFormId;
        this.storeName = storeName;
        this.storeAddress = storeAddress;
        this.title = title;
        this.cakeSize = cakeSize;
        this.quantity = quantity;
        this.content = content;
        this.proposedPrice = proposedPrice;
        this.proposedPickupDate = proposedPickupDate;
        this.createdAt = createdAt;
        this.modifiedAt = modifiedAt;
        this.status = status;
        this.image = image;
    }

    public CustomerProposalFormDataDto(Long proposalFormId, Long requestFormId, String storeName, String storeAddress, String title, CakeSize cakeSize, int quantity, String content, int proposedPrice, LocalDateTime proposedPickupDate, LocalDateTime createdAt, LocalDateTime modifiedAt, String status, String image, String chatRoomId) {
        this.proposalFormId = proposalFormId;
        this.requestFormId = requestFormId;
        this.storeName = storeName;
        this.storeAddress = storeAddress;
        this.title = title;
        this.cakeSize = cakeSize;
        this.quantity = quantity;
        this.content = content;
        this.proposedPrice = proposedPrice;
        this.proposedPickupDate = proposedPickupDate;
        this.createdAt = createdAt;
        this.modifiedAt = modifiedAt;
        this.status = status;
        this.image = image;
        this.chatRoomId = chatRoomId;
    }

    //기능
}
