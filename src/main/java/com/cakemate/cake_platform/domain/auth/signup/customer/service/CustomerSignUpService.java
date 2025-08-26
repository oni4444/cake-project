package com.cakemate.cake_platform.domain.auth.signup.customer.service;

import com.cakemate.cake_platform.common.command.SearchCommand;
import com.cakemate.cake_platform.common.dto.ApiResponse;

import com.cakemate.cake_platform.common.exception.MemberNotFoundException;
import com.cakemate.cake_platform.common.jwt.util.JwtUtil;
import com.cakemate.cake_platform.domain.auth.entity.Owner;
import com.cakemate.cake_platform.domain.auth.exception.CustomerNotFoundException;
import com.cakemate.cake_platform.domain.auth.exception.OAuthAccountAlreadyBoundException;
import com.cakemate.cake_platform.domain.auth.exception.EmailAlreadyExistsException;
import com.cakemate.cake_platform.domain.auth.OauthKakao.response.KakaoTokenResponse;
import com.cakemate.cake_platform.domain.auth.OauthKakao.response.KakaoUserResponse;
import com.cakemate.cake_platform.domain.auth.oAuthEnum.OAuthProvider;
import com.cakemate.cake_platform.domain.auth.signin.customer.dto.response.CustomerSignInResponse;
import com.cakemate.cake_platform.domain.auth.signup.customer.dto.response.CustomerSignUpResponse;
import com.cakemate.cake_platform.domain.auth.entity.Customer;
import com.cakemate.cake_platform.domain.auth.signup.customer.repository.CustomerRepository;
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

import java.util.UUID;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Transactional
@Service
public class CustomerSignUpService {
    private final CustomerRepository customerRepository;
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

    public CustomerSignUpService(CustomerRepository customerRepository, PasswordEncoder passwordEncoder,
                                 MemberRepository memberRepository, JwtUtil jwtUtil, ObjectMapper objectMapper,
                                 RestClient.Builder restClientBuilder) {
        this.customerRepository = customerRepository;
        this.passwordEncoder = passwordEncoder;
        this.memberRepository = memberRepository;
        this.jwtUtil = jwtUtil;
        this.objectMapper = objectMapper;
        this.restClient = restClientBuilder.baseUrl(kapiHost).build();
    }

    public ApiResponse<?> customerLocalSignUpProcess(SearchCommand customerSignUpRequest) {
        String email = customerSignUpRequest.getEmail();
        String password = customerSignUpRequest.getPassword();
        String passwordConfirm = customerSignUpRequest.getPasswordConfirm();
        String name = customerSignUpRequest.getName();
        String phoneNumber = customerSignUpRequest.getPhoneNumber();
        // 비밀번호 정규식 검증
        customerSignUpRequest.hasPasswordPattern();
        // 비밀번호, 비밀빈호 확인이 일치하는지
        customerSignUpRequest.isMatchedPassword();

        boolean customerEmail = existsCustomerEmail(email);
        if (customerEmail) {
            throw new EmailAlreadyExistsException();
        }

        String passwordEncode = passwordEncoder.encode(password);
        String passwordConfirmEncode = passwordEncoder.encode(passwordConfirm);

        boolean existsCustomer = existsCustomer(name, phoneNumber);
        Customer customerByLocal;

        if (existsCustomer) {
            Optional<Customer> customerByNameAndPhoneNumber =
                    customerRepository.findByNameAndPhoneNumber(name, phoneNumber);
            if (customerByNameAndPhoneNumber.isPresent()) {

                Member customerByProvider = findCustomerByProvider(name, phoneNumber, OAuthProvider.KAKAO);

                String customerJwtToken = jwtUtil.createMemberJwtToken(customerByProvider);
                CustomerSignInResponse customerSignInResponse = new CustomerSignInResponse(customerJwtToken);

                ApiResponse<CustomerSignInResponse> SignInSuccess
                        = ApiResponse
                        .success(HttpStatus.OK, "환영합니다 " +
                                customerByProvider.getCustomer().getName() + "님", customerSignInResponse);
                return SignInSuccess;
            }
        } 


        Customer customerInfo = new Customer(UUID.randomUUID().toString(), email, passwordEncode,
                passwordConfirmEncode, name, phoneNumber, OAuthProvider.LOCAL, null);
        customerByLocal = customerRepository.save(customerInfo);
        // Member 테이블에 저장

        Member custmerMember = new Member(customerInfo);
        memberRepository.save(custmerMember);

        CustomerSignUpResponse customerSignUpResponse = new CustomerSignUpResponse(customerByLocal);

        ApiResponse<CustomerSignUpResponse> signUpSuccess
                = ApiResponse
                .success(HttpStatus.CREATED,
                        customerByLocal.getName() + "님 회원가입이 완료되었습니다.",
                        customerSignUpResponse);

        return signUpSuccess;
    }


    private Customer findCustomer(String name, String phoneNumber) {
        Customer customer = customerRepository
                .findByNameAndPhoneNumber(name, phoneNumber)
                .orElseThrow(() -> new CustomerNotFoundException());
        return customer;
    }

    private boolean existsCustomerEmail(String email) {
        return customerRepository.existsByEmail(email);
    }

    private boolean existsCustomer(String name, String phoneNumber) {
        return customerRepository.existsByNameAndPhoneNumber(name, phoneNumber);
    }

    private Member findCustomerByKaKaoInMember(String name, String phoneNumber, OAuthProvider provider, Long id) {
        return memberRepository.findByCustomerByKakao(name, phoneNumber,
                provider, id).orElseThrow(() -> new MemberNotFoundException("해당 회원을 찾을 수 없습니다."));
    }

    private Member findCustomerByProvider(String name, String phoneNumber, OAuthProvider provider) {
        return memberRepository.findCustomerByProvider(name, phoneNumber, provider)
                .orElseThrow(() -> new MemberNotFoundException("해당 회원을 찾을 수 없습니다."));
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
