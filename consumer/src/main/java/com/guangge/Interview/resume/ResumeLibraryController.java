package com.guangge.Interview.resume;

import com.guangge.Interview.practice.resume.ResumeExtractionService;
import com.guangge.Interview.practice.resume.ResumeExtractView;
import com.guangge.Interview.practice.security.OwnerResolver;
import com.guangge.Interview.resume.dto.ResumeDetailView;
import com.guangge.Interview.resume.dto.ResumeOptimizationView;
import com.guangge.Interview.resume.dto.ResumeRequests;
import com.guangge.Interview.resume.dto.ResumeView;
import com.guangge.Interview.resume.export.ExportedResume;
import com.guangge.Interview.resume.export.ResumeExportFormat;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/** 简历库接口，见 docs/RESUME-REQUIREMENTS.md §6。 */
@RestController
@RequestMapping("/api/v1/resumes")
public class ResumeLibraryController {

    private final OwnerResolver owners;
    private final ResumeLibraryService library;
    private final ResumeExtractionService extraction;

    public ResumeLibraryController(OwnerResolver owners,
                                   ResumeLibraryService library,
                                   ResumeExtractionService extraction) {
        this.owners = owners;
        this.library = library;
        this.extraction = extraction;
    }

    /**
     * 上传文件解析出正文，**不落库**。
     *
     * 复用出题链路那套 Tika 抽取；先给用户过目再保存，避免解析残缺直接进库。
     */
    @PostMapping(value = "/parse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResumeExtractView parse(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestPart("file") MultipartFile file) throws IOException {
        owners.requireOwner(authorization);
        try (InputStream content = file.getInputStream()) {
            return extraction.extract(file.getOriginalFilename(), file.getSize(), content);
        }
    }

    @PostMapping
    public ResumeDetailView create(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Validated @RequestBody ResumeRequests.SaveResume request) {
        return library.create(owners.requireOwner(authorization), request);
    }

    @GetMapping
    public List<ResumeView> list(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return library.list(owners.requireOwner(authorization));
    }

    @GetMapping("/{id}")
    public ResumeDetailView get(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID id) {
        return library.get(owners.requireOwner(authorization), id);
    }

    @PutMapping("/{id}")
    public ResumeDetailView update(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID id,
            @Validated @RequestBody ResumeRequests.SaveResume request) {
        return library.update(owners.requireOwner(authorization), id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID id) {
        library.delete(owners.requireOwner(authorization), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/optimizations")
    public ResumeOptimizationView optimize(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID id,
            @Valid @RequestBody ResumeRequests.Optimize request) {
        return library.optimize(owners.requireOwner(authorization), id, request);
    }

    @GetMapping("/{id}/optimizations")
    public List<ResumeOptimizationView> history(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID id) {
        return library.optimizationHistory(owners.requireOwner(authorization), id);
    }

    @GetMapping("/optimizations/{optimizationId}")
    public ResumeOptimizationView optimization(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID optimizationId) {
        return library.optimization(owners.requireOwner(authorization), optimizationId);
    }

    /** 采纳：sections 为空表示全部采用。 */
    @PostMapping("/{id}/optimizations/{optimizationId}/apply")
    public ResumeDetailView apply(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID id,
            @PathVariable UUID optimizationId,
            @Valid @RequestBody ResumeRequests.Apply request) {
        return library.apply(owners.requireOwner(authorization), id, optimizationId, request.sections());
    }

    /**
     * 导出。
     *
     * 走鉴权接口而不是静态目录：简历是隐私内容，不能放在可猜的路径上
     * （遗留实现把 PDF 放进 external/static/pdf/ 直接下载，这是要改掉的）。
     * format 支持 md（默认）/ docx / pdf。
     */
    @GetMapping("/{id}/export")
    public ResponseEntity<byte[]> export(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID id,
            @RequestParam(defaultValue = "md") String format) {
        ExportedResume exported = library.export(
                owners.requireOwner(authorization), id, ResumeExportFormat.parse(format));
        String name = URLEncoder.encode(exported.fileName(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(exported.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + name)
                .body(exported.body());
    }
}
