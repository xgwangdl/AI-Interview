package com.guangge.Interview.resume;

import com.guangge.Interview.resume.dto.ResumeOptimizationResult;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/** 走 Spring AI 的简历优化实现。 */
@Component
public class SpringAiResumeOptimizationProvider implements ResumeOptimizationProvider {

    private final ChatClient chatClient;
    private final ResumeOptimizationValidator validator;
    private final String modelName;

    public SpringAiResumeOptimizationProvider(
            ChatClient.Builder chatClientBuilder,
            ResumeOptimizationValidator validator,
            @Value("classpath:prompt/resume-optimization-system-prompt.st") Resource systemPrompt,
            @Value("${spring.ai.dashscope.chat.options.model:unknown}") String modelName) {
        // 注意 clone：ChatClient.Builder.defaultSystem 会修改自身并返回同一实例，
        // 直接复用注入的 builder 会把别的组件的系统提示词覆盖掉（踩过）。
        this.chatClient = chatClientBuilder.clone().defaultSystem(systemPrompt).build();
        this.validator = validator;
        this.modelName = modelName;
    }

    @Override
    public ResumeOptimizationResult optimize(String role, String jobDescription, String resumeText) {
        String raw = chatClient.prompt()
                .user("""
                        目标岗位：%s
                        岗位描述（JD）：
                        %s

                        简历原文：
                        %s
                        """.formatted(
                        role == null || role.isBlank() ? "（未提供）" : role,
                        jobDescription,
                        resumeText))
                .call()
                .content();
        return validator.parse(raw);
    }

    @Override
    public String modelName() {
        return modelName;
    }
}
