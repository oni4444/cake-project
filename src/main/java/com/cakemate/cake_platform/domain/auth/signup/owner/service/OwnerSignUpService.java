package com.cakemate.cake_platform.domain.auth.signup.owner.service;

import com.cakemate.cake_platform.common.command.SearchCommand;
import com.cakemate.cake_platform.common.dto.ApiResponse;
import com.cakemate.cake_platform.common.exception.MemberNotFoundException;
import com.cakemate.cake_platform.common.jwt.util.JwtUtil;
import com.cakemate.cake_platform.domain.auth.exception.CustomerNotFoundException;
import com.cakemate.cake_platform.domain.auth.exception.OAuthAccountAlreadyBoundException;
import com.cakemate.cake_platform.domain.auth.OauthKakao.response.KakaoTokenResponse;
import com.cakemate.cake_platform.domain.auth.OauthKakao.response.KakaoUserResponse;
import com.cakemate.cake_platform.domain.auth.exception.EmailAlreadyExistsException;
import com.cakemate.cake_platform.domain.auth.oAuthEnum.OAuthProvider;
import com.cakemate.cake_platform.domain.auth.signin.customer.dto.response.CustomerSignInResponse;
import com.cakemate.cake_platform.domain.auth.signin.owner.dto.response.OwnerSignInResponse;
import com.cakemate.cake_platform.domain.auth.signup.owner.dto.response.OwnerSignUpResponse;
import com.cakemate.cake_platform.domain.auth.exception.OwnerNotFoundException;
import com.cakemate.cake_platform.domain.auth.entity.Owner;
import com.cakemate.cake_platform.domain.auth.signup.owner.repository.OwnerRepository;
import com.cakemate.cake_platform.domain.member.entity.Member;
import com.cakemate.cake_platform.domain.member.repository.MemberRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpSession;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Objects;
import java.util.Optional;

@Slf4j
@Transactional
@Service
public class OwnerSignUpService {
    private final OwnerRepository ownerRepository;
    private final PasswordEncoder passwordEncoder;
    private final MemberRepository memberRepository;
    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    @Value("${kakao.client-id}")
    private String clientId;
    @Value("${kakao.client-secret}")
    private String clientSecret;
    @Value("${kakao.kauth-host}")
    private String kauthHost;
    @Value("${kakao.kapi-host}")
    private String kapiHost;


    public OwnerSignUpService(OwnerRepository ownerRepository, PasswordEncoder passwordEncoder,
                              MemberRepository memberRepository, JwtUtil jwtUtil, ObjectMapper objectMapper, RestClient.Builder restClientBuilder) {
        this.ownerRepository = ownerRepository;
        this.passwordEncoder = passwordEncoder;
        this.memberRepository = memberRepository;
        this.jwtUtil = jwtUtil;
        this.objectMapper = objectMapper;
        this.restClient = restClientBuilder.baseUrl(kapiHost).build();
    }


