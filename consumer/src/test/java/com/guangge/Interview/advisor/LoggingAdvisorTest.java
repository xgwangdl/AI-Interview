package com.guangge.Interview.advisor;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LoggingAdvisorTest {

    @Test
    void neverWritesPromptOrResponseContentToTheLog() {
        Logger logger = (Logger) LoggerFactory.getLogger(LoggingAdvisor.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            String email = "candidate@example.com";
            String token = "sk-supersecret123";
            ChatClientRequest request = new ChatClientRequest(
                    new Prompt(List.of(new UserMessage("我的邮箱 " + email + "，令牌 Bearer " + token))), Map.of());
            ChatClientResponse response = new ChatClientResponse(
                    new ChatResponse(List.of(new Generation(new AssistantMessage("我的建议是 " + email)))),
                    Map.of());
            CallAdvisorChain chain = mock(CallAdvisorChain.class);
            when(chain.nextCall(request)).thenReturn(response);

            new LoggingAdvisor().adviseCall(request, chain);

            String logged = appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .collect(Collectors.joining("\n"));
            assertFalse(logged.contains(email), logged);
            assertFalse(logged.contains(token), logged);
            assertFalse(logged.contains("我的邮箱"), logged);
            assertFalse(logged.contains("我的建议"), logged);
            assertTrue(logged.contains("chat request messages=1"), logged);
            assertTrue(logged.contains("chars="), logged);
            assertTrue(logged.contains("resultChars="), logged);
        } finally {
            logger.detachAppender(appender);
        }
    }
}
