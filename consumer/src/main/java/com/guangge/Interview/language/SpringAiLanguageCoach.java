package com.guangge.Interview.language;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * 走 Spring AI 的口语陪练与复盘。
 *
 * 两个 ChatClient 必须各 clone 一份：`defaultSystem` 是"改自身并返回同一个 builder"（之前踩过），
 * 共用会互相覆盖提示词。
 */
@Component
public class SpringAiLanguageCoach implements LanguageCoach {

    private final ChatClient coach;
    private final ChatClient reporter;
    private final LanguageCoachValidator validator;
    private final String modelName;

    public SpringAiLanguageCoach(
            ChatClient.Builder builder,
            LanguageCoachValidator validator,
            @Value("classpath:prompt/language-coach-system-prompt.st") Resource coachPrompt,
            @Value("classpath:prompt/language-report-system-prompt.st") Resource reportPrompt,
            @Value("${spring.ai.dashscope.chat.options.model:unknown}") String modelName) {
        this.coach = builder.clone().defaultSystem(coachPrompt).build();
        this.reporter = builder.clone().defaultSystem(reportPrompt).build();
        this.validator = validator;
        this.modelName = modelName;
    }

    @Override
    public LanguageCoachResult reply(LanguageCoachRequest request) {
        String raw = coach.prompt().user(turnMessage(request)).call().content();
        return validator.parseTurn(raw, request.transcript());
    }

    @Override
    public LanguageReportResult report(LanguageReportRequest request) {
        String raw = reporter.prompt().user(reportMessage(request)).call().content();
        return validator.parseReport(raw);
    }

    @Override
    public String modelName() {
        return modelName;
    }

    private static String turnMessage(LanguageCoachRequest request) {
        StringBuilder history = new StringBuilder();
        for (LanguageCoachRequest.Exchange exchange : request.history()) {
            history.append("对方：").append(nullToDash(exchange.user())).append('\n');
            history.append("你：").append(nullToDash(exchange.assistant())).append('\n');
        }
        return """
                目标语言：%s
                场景：%s（%s）
                %s
                本轮练习目标：%s

                之前的对话：
                %s
                对方这一轮说的（语音转写，可能有错别字）：
                %s
                """.formatted(
                request.language().displayName(),
                request.scenario().title(), request.scenario().chineseHint(),
                LanguageCatalog.levelInstruction(request.level()),
                request.target(),
                history.isEmpty() ? "（这是第一轮）" : history.toString().strip(),
                request.transcript());
    }

    private static String reportMessage(LanguageReportRequest request) {
        StringBuilder turns = new StringBuilder();
        for (LanguageReportRequest.Turn turn : request.turns()) {
            turns.append(turn.index()).append(". 对方：").append(nullToDash(turn.transcript())).append('\n');
            turns.append("   你：").append(nullToDash(turn.reply())).append('\n');
        }
        return """
                目标语言：%s
                场景：%s（%s）
                %s
                本轮目标：%s

                全部轮次：
                %s
                """.formatted(
                request.language().displayName(), request.scenario().title(), request.scenario().chineseHint(),
                LanguageCatalog.levelInstruction(request.level()), request.target(), turns.toString().strip());
    }

    private static String nullToDash(String value) {
        return value == null || value.isBlank() ? "（没有内容）" : value.strip();
    }
}