    public ApiResponse<?> ownerLocalSignUpProcess(SearchCommand ownerSignUpRequest) {
        String email = ownerSignUpRequest.getEmail();
        String password = ownerSignUpRequest.getPassword();
        String passwordConfirm = ownerSignUpRequest.getPasswordConfirm();
        String name = ownerSignUpRequest.getName();
        String phoneNumber = ownerSignUpRequest.getPhoneNumber();
        // 비밀번호 정규식 검증
        ownerSignUpRequest.hasPasswordPattern();
        // 비밀번호, 비밀빈호 확인이 일치하는지
        ownerSignUpRequest.isMatchedPassword();

        boolean existsOwnerEmail = existsOwnerEmail(email);
        if (existsOwnerEmail) {
            throw new EmailAlreadyExistsException();
        }
        String passwordEncode = passwordEncoder.encode(password);
        String passwordConfirmEncode = passwordEncoder.encode(passwordConfirm);

        boolean existsOwner = existsOwner(name, phoneNumber);

        Owner ownerByLocal;
        if (existsOwner) {
            Optional<Owner> ownerByNameAndPhoneNumber =
                    ownerRepository.findByNameAndPhoneNumber(name, phoneNumber);
            if (ownerByNameAndPhoneNumber.isPresent()) {
                OAuthProvider provider = ownerByNameAndPhoneNumber.get().getProvider();
                Member customerByKaKaoInMember = findOwnerByProvider(name,
                        phoneNumber,
                        provider);

                String customerJwtToken = jwtUtil.createMemberJwtToken(customerByKaKaoInMember);
                OwnerSignInResponse ownerSignInResponse = new OwnerSignInResponse(customerJwtToken);

                ApiResponse<OwnerSignInResponse> SignInSuccess
                        = ApiResponse
                        .success(HttpStatus.OK, "환영합니다 " + name + "님", ownerSignInResponse);
                return SignInSuccess;
            }
        }
            // 이름과 핸드폰 번호가 동일 할 경우 어떤 경로로 회원가입 되어 있는지 확인하는 예외처리 추가



        Owner ownerInfo = new Owner(email, passwordEncode, passwordConfirmEncode, name, phoneNumber, OAuthProvider.LOCAL, null);
        ownerByLocal = ownerRepository.save(ownerInfo);

        // 멤버 테이블에 저장
        Long ownerId = ownerInfo.getId();
        Owner owner = ownerRepository
                .findById(ownerId)
                .orElseThrow(() -> new OwnerNotFoundException("OwnerId가 존재하지 않습니다."));
        Member ownerMember = new Member(owner);
        memberRepository.save(ownerMember);

        OwnerSignUpResponse ownerSignUpResponse = new OwnerSignUpResponse(ownerByLocal);

        ApiResponse<OwnerSignUpResponse> signUpSuccess
                = ApiResponse
                .success(HttpStatus.CREATED,
                        ownerByLocal.getName() + "님 회원가입이 완료되었습니다.",
                        ownerSignUpResponse);
        return signUpSuccess;
    }

    private Owner findOwner(String name, String phoneNumber) {
        Owner owner = ownerRepository
                .findByNameAndPhoneNumber(name, phoneNumber)
                .orElseThrow(() -> new OwnerNotFoundException("해당 점주가 존재하지 않습니다"));
        return owner;
    }

    private boolean existsOwnerEmail(String email) {
        return ownerRepository.existsByEmail(email);
    }

    private boolean existsOwner(String name, String phoneNumber) {
        return ownerRepository.existsByNameAndPhoneNumber(name, phoneNumber);
    }



    private Member findOwnerByProvider(String name, String phoneNumber, OAuthProvider provider) {
        return memberRepository.findOwnerByProvider(name, phoneNumber, provider)
                .orElseThrow(() -> new MemberNotFoundException("회원이 존재 하지 않습니다."));
    }

    private Member findOwnerByKaKaoInMember(String name, String phoneNumber, OAuthProvider provider, Long providerId) {
        return memberRepository.findOwnerByKaKao(name, phoneNumber, provider, providerId)
                .orElseThrow(() -> new MemberNotFoundException("회원이 존재하지 않습니다"));
    }

