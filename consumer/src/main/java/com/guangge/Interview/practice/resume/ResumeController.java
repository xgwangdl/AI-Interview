package com.guangge.Interview.practice.resume;

import com.guangge.Interview.practice.security.OwnerResolver;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

@RestController
@RequestMapping("/api/v1/resume")
public class ResumeController {

    private final OwnerResolver owners;
    private final ResumeExtractionService resumes;

    public ResumeController(OwnerResolver owners, ResumeExtractionService resumes) {
        this.owners = owners;
        this.resumes = resumes;
    }

    @PostMapping(value = "/extract", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResumeExtractView extract(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestPart("file") MultipartFile file) throws IOException {
        owners.requireOwner(authorization);
        try (InputStream content = file.getInputStream()) {
            return resumes.extract(file.getOriginalFilename(), file.getSize(), content);
        }
    }
}
