package com.guangge.Interview.assistant;

import com.guangge.Interview.advisor.LoggingAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.PromptChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

/**
 * 简历助手
 */
@Service
public class ResumeRewriterAssistant {

    private final ChatClient chatClient;
    private Resource userTextAdvisors;

    public ResumeRewriterAssistant(ChatClient.Builder modelBuilder, VectorStore vectorStore,
                                   @Value("classpath:prompt/resume-optimizer-system-prompt.st") Resource systemText,
                                   @Value("classpath:prompt/resume-optimizer-user-prompt.st") Resource userTextAdvisors) throws IOException {
        this.userTextAdvisors = userTextAdvisors;
        this.chatClient = modelBuilder.defaultSystem(s -> s.text(systemText))
                .defaultAdvisors(
                        QuestionAnswerAdvisor.builder(vectorStore)
                                .searchRequest(SearchRequest.builder().similarityThreshold(0.5).build())
                                .promptTemplate(new PromptTemplate(userTextAdvisors.getContentAsString(Charset.defaultCharset())))
                                .build(),
                        new LoggingAdvisor()
                )
                //.defaultToolNames("changeTestResult","getInterviewDetails")
                .build();
    }

    /**
     * 重写简历
     * @param jdText 需求JD
     * @param resumeText 简历
     * @param keyword 关键词
     * @return 简历
     */
    public String rewirter(String jdText, String resumeText,
                           String keyword) throws IOException {
        Map<String, Object> questionMap = new HashMap<>();
        questionMap.put("jd_text",jdText);
        questionMap.put("resume_text",resumeText);
        questionMap.put("keyword",keyword);
        String userContent = userTextAdvisors.getContentAsString(Charset.defaultCharset());
        return this.chatClient.prompt()
                .user(u -> u.text(userTextAdvisors).params(questionMap))
                .call().content();
    }
}
