package com.guangge.Interview.practice.plan;

import com.guangge.Interview.practice.security.OwnerResolver;
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

    public QuestionPlanController(OwnerResolver owners, QuestionPlanService plans) {
        this.owners = owners;
        this.plans = plans;
    }

    @PostMapping("/question-plan")
    public QuestionPlanView plan(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Validated @RequestBody QuestionPlanRequest request) {
        owners.requireOwner(authorization);
        return plans.plan(request);
    }
}
