package com.guangge.Interview.controller;

import com.guangge.Interview.comsumer.client.ConsumerClient;
import com.guangge.Interview.exception.RestException;
import com.guangge.Interview.util.CommonResult;
import com.guangge.Interview.util.JacksonMapperUtils;
import com.guangge.Interview.util.ResultCode;
import com.guangge.Interview.vo.RegisterFaceRequest;
import com.guangge.Interview.vo.UserResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@RestController
public class LoginController {

    private final ConsumerClient consumerClient;

    @Value("${flaskBaseUrl}")
    private String flaskBaseUrl;

    public LoginController(ConsumerClient consumerClient) {
        this.consumerClient = consumerClient;
    }

    @PostMapping(value = "/login")
    public CommonResult<UserResponse> login(@RequestParam("name") String name,
                                            @RequestParam("code") String code) {
        return consumerClient.login(name,code);
    }

    @PostMapping(value = "/auth/verify-token")
    public CommonResult<String> verifyToken(@RequestHeader("token") String token) {
        return consumerClient.verifyToken(token);
    }

    @PostMapping("/LoginAdmin")
    public CommonResult<UserResponse> verifyFace(@RequestBody RegisterFaceRequest request) throws Exception {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String requestBody = "{\"image\": \"" + request.getImage() + "\"}";
        HttpEntity<String> requestEntity = new HttpEntity<>(requestBody, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(flaskBaseUrl + "/verify-face", requestEntity, String.class);
        Map<String, Object> stringObjectMap = JacksonMapperUtils.json2map(response.getBody());
        if ("0".equals((String)stringObjectMap.get("status"))) {
            String userId = (String)stringObjectMap.get("userid");
            return consumerClient.loginAdmin(userId);
        } else {
            throw new RestException(String.valueOf(ResultCode.FORBIDDEN.getCode()),"管理员登录失败");
        }
    }
}
