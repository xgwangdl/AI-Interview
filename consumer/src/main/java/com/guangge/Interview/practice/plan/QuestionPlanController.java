package com.guangge.Interview.practice.plan;

import com.guangge.Interview.practice.security.OwnerResolver;
import com.guangge.Interview.quota.ModelCallGuard;
import org.springframework.http.HttpHeaders;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 在创建练习之前先按岗位与 JD 生成题目。 */
@RestController
@RequestMapping("/api/v1/practice-sessions")
public class QuestionPlanController {

    private final OwnerResolver owners;
    private final QuestionPlanService plans;
    private final ModelCallGuard modelCalls;

    public QuestionPlanController(OwnerResolver owners, QuestionPlanService plans, ModelCallGuard modelCalls) {
        this.owners = owners;
        this.plans = plans;
        this.modelCalls = modelCalls;
    }

    @PostMapping("/question-plan")
    public QuestionPlanView plan(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Validated @RequestBody QuestionPlanRequest request) {
        String owner = owners.requireOwner(authorization);
        // 出题要花模型的钱：先占额度（拿不到就是 429），再调用
        return modelCalls.call(owner, "面试出题", () -> plans.plan(request));
    }
}
