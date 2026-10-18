package com.guangge.Interview.controller;

import com.guangge.Interview.assistant.InterviewAssistant;
import com.guangge.Interview.assistant.JavaAssistant;
import com.guangge.Interview.assistant.ProgramAssistant;
import com.guangge.Interview.audio.services.AudioConverter;
import com.guangge.Interview.audio.services.SpeechToTextService;
import com.guangge.Interview.audio.services.TextToSpeechService;
import com.guangge.Interview.record.ProgramRecord;
import com.guangge.Interview.util.JacksonMapperUtils;
import com.guangge.Interview.vo.ChatReqeust;
import com.guangge.Interview.vo.CheckProgramRequest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

@RestController
@RequestMapping("interview")
public class InterViewController {

    private final JavaAssistant interViewAgent;
    private final InterviewAssistant interviewAssistant;
    private final TextToSpeechService speechService;
    private final SpeechToTextService speechToTextService;
    private final AudioConverter audioConverter;
    private final ResourceLoader resourceLoader;
    private final ProgramAssistant programAssistant;

    public InterViewController(JavaAssistant interViewAgent, InterviewAssistant interviewAssistant,
                               TextToSpeechService speechService, SpeechToTextService speechToTextService,
                               AudioConverter audioConverter, ResourceLoader resourceLoader,
                               ProgramAssistant programAssistant) {
        this.interViewAgent = interViewAgent;
        this.interviewAssistant = interviewAssistant;
        this.speechService = speechService;
        this.speechToTextService = speechToTextService;
        this.audioConverter = audioConverter;
        this.resourceLoader = resourceLoader;
        this.programAssistant = programAssistant;
    }


    /**
     * 笔试题面试接口
     * @param chatReqeust 回话内容
     * @return 面试官回答内容
     */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(@RequestBody ChatReqeust chatReqeust)  {
        return interViewAgent.chat(chatReqeust.getChatId(), chatReqeust.getUserMessage());
    }

    /**
     * 面试接口
     * @param chatId 回话id
     * @param audio 面试者回答音频
     * @return 面试官问题音频
     * @throws IOException
     * @throws InterruptedException
     * @throws UnsupportedAudioFileException
     */
    @PostMapping(value="/face2faceChat", produces = "audio/wav")
    public ResponseEntity<byte[]> face2faceChat(@RequestParam("chatId") String chatId,@RequestParam(value ="userName", required = false) String userName,
                                                @RequestPart(value = "audio", required = false) MultipartFile audio) throws IOException, InterruptedException, UnsupportedAudioFileException {
        String completed = "";
        String text = "";
        if (StringUtils.hasLength(userName)) {
            text = userName;
        } else {
            File tempFile = File.createTempFile("audio-", ".opus");
            audio.transferTo(tempFile);
            File convertFile = audioConverter.convertToWav(tempFile);
            // 语音转文字
            text = speechToTextService.transcribeAudio(convertFile);
            tempFile.delete();
            convertFile.delete();
        }

        // 获取大模型响应
        String response = interviewAssistant.chat(chatId,text);

        if (response.contains("再见")) {
            completed = "completed";
        }

        // 文字转语音
        byte[] audioResponse = speechService.textToSpeech(response);

        return ResponseEntity.ok()
                .header("Content-Type", "audio/wav")
                .header("X-Chat-Status", completed) // 状态信息
                .body(audioResponse);
    }

    /**
     * 欢迎音频取得
     * @return 欢迎音频
     * @throws IOException
     */
    @GetMapping(value="/welcomemp3", produces = "audio/mp3")
    public ResponseEntity<byte[]> welcomemp3() throws IOException {
        Resource resource = new ClassPathResource("static/audio/welcome.mp3");
        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }

        byte[] audioBytes = Files.readAllBytes(resource.getFile().toPath());
        return ResponseEntity.ok()
                .header("Content-Type", "audio/mp3")
                .body(audioBytes);
    }

    @GetMapping(value = "/makeProgram")
    public ResponseEntity<ProgramRecord> program(@RequestParam("first") Boolean first,
                                                 @RequestParam("name") String name) throws Exception {
        String result = this.programAssistant.makeQuestion(first,name);
        return ResponseEntity.ok(JacksonMapperUtils.json2pojo(result,ProgramRecord.class));
    }

    /**
     * 检查结果
     * @param request 信息
     * @return 结果
     * @throws Exception
     */
    @PostMapping(value = "/checkProgram", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> checkProgram(@RequestBody CheckProgramRequest request) throws Exception {
        String userContent = """
                                题目：{question},
                                示例输入:{input}
                                示例输出:{output}
                                回答内容:{code}
                                """;
        Flux<String> result = this.programAssistant.reviewQuestion(request.getQuestion(),request.getInput(),request.getOutput(),request.getCode());
        return result;
    }
}
