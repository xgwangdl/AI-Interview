package com.guangge.Interview.language;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * 走 Spring AI 的口语陪练与复盘。
 *
 * 两个 ChatClient 必须各 clone 一份：`defaultSystem` 是"改自身并返回同一个 builder"（之前踩过），
 * 共用会互相覆盖提示词。
 *
 * 对话用**单独的更快模型**（默认 qwen-plus）并把回复 token 夹住：实测 qwen-max 一句话要 3 秒，
 * 而对话的体感主要由这一句决定。复盘那次调用允许更长（JSON + 3 条建议），单独给 500 token。
 */
@Component
public class SpringAiLanguageCoach implements LanguageCoach {

    private final ChatClient coach;
    private final ChatClient reporter;
    private final ChatClient talker;
    private final ChatClient critic;
    private final LanguageCoachValidator validator;
    private final String modelName;

    public SpringAiLanguageCoach(
            ChatClient.Builder builder,
            LanguageCoachValidator validator,
            @Value("classpath:prompt/language-coach-system-prompt.st") Resource coachPrompt,
            @Value("classpath:prompt/language-report-system-prompt.st") Resource reportPrompt,
            @Value("classpath:prompt/language-talk-system-prompt.st") Resource talkPrompt,
            @Value("classpath:prompt/language-feedback-system-prompt.st") Resource feedbackPrompt,
            @Value("${practice.language.chat-model:qwen-plus}") String chatModel,
            @Value("${practice.language.max-reply-tokens:160}") int maxReplyTokens) {
        ChatOptions fast = ChatOptions.builder().model(chatModel).maxTokens(maxReplyTokens).temperature(0.6).build();
        ChatOptions report = ChatOptions.builder().model(chatModel).maxTokens(600).temperature(0.4).build();
        this.coach = builder.clone().defaultOptions(fast).defaultSystem(coachPrompt).build();
        this.reporter = builder.clone().defaultOptions(report).defaultSystem(reportPrompt).build();
        // L2：纯文本流式回复（JSON 没法边生成边显示）；反馈单独一次短调用，不挡回复。
        this.talker = builder.clone().defaultOptions(fast).defaultSystem(talkPrompt).build();
        this.critic = builder.clone().defaultOptions(fast).defaultSystem(feedbackPrompt).build();
        this.validator = validator;
        this.modelName = chatModel;
    }

    @Override
    public LanguageCoachResult reply(LanguageCoachRequest request) {
        String raw = coach.prompt().user(turnMessage(request)).call().content();
        return validator.parseTurn(raw, request.transcript());
    }

    @Override
    public String streamReply(LanguageCoachRequest request, java.util.function.Consumer<String> onDelta) {
        StringBuilder reply = new StringBuilder();
        talker.prompt().user(turnMessage(request)).stream().content()
                .doOnNext(delta -> {
                    reply.append(delta);
                    onDelta.accept(delta);
                })
                .blockLast();
        return reply.toString().strip();
    }

    @Override
    public java.util.List<LanguageFeedback> feedback(LanguageCoachRequest request) {
        String raw = critic.prompt().user(turnMessage(request)).call().content();
        return validator.parseFeedback(raw, request.transcript());
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
                语言：%s
                场景：%s
                %s
                目标：%s
                之前的对话：
                %s
                对方这一轮说（转写，可能有错别字）：
                %s
                """.formatted(
                request.language().displayName(),
                request.scenario().title(),
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
