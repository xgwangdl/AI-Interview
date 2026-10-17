package com.guangge.Interview.language.stream;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.guangge.Interview.auth.Sign;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/**
 * 握手时校验 token 并解析出账号。
 *
 * 浏览器无法给 WebSocket 请求加 Authorization 头（与 audio 标签同一个限制），
 * 所以 token 放在查询串里；这里验完把用户名塞进 session attributes，后续不再信任客户端。
 */
@Component
public class LanguageHandshakeInterceptor implements HandshakeInterceptor {

    static final String OWNER_ATTRIBUTE = "owner";

    private final String tokenSecret;

    public LanguageHandshakeInterceptor(@Value("${auth.token.secret}") String tokenSecret) {
        this.tokenSecret = tokenSecret;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = UriComponentsBuilder.fromUri(request.getURI()).build()
                .getQueryParams().getFirst("token");
        try {
            DecodedJWT jwt = Sign.verifyToken(token, tokenSecret);
            String userName = jwt.getClaim("userName").asString();
            if (userName == null || userName.isBlank()) throw new IllegalArgumentException("missing user");
            attributes.put(OWNER_ATTRIBUTE, userName);
            return true;
        } catch (Exception exception) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // 不需要额外动作
    }
}
