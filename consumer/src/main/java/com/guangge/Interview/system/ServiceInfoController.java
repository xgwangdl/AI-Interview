package com.guangge.Interview.system;

import com.guangge.Interview.audio.services.AudioConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/** 后端根地址用于部署探活，避免直接打开 API 域名时误以为服务不存在。 */
@RestController
public class ServiceInfoController {

    private final String serviceName;
    private final String version;
    private final AudioConverter audioConverter;

    public ServiceInfoController(
            @Value("${spring.application.name:light-interview}") String serviceName,
            @Value("${info.app.version:0.0.1-SNAPSHOT}") String version,
            AudioConverter audioConverter) {
        this.serviceName = serviceName;
        this.version = version;
        this.audioConverter = audioConverter;
    }

    @GetMapping("/")
    public Map<String, Object> info() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("service", serviceName);
        response.put("status", "UP");
        response.put("version", version);
        response.put("health", "/actuator/health");
        response.put("api", "/api/v1");
        response.put("ffmpegAvailable", audioConverter.isAvailable());
        return response;
    }
}
