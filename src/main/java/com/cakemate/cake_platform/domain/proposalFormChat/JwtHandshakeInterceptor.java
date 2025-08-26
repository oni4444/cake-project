package com.cakemate.cake_platform.domain.proposalFormChat;

import com.cakemate.cake_platform.common.jwt.util.JwtUtil;
import com.cakemate.cake_platform.domain.proposalFormChat.dto.ChatMessageRequestDto;
import com.cakemate.cake_platform.domain.proposalFormChat.service.ChatService;
import com.cakemate.cake_platform.domain.store.owner.exception.ForbiddenException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.support.InterceptingHttpAccessor;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Component
@Slf4j
public class JwtHandshakeInterceptor implements HandshakeInterceptor {
    // UUID v4 정규식 패턴
    private static final Pattern UUID_V4 = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$"
    );
    private final JwtUtil jwtUtil;
    private final ChatService chatService;


    public JwtHandshakeInterceptor(JwtUtil jwtUtil, ChatService chatService) {
        this.jwtUtil = jwtUtil;
        this.chatService = chatService;
    }


    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes
    ) throws Exception {
        //Authorization 헤더에서 Bearer 토큰 꺼내기
        String authorizationHeader = request.getHeaders().getFirst("Authorization");

        try {
            if (authorizationHeader == null || authorizationHeader.isEmpty()) {

                return unauthorizedError(response, "인증 토큰이 없습니다.");
            }

            if (authorizationHeader == null || !authorizationHeader.regionMatches(
                    true, 0, "Bearer ", 0, 7
            )) {
                return unauthorizedError(response, "Bearer 토큰이 아닙니다.");
            }

            // "Bearer " 제거
            String token = jwtUtil.substringToken(authorizationHeader);

            // 토큰 검증 및 Claims 추출
            Claims claims = jwtUtil.verifyToken(token);

            // memberId 추출
            Long memberId = jwtUtil.subjectMemberId(claims);

            //토큰에 저장된 memberName 추출
            String memberName = jwtUtil.extractMemberName(claims);

            //토큰 만료시간 추출
            Date expiration = claims.getExpiration();

            if (memberId == null || memberName == null || expiration == null) {
                return unauthorizedError(response, "토큰 클레임이 유효하지 않습니다.");
            }

            long expirationTime = expiration.getTime();

            if (expirationTime < System.currentTimeMillis()) {
                return unauthorizedError(response, "토큰이 만료되었습니다.");
            }

            boolean customerToken = jwtUtil.isCustomerToken(claims);

            boolean ownerToken = jwtUtil.isOwnerToken(claims);

            if (!customerToken && !ownerToken) {
                return unauthorizedError(response, "유효한 접근이 아닙니다.");
            }

            String roomId = request.getHeaders().getFirst("roomId");
            if (roomId == null || roomId.isEmpty()) {
                return forbiddenError(response, "roomId 헤더가 없습니다.");
            }
            if (!UUID_V4.matcher(roomId).matches()) {
                return forbiddenError(response, "roomId 형식 오류");
            }

            if (!chatService.canAccessRoom(memberId, roomId)) {
                return forbiddenError(response, "해당 채팅방에 접근 권한이 없습니다.");
            }

            if (ownerToken) {
                attributes.put("ownerId", memberId);
            } else {
                attributes.put("customerId", memberId);
            }

            // 공통 식별자
            attributes.put("memberId", memberId);

            //토큰 만료시간 저장
            attributes.put("expirationTime", expirationTime);

            //이름 저장
            attributes.put("memberName", memberName);


            //그 토큰이 점주용 토큰인지(ownerToken), 소비자용 토큰인지(customerToken)를 판별 후
            //판별 결과를 문자열 "OWNER" 또는 "CUSTOMER"로 변환해서
            //session.getAttributes()에 memberType 으로 저장.
            attributes.put("memberType", ownerToken ? "OWNER" : "CUSTOMER");

            attributes.put("roomId", roomId);

            return true;

        } catch (JwtException | IllegalArgumentException e) {
            return unauthorizedError(response, "토큰 검증 실패");
        }
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception
    ) {

    }


    // 인증 실패(Authorization 헤더 없음, 토큰 만료, 위조 등)
    private boolean unauthorizedError(ServerHttpResponse response, String reason) {
        response.setStatusCode(HttpStatus.UNAUTHORIZED); // 401
        log.warn("WebSocket handshake unauthorized: {}", reason);
        return false;// beforeHandshake 의 return false → 연결 차단
    }

    // 권한 없음(토큰은 정상인데, 해당 리소스 접근 권한이 없는 경우)
    private boolean forbiddenError(ServerHttpResponse response, String reason) {
        response.setStatusCode(HttpStatus.FORBIDDEN); // 403
        log.warn("WebSocket handshake forbidden: {}", reason);
        return false;// beforeHandshake 의 return false → 연결 차단
    }
}
