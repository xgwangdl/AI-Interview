package com.guangge.Interview.auth;


import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.guangge.Interview.exception.RestException;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class Sign {

    /** 调用方没给有效期时的兜底值：1 小时，够跑完一轮练习。 */
    private static final long DEFAULT_TTL_MILLIS = java.util.concurrent.TimeUnit.HOURS.toMillis(1);

    private static Map<String, JWTVerifier> verifierMap = new HashMap<>();
    private static Map<String, Algorithm> algorithmMap = new HashMap<>();

    private static Algorithm getAlgorithm(String secret) {
        Algorithm algorithm = algorithmMap.get(secret);
        if (algorithm == null) {
            synchronized (algorithmMap) {
                algorithm = algorithmMap.get(secret);
                if (algorithm == null) {
                    algorithm = Algorithm.HMAC256(secret);
                    algorithmMap.put(secret, algorithm);
                }
            }
        }
        return algorithm;
    }

    public static String getToken(String userName, String secret) {
        return getToken(userName, secret, DEFAULT_TTL_MILLIS);
    }

    /**
     * 签发令牌。
     *
     * ttlMillis 必须由调用方给出：原来这里**硬编码 10 分钟**，而一次模拟面试要 20 分钟左右，
     * 结果是用户录到一半令牌就过期、所有请求开始 401。
     */
    public static String getToken(String userName, String secret, long ttlMillis) {
        if (StringUtils.isEmpty(secret)) {
            throw new RestException("00X", "No signing token!");
        }
        Algorithm algorithm = getAlgorithm(secret);
        long ttl = ttlMillis > 0 ? ttlMillis : DEFAULT_TTL_MILLIS;
        String token = JWT.create().withClaim("userName", userName)
                .withExpiresAt(new Date(System.currentTimeMillis() + ttl))
                .sign(algorithm);
        return token;
    }

    public static DecodedJWT verifyToken(String token,String secret) {
        JWTVerifier verifier = verifierMap.get(token);
        if (verifier == null) {
            synchronized (verifierMap) {
                verifier = verifierMap.get(token);
                if (verifier == null) {
                    Algorithm algorithm = getAlgorithm(secret);
                    verifier = JWT.require(algorithm).build();
                    verifierMap.put(token, verifier);
                }
            }
        }
        DecodedJWT decodedJWT = verifier.verify(token);
        if (decodedJWT == null) {
            throw new RestException("00Z", "Token is error!");
        }
        return decodedJWT;
    }
}
