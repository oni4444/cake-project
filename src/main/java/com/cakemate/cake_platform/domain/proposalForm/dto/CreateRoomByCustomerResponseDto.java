package com.cakemate.cake_platform.domain.proposalForm.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CreateRoomByCustomerResponseDto {
    //값이 null 일 때 응답에서 안 보이게 하는 어노테이션
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String chatRoomId;
}