    private KakaoUserResponse retrieveKakaoUser(String code) {
        try {
            KakaoTokenResponse tokenResponse = getToken(code);
            String accessToken = tokenResponse.getAccess_token();

            // accessToken을 RequestContextHolder.currentRequestAttributes()에 저장
            if (accessToken != null) {
                saveAccessToken(accessToken);
            }

            RestClient.RequestBodySpec userProfileRequestSpec = restClient
                    .method(HttpMethod.valueOf("GET"))
                    .uri(kapiHost + "/v2/user/me")
                    .headers(headers -> headers.setBearerAuth(Objects.requireNonNull(accessToken)));

            String body = userProfileRequestSpec.retrieve().body(String.class);

            // body에 담겨 있는 json을 자바 객체로 가져오기 위해 역직렬화 진행
            KakaoUserResponse kakaoUserInfo = objectMapper.readValue(body, KakaoUserResponse.class);
            return kakaoUserInfo;

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public String createDefaultMessage() {
        return "template_object={\"object_type\":\"text\",\"text\":\"Hello, world!\",\"link\":{\"web_url\":\"https://developers.kakao.com\",\"mobile_web_url\":\"https://developers.kakao.com\"}}";
    }

    private HttpSession getSession() {
        ServletRequestAttributes attr = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        HttpSession session = attr.getRequest().getSession();
        return session;
    }

    private void saveAccessToken(String accessToken) {
        getSession().setAttribute("access_token", accessToken);
    }

    // 세션에서 어세스토큰 조회
    private String getAccessToken() {
        String accessToken = (String) getSession().getAttribute("access_token");
        return accessToken;
    }

    private void invalidateSession() {
        getSession().invalidate();
    }

    public String call(String method, String urlString, String body) {
        RestClient.RequestBodySpec requestSpec
                = restClient
                .method(HttpMethod.valueOf(method))
                .uri(urlString)
                .headers(headers -> headers.setBearerAuth(getAccessToken()));
        if (body != null) {
            requestSpec.contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(body);
        }
        try {
            String resultBody = requestSpec.retrieve()
                    .body(String.class);
            return resultBody;
        } catch (RestClientResponseException e) {
            // 에러 메시지 (응답 바디)
            String errorBody = e.getResponseBodyAsString();
            return errorBody;
        }
    }

    public boolean handleAuthorizationCallback(String code) {
        try {
            KakaoTokenResponse tokenResponse = getToken(code);

            log.info("tokenResponse::: {} ", tokenResponse);

            if (tokenResponse != null) {
                saveAccessToken(tokenResponse.getAccess_token());
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return false;
    }

    private KakaoTokenResponse getToken(String code) throws Exception {
        String params = String.format("grant_type=authorization_code&client_id=%s&client_secret=%s&code=%s",
                clientId, clientSecret, code);
        String response = call("POST", kauthHost + "/oauth/token", params);
        KakaoTokenResponse kakaoTokenResponse = objectMapper.readValue(response, KakaoTokenResponse.class);
        return kakaoTokenResponse;
    }

    public ResponseEntity<?> getUserProfile() {
        try {
            String response = call("GET", kapiHost + "/v2/user/me", null);
            ResponseEntity<Object> responseEntity = ResponseEntity.ok(objectMapper.readValue(response, Object.class));
            return responseEntity;
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }

    public ResponseEntity<?> getFriends() {
        try {
            String response = call("GET", kapiHost + "/v1/api/talk/friends", null);
            return ResponseEntity.ok(objectMapper.readValue(response, Object.class));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }

    public ResponseEntity<?> sendMessage(String messageRequest) {
        try {
            String response = call("POST", kapiHost + "/v2/api/talk/memo/default/send", messageRequest);
            return ResponseEntity.ok(objectMapper.readValue(response, Object.class));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }

    public ResponseEntity<?> sendMessageToFriend(String uuid, String messageRequest) {
        try {
            String response = call("POST",
                    kapiHost + "/v1/api/talk/friends/message/default/send?receiver_uuids=[" + uuid + "]",
                    messageRequest);
            return ResponseEntity.ok(objectMapper.readValue(response, Object.class));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }

    public ResponseEntity<?> logout() {
        try {
            String response = call("POST", kapiHost + "/v1/user/logout", null);
            invalidateSession();
            return ResponseEntity.ok(objectMapper.readValue(response, Object.class));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }

    public ResponseEntity<?> unlink() {
        try {
            String response = call("POST", kapiHost + "/v1/user/unlink", null);
            invalidateSession();
            return ResponseEntity.ok(objectMapper.readValue(response, Object.class));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }
}