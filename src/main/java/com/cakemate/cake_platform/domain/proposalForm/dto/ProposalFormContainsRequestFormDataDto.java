package com.cakemate.cake_platform.domain.proposalForm.dto;

import com.cakemate.cake_platform.domain.proposalFormChat.dto.ChatHistorySectionDto;
import lombok.Getter;

import java.util.List;

/**
 * proposalFormDetailResponseDto에서 활용하는 객체입니다!!
 * 단건 상세 조회 시 사용
 */
@Getter
public class ProposalFormContainsRequestFormDataDto {
    //속성
    private RequestFormDataDto requestForm;
    private ProposalFormDataDto proposalForm;
    private List<CommentDataDto> comments;
    //생성자
    public ProposalFormContainsRequestFormDataDto(RequestFormDataDto requestForm, ProposalFormDataDto proposalForm, List<CommentDataDto> comments) {
        this.requestForm = requestForm;
        this.proposalForm = proposalForm;
        this.comments = comments;
    }

    //기능
}