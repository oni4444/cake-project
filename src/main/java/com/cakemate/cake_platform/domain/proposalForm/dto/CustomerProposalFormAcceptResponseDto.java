package com.cakemate.cake_platform.domain.proposalForm.dto;

import com.cakemate.cake_platform.domain.proposalForm.enums.ProposalFormStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CustomerProposalFormAcceptResponseDto {

    private Long proposalId;
    private ProposalFormStatus proposalFormStatus;
}
