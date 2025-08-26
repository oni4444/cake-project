package com.cakemate.cake_platform.domain.proposalForm.dto;

import com.cakemate.cake_platform.domain.proposalFormChat.dto.ChatHistorySectionDto;
import lombok.Getter;

import java.util.List;

@Getter
public class CustomerProposalFormDetailDto {
    //속성
    private RequestFormDataDto requestForm;
    private CustomerProposalFormDataDto proposalForm;
    private List<CommentDataDto> comments;

    //생성자
    public CustomerProposalFormDetailDto(RequestFormDataDto requestForm, CustomerProposalFormDataDto proposalForm, List<CommentDataDto> comments) {
        this.requestForm = requestForm;
        this.proposalForm = proposalForm;
        this.comments = comments;
    }

    //기능
}
