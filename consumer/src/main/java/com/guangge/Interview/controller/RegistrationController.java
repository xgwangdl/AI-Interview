package com.guangge.Interview.controller;

import com.guangge.Interview.auth.Sessions;
import com.guangge.Interview.data.Candidates;
import com.guangge.Interview.services.CandidatesService;
import com.guangge.Interview.util.CommonResult;
import com.guangge.Interview.vo.UserResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 开放注册。
 *
 * 之前账号只能手工 INSERT 到 candidates 表，除了内置的冒烟用户没人能进来。
 * 这里让用户自己设用户名和口令（口令按 BCrypt 存哈希），注册成功直接签发 token，
 * 前端可以立即进入工作台，不用再登录一次。
 */
@RestController
public class RegistrationController {

    private final CandidatesService candidatesService;

    @Value("${auth.token.secret}")
    private String secret;

    public RegistrationController(CandidatesService candidatesService) {
        this.candidatesService = candidatesService;
    }

    @PostMapping(value = "/register")
    public CommonResult<UserResponse> register(@RequestParam("name") String name,
                                               @RequestParam("code") String code,
                                               @RequestParam(value = "email", required = false) String email) {
        Candidates candidate = candidatesService.register(name, code, email);
        String token = Sessions.loginUser(candidate.getName(), true, secret);
        UserResponse userResponse = new UserResponse();
        userResponse.setUserId(candidate.getId());
        userResponse.setUserName(candidate.getName());
        userResponse.setToken(token);
        return CommonResult.success(userResponse);
    }
}
