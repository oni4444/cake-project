package com.cakemate.cake_platform.domain.member.owner.service;

import com.cakemate.cake_platform.common.config.PasswordValidator;
import com.cakemate.cake_platform.common.dto.ApiResponse;
import com.cakemate.cake_platform.common.exception.MemberAlreadyDeletedException;
import com.cakemate.cake_platform.common.exception.MemberNotFoundException;
import com.cakemate.cake_platform.domain.auth.entity.Owner;
import com.cakemate.cake_platform.domain.auth.exception.BadRequestException;
import com.cakemate.cake_platform.domain.auth.signup.owner.repository.OwnerRepository;
import com.cakemate.cake_platform.domain.member.owner.dto.request.UpdateOwnerProfileRequestDto;
import com.cakemate.cake_platform.domain.member.owner.dto.response.OwnerProfileResponseDto;
import com.cakemate.cake_platform.domain.member.owner.dto.response.UpdateOwnerProfileResponseDto;
import com.cakemate.cake_platform.domain.member.repository.MemberRepository;
import com.cakemate.cake_platform.domain.store.entity.Store;
import com.cakemate.cake_platform.domain.store.owner.exception.NotFoundOwnerException;
import com.cakemate.cake_platform.domain.store.repository.StoreRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class OwnerManagementService {

    private final MemberRepository memberRepository;
    private final OwnerRepository ownerRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordValidator passwordValidator;
    private final StoreRepository storeRepository;


    public OwnerManagementService(MemberRepository memberRepository, OwnerRepository ownerRepository, PasswordEncoder passwordEncoder, PasswordValidator passwordValidator, StoreRepository storeRepository) {
        this.memberRepository = memberRepository;
        this.ownerRepository = ownerRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordValidator = passwordValidator;
        this.storeRepository = storeRepository;
    }

    /**
     * 점주 -> 내 정보 조회 Service
     * @param ownerId
     * @return
     */
    public OwnerProfileResponseDto getOwnerProfileService(Long ownerId) {
        Owner owner = ownerRepository.findById(ownerId)
                .orElseThrow(() -> new MemberNotFoundException("존재하지 않는 회원입니다."));

        if (owner.isDeleted()) {
            throw new MemberAlreadyDeletedException("이미 탈퇴한 회원입니다.");
        }

        OwnerProfileResponseDto responseDto = new OwnerProfileResponseDto(
                owner.getName(),
                owner.getEmail(),
                owner.getPhoneNumber()
        );

        return responseDto;
    }

    /**
     * (점주) 내 정보 수정 서비스
     */
    @Transactional
    public ApiResponse<UpdateOwnerProfileResponseDto> putUpdateOwnerService(
            Long ownerId, UpdateOwnerProfileRequestDto updateOwnerProfileRequestDto
    ) {
        //데이터준비
        String password = updateOwnerProfileRequestDto.getPassword();
        String passwordConfirm = updateOwnerProfileRequestDto.getPasswordConfirm();
        String phoneNumber = updateOwnerProfileRequestDto.getPhoneNumber();

        //점주 조회 & 예외처리
        Owner owner = ownerRepository.findByIdAndIsDeletedFalse(ownerId)
                .orElseThrow(() -> new NotFoundOwnerException("점주 정보를 찾을 수 없습니다."));

        // 전화번호 검증 → null 이거나 정규식 불일치면 예외
        if (phoneNumber == null || !phoneNumber.matches("^010-[0-9]{4}-[0-9]{4}$")) {
            throw new BadRequestException("핸드폰 번호 형식을 지켜주세요(010-xxxx-xxxx)");
        }

        // 비밀번호 변경 검증 및 처리 -> null 이거나 비밀번호 확인이 불일치면 예외
        if (passwordValidator.isPasswordChangeRequested(password)) {
            if (passwordConfirm == null || !password.equals(passwordConfirm)) {
                throw new BadRequestException("비밀번호와 비밀번호 확인이 일치하지 않습니다.");
            }
            owner.changePassword(passwordEncoder.encode(password));
        }
        // 이름 & 전화번호 업데이트
        Owner updateOwnerProfile = owner.updateProfile(phoneNumber);
        Long foundOwnerId = updateOwnerProfile.getId();
        String updateOwnerProfileName = updateOwnerProfile.getName();
        String updateOwnerProfileEmail = updateOwnerProfile.getEmail();
        String updateOwnerProfilePhoneNumber = updateOwnerProfile.getPhoneNumber();


        UpdateOwnerProfileResponseDto updateOwnerProfileResponseDto = new UpdateOwnerProfileResponseDto(
                foundOwnerId, updateOwnerProfileName,
                updateOwnerProfileEmail, updateOwnerProfilePhoneNumber
        );

        return ApiResponse.success(
                HttpStatus.OK, "점주 정보가 성공적으로 수정되었습니다.", updateOwnerProfileResponseDto
        );
    }

    /**
     * 점주 회원 탈퇴
     */
    @Transactional
    public ApiResponse<Void> deleteOwnerProfileService(Long ownerId) {
        Owner owner = ownerRepository.findByIdAndIsDeletedFalse(ownerId)
                .orElseThrow(() -> new NotFoundOwnerException("점주 정보를 찾을 수 없습니다."));

        // 이미 삭제되었으면 예외 던지기 (optional)
        if (owner.isDeleted()) {
            throw new IllegalStateException("이미 탈퇴한 점주입니다.");
        }

        // soft delete 처리
        owner.delete();
        ownerRepository.save(owner);

        return ApiResponse.success(HttpStatus.OK, owner.getName() + "님, 회원탈퇴가 정상적으로 완료되었습니다.", null);

    }
}
