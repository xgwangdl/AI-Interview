package com.guangge.Interview.resume;

import com.guangge.Interview.resume.dto.ResumeOptimizationResult;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** 走 Spring AI 的简历优化实现。 */
@Component
public class SpringAiResumeOptimizationProvider implements ResumeOptimizationProvider {

    private final ChatClient chatClient;
    private final ResumeOptimizationValidator validator;
    private final String modelName;

    public SpringAiResumeOptimizationProvider(
            ChatClient.Builder chatClientBuilder,
            ResumeOptimizationValidator validator,
            @Value("classpath:resume/spring-ai-system-prompt.md") Resource systemPrompt,
            @Value("classpath:resume/resume-data.schema.json") Resource resumeSchema,
            @Value("classpath:resume/content-integrity.md") Resource integrityRules,
            @Value("classpath:resume/locale-guide.md") Resource localeGuide,
            @Value("classpath:prompt/resume-optimization-output-contract.st") Resource outputContract,
            @Value("${spring.ai.dashscope.chat.options.model:unknown}") String modelName) {
        // 注意 clone：ChatClient.Builder.defaultSystem 会修改自身并返回同一实例，
        // 直接复用注入的 builder 会把别的组件的系统提示词覆盖掉（踩过）。
        this.chatClient = chatClientBuilder.clone()
                .defaultSystem(composePrompt(
                        systemPrompt, integrityRules, localeGuide, resumeSchema, outputContract))
                .build();
        this.validator = validator;
        this.modelName = modelName;
    }

    @Override
    public ResumeOptimizationResult optimize(
            String role, String jobDescription, String resumeText, String language, String documentType) {
        String targetLanguage = language == null || language.isBlank() ? "zh" : language.strip();
        String targetDocumentType = documentType == null || documentType.isBlank()
                ? ("ja".equals(targetLanguage) ? "career_history" : "resume")
                : documentType.strip();
        String raw = chatClient.prompt()
                .user("""
                        目标岗位：%s
                        输出语言：%s
                        文档类型：%s
                        岗位描述（JD）：
                        %s

                        简历原文：
                        %s
                        """.formatted(
                        role == null || role.isBlank() ? "（未提供）" : role,
                        targetLanguage,
                        targetDocumentType,
                        jobDescription,
                        resumeText))
                .call()
                .content();
        return validator.parse(raw, resumeText);
    }

    @Override
    public String modelName() {
        return modelName;
    }

    private static String composePrompt(
            Resource systemPrompt,
            Resource integrityRules,
            Resource localeGuide,
            Resource resumeSchema,
            Resource outputContract) {
        return String.join("\n\n---\n\n",
                read(systemPrompt),
                read(integrityRules),
                read(localeGuide),
                "resume-data.schema.json:\n" + read(resumeSchema),
                read(outputContract));
    }

    private static String read(Resource resource) {
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("无法加载简历 Skill 资源：" + resource.getDescription(), exception);
        }
    }
}
