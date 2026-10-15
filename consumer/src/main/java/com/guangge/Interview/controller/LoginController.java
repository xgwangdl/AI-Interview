package com.guangge.Interview.controller;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.guangge.Interview.auth.Sessions;
import com.guangge.Interview.auth.Sign;
import com.guangge.Interview.data.Candidates;
import com.guangge.Interview.services.CandidatesService;
import com.guangge.Interview.security.LoginAttemptGuard;
import com.guangge.Interview.util.CommonResult;
import com.guangge.Interview.vo.UserResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class LoginController {
    private static final Logger logger = LoggerFactory.getLogger(LoginController.class);

    private final CandidatesService candidatesService;
    private final LoginAttemptGuard loginAttempts;

    @Value("${auth.token.secret}")
    private String secret;

    /** 令牌有效期（小时）。默认 12 小时：一轮练习 20 分钟左右，原来的 10 分钟会在中途过期。 */
    @Value("${auth.token.ttl-hours:12}")
    private long tokenTtlHours;

    public LoginController(CandidatesService interviewerService, LoginAttemptGuard loginAttempts) {
        this.candidatesService = interviewerService;
        this.loginAttempts = loginAttempts;
    }

    /**
     * 登录
     * @param name 登录名
     * @param code 邀请code
     * @return 登录信息
     * @throws Exception
     */
    @PostMapping(value = "/login")
    public CommonResult<UserResponse> login(@RequestParam("name") String name,
                                            @RequestParam("code") String code) throws Exception {
        String attemptKey = this.loginAttempts.keyOf(name);
        if (this.loginAttempts.isLocked(attemptKey)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "尝试次数过多，请稍后再试");
        }
        Candidates candidates;
        try {
            candidates = this.candidatesService.login(name, code);
        } catch (RuntimeException exception) {
            this.loginAttempts.recordFailure(attemptKey);
            throw exception;
        }
        this.loginAttempts.clear(attemptKey);
        String token = Sessions.loginUser(candidates.getName(),
                java.util.concurrent.TimeUnit.HOURS.toMillis(Math.max(1, tokenTtlHours)),
                secret);
        UserResponse userResponse = new UserResponse();
        userResponse.setUserId(candidates.getId());
        userResponse.setUserName(candidates.getName());
        userResponse.setToken(token);
        return CommonResult.success(userResponse);
    }

    /**
     * JWT认证
     * @param token token
     * @return 认证信息
     */
    @PostMapping(value = "/auth/verify-token")
    public CommonResult<String> verifyToken(@RequestHeader("token") String token) {
        DecodedJWT decodedJWT = null;
        try {
            decodedJWT = Sign.verifyToken(token, secret);
        } catch (Exception ex) {
            logger.error(ex.getMessage());
            return CommonResult.unauthorized("Token验证失败");
        }
        String username = decodedJWT.getClaim("userName").asString();
        return CommonResult.success(username);
    }
}
