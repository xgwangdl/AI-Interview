package com.guangge.Interview.test;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.guangge.Interview.services.ResumeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.util.function.Function;

@Configuration
public class WrittenTestTools {
    @Autowired
    private ResumeService resumeService;

    public record InterViewRequest(@JsonPropertyDescription("序号") String number, @JsonPropertyDescription("姓名") String name,
                                   @JsonPropertyDescription("分数") int score, @JsonPropertyDescription("评语") String evaluate){}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record InterViewRecord(@JsonPropertyDescription("序号") String number, @JsonPropertyDescription("姓名") String name,
                                  @JsonPropertyDescription("分数") int score, @JsonPropertyDescription("状态") String interViewStatus,
                                  @JsonPropertyDescription("评语") String evaluate,@JsonPropertyDescription("邮箱") String email,
                                  @JsonPropertyDescription("音频路径") String mp3path) {}

    @Bean
    @Description("更改笔试结果")
    public Function<WrittenTestTools.InterViewRequest, String> changeTestResult() {
        return request -> {
            resumeService.changeTestReuslt(request.name(), request.score(), request.evaluate());
            return "";
        };
    }

    @Bean
    @Description("获取笔试结果")
    public Function<WrittenTestTools.InterViewRequest, WrittenTestTools.InterViewRecord> getInterviewDetails() {
        return request -> {
            try {
                return resumeService.getInterViewDetails(request.name());
            }
            catch (Exception e) {
                return null;
            }
        };
    }

    @Bean
    @Description("根据姓名获取简历")
    public Function<WrittenTestTools.InterViewRequest, String> getResumeByName() {
        return request -> {
            try {
                String resume = resumeService.findInterView(request.name()).getRawText();
                if (resume.isEmpty()) {
                    return "没有发现这个人的简历，请让他联系工作人员已确认是否上传了简历。";
                }
                return resume;
            } catch (IllegalArgumentException ex) {
                return "没有发现这个人，请确认真的被邀请面试了吗。";
            }
        };
    }

    @Bean
    @Description("根据序号获取简历")
    public Function<WrittenTestTools.InterViewRequest, String> getResumeById() {
        return request -> {
            try {
                String resume = resumeService.findInterView(Long.valueOf(request.number())).getRawText();
                if (resume.isEmpty()) {
                    return "没有发现这个人的简历，请确认他真实姓名。";
                }
                return resume;
            } catch (IllegalArgumentException ex) {
                return "没有发现这个人，请确认真的被邀请面试了吗。";
            }
        };
    }

    @Bean
    @Description("更改面试结果")
    public Function<WrittenTestTools.InterViewRequest, String> changeInterView() {
        return request -> {
            resumeService.changeInterview(request.name(),request.evaluate());
            return "";
        };
    }
}
