package com.guangge.Interview.quota;

import com.guangge.Interview.practice.security.OwnerResolver;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前账号今天还能调多少次模型。
 *
 * 放在 /me 下面（和主题色偏好同一个前缀）：它是"我这个账号的状态"，
 * 前端在顶栏显示一行"今日 AI 额度 剩余 x/y"就够，不需要单独一页。
 */
@RestController
@RequestMapping("/api/v1/me")
public class ModelUsageController {

    private final OwnerResolver owners;
    private final ModelCallGuard guard;

    public ModelUsageController(OwnerResolver owners, ModelCallGuard guard) {
        this.owners = owners;
        this.guard = guard;
    }

    @GetMapping("/model-usage")
    public ModelUsageView usage(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return guard.usage(owners.requireOwner(authorization));
    }
}
