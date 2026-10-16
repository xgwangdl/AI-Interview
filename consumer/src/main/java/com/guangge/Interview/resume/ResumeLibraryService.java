package com.guangge.Interview.resume;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guangge.Interview.resume.dto.ResumeDetailView;
import com.guangge.Interview.resume.dto.ResumeOptimizationResult;
import com.guangge.Interview.resume.dto.ResumeOptimizationView;
import com.guangge.Interview.resume.dto.ResumeRequests;
import com.guangge.Interview.resume.dto.ResumeView;
import com.guangge.Interview.resume.export.ExportedResume;
import com.guangge.Interview.resume.export.ResumeDocxExporter;
import com.guangge.Interview.resume.export.ResumeExportException;
import com.guangge.Interview.resume.export.ResumeExportFormat;
import com.guangge.Interview.resume.export.ResumeLayout;
import com.guangge.Interview.resume.export.ResumePdfExporter;
import com.guangge.Interview.resume.export.ResumeTheme;
import com.guangge.Interview.resume.export.ResumeThemeResolver;
import com.guangge.Interview.util.LogSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 简历库：增删改查 + AI 优化 + 逐段采纳。
 *
 * 与练习模块同样的隔离规则：不属于自己的资源一律 404，不泄露存在性。
 * 简历属个人敏感信息，所以日志里只记长度与条数，从不记正文。
 */
@Service
public class ResumeLibraryService {

    private static final Logger log = LoggerFactory.getLogger(ResumeLibraryService.class);
    /** 与需求文档一致：正文上限 20,000 字。 */
    public static final int MAX_TEXT_CHARS = 20000;

    private final ResumeLibraryRepository resumes;
    private final ResumeOptimizationRepository optimizations;
    private final ResumeOptimizationProvider provider;
    private final ObjectMapper objectMapper;
    private final ResumeDocxExporter docxExporter;
    private final ResumePdfExporter pdfExporter;
    private final ResumeThemeResolver themeResolver;

    public ResumeLibraryService(ResumeLibraryRepository resumes,
                               ResumeOptimizationRepository optimizations,
                               ResumeOptimizationProvider provider,
                               ObjectMapper objectMapper,
                               ResumeDocxExporter docxExporter,
                               ResumePdfExporter pdfExporter,
                               ResumeThemeResolver themeResolver) {
        this.resumes = resumes;
        this.optimizations = optimizations;
        this.provider = provider;
        this.objectMapper = objectMapper;
        this.docxExporter = docxExporter;
        this.pdfExporter = pdfExporter;
        this.themeResolver = themeResolver;
    }

    // ---------- 简历本体 ----------

    @Transactional
    public ResumeDetailView create(String ownerId, ResumeRequests.SaveResume request) {
        String text = trimmed(request.rawText());
        Instant now = Instant.now();
        ResumeEntity entity = new ResumeEntity();
        entity.setId(UUID.randomUUID());
        entity.setOwnerId(ownerId);
        entity.setTitle(request.title().trim());
        entity.setSource(ResumeEntity.SOURCE_UPLOAD.equals(request.source())
                ? ResumeEntity.SOURCE_UPLOAD : ResumeEntity.SOURCE_MANUAL);
        entity.setFileName(request.fileName());
        entity.setRawText(text);
        entity.setVersion(1);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        resumes.save(entity);
        log.info("新建简历 {}（owner={}，{} 字）", entity.getId(), ownerId, text.length());
        return toDetail(entity);
    }

    @Transactional(readOnly = true)
    public List<ResumeView> list(String ownerId) {
        return resumes.findAllByOwnerIdOrderByUpdatedAtDesc(ownerId).stream()
                .map(entity -> new ResumeView(
                        entity.getId(), entity.getTitle(), entity.getSource(), entity.getFileName(),
                        entity.getVersion(), entity.getRawText().length(),
                        !optimizations.findAllByResumeIdOrderByCreatedAtDesc(entity.getId()).isEmpty(),
                        entity.getUpdatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public ResumeDetailView get(String ownerId, UUID id) {
        return toDetail(requireOwned(ownerId, id));
    }

    @Transactional
    public ResumeDetailView update(String ownerId, UUID id, ResumeRequests.SaveResume request) {
        ResumeEntity entity = requireOwned(ownerId, id);
        String text = trimmed(request.rawText());
        boolean textChanged = !text.equals(entity.getRawText());
        entity.setTitle(request.title().trim());
        entity.setRawText(text);
        if (textChanged) entity.setVersion(entity.getVersion() + 1);
        entity.setUpdatedAt(Instant.now());
        resumes.save(entity);
        return toDetail(entity);
    }

    @Transactional
    public void delete(String ownerId, UUID id) {
        ResumeEntity entity = requireOwned(ownerId, id);
        // 优化记录随简历级联删除（外键 ON DELETE CASCADE），这里显式删一遍便于日志
        optimizations.deleteAll(optimizations.findAllByResumeIdOrderByCreatedAtDesc(id));
        resumes.delete(entity);
        log.info("删除简历 {}", id);
    }

    // ---------- 优化 ----------

    @Transactional
    public ResumeOptimizationView optimize(String ownerId, UUID resumeId, ResumeRequests.Optimize request) {
        ResumeEntity resume = requireOwned(ownerId, resumeId);
        ResumeOptimizationEntity entity = new ResumeOptimizationEntity();
        entity.setId(UUID.randomUUID());
        entity.setResumeId(resumeId);
        entity.setOwnerId(ownerId);
        entity.setRole(request.role());
        entity.setJobDescription(request.jobDescription());
        entity.setModel(provider.modelName());
        entity.setCreatedAt(Instant.now());
        try {
            ResumeOptimizationResult result =
                    provider.optimize(request.role(), request.jobDescription(), resume.getRawText());
            String optimizedText = String.join("\n\n", result.sections().stream()
                    .map(ResumeOptimizationResult.Section::optimized)
                    .toList());
            entity.setStatus(ResumeOptimizationEntity.STATUS_COMPLETED);
            entity.setOptimizedText(optimizedText);
            entity.setChangesJson(write(result.sections()));
            // 存"正文里真实出现的占位符"，而不是模型自述的那份：模型可能只声明不写
            entity.setPlaceholdersJson(write(ResumeOptimizationValidator.placeholdersIn(optimizedText)));
            optimizations.save(entity);
            log.info("简历 {} 优化完成：{} 段，{} 处待补充", resumeId,
                    result.sections().size(),
                    ResumeOptimizationValidator.placeholdersIn(optimizedText).size());
            return toOptimizationView(entity, result);
        } catch (RuntimeException exception) {
            entity.setStatus(ResumeOptimizationEntity.STATUS_FAILED);
            entity.setError(LogSanitizer.truncate(LogSanitizer.redact(message(exception)), 1000));
            optimizations.save(entity);
            log.warn("简历 {} 优化失败：{}", resumeId, entity.getError());
            return toOptimizationView(entity, null);
        }
    }

    @Transactional(readOnly = true)
    public List<ResumeOptimizationView> optimizationHistory(String ownerId, UUID resumeId) {
        requireOwned(ownerId, resumeId);
        return optimizations.findAllByResumeIdOrderByCreatedAtDesc(resumeId).stream()
                .map(entity -> toOptimizationView(entity, null))
                .toList();
    }

    @Transactional(readOnly = true)
    public ResumeOptimizationView optimization(String ownerId, UUID optimizationId) {
        ResumeOptimizationEntity entity = optimizations.findByIdAndOwnerId(optimizationId, ownerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Optimization not found"));
        return toOptimizationView(entity, null);
    }

    /**
     * 采纳优化结果。
     *
     * sections 为空 = 全部采用（前端会二次确认）；否则只替换选中的那几段。
     * 替换按"优化后文本里的段落"定位原文，找不到就跳过，不做模糊匹配——猜错段落比不替换更糟。
     */
    @Transactional
    public ResumeDetailView apply(String ownerId, UUID resumeId, UUID optimizationId, List<Integer> sections) {
        ResumeEntity resume = requireOwned(ownerId, resumeId);
        ResumeOptimizationEntity entity = optimizations.findByIdAndOwnerId(optimizationId, ownerId)
                .filter(item -> item.getResumeId().equals(resumeId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Optimization not found"));
        if (!ResumeOptimizationEntity.STATUS_COMPLETED.equals(entity.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "这次优化没有成功，无法采纳");
        }
        ResumeOptimizationResult result = readResult(entity);

        List<ResumeOptimizationResult.Section> chosen = new ArrayList<>();
        if (sections == null || sections.isEmpty()) {
            chosen.addAll(result.sections());
        } else {
            for (Integer index : sections) {
                if (index != null && index >= 0 && index < result.sections().size()) {
                    chosen.add(result.sections().get(index));
                }
            }
        }
        if (chosen.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "没有选中任何段落");
        }

        String text = resume.getRawText();
        int applied = 0;
        for (ResumeOptimizationResult.Section section : chosen) {
            if (section.original() == null || section.original().isBlank()) continue;   // 新增段落无处替换
            if (text.contains(section.original())) {
                text = text.replace(section.original(), section.optimized());
                applied++;
            }
        }
        if (applied == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "选中的段落都没能在当前正文里定位到（简历可能已被修改过），请重新优化");
        }
        resume.setRawText(text);
        resume.setVersion(resume.getVersion() + 1);
        resume.setUpdatedAt(Instant.now());
        resumes.save(resume);
        log.info("简历 {} 采纳 {} 段，版本 → {}", resumeId, applied, resume.getVersion());
        return toDetail(resume);
    }

    /**
     * 导出当前版本。
     *
     * 三种格式各有用处：Markdown 方便继续编辑与二次优化，Word 能直接改，
     * PDF 是投递与打印时最不容易被排版打乱的那份。内容都取自库里当前版本的正文。
     */
    @Transactional(readOnly = true)
    public ExportedResume export(String ownerId, UUID resumeId, ResumeExportFormat format) {
        ResumeEntity resume = requireOwned(ownerId, resumeId);
        try {
            // 强调色跟着账号的主题色走：导出的文件和用户在界面上看到的是同一套配色
            ResumeTheme theme = themeResolver.themeOf(ownerId);
            byte[] body = switch (format) {
                case MARKDOWN -> ResumeLayout.toMarkdown(resume.getTitle(), resume.getRawText())
                        .getBytes(StandardCharsets.UTF_8);
                case DOCX -> docxExporter.render(resume.getTitle(), resume.getRawText(), theme);
                case PDF -> pdfExporter.render(resume.getTitle(), resume.getRawText(), theme);
            };
            log.info("导出简历 {}：{}，{} 字节", resumeId, format.extension(), body.length);
            return new ExportedResume(fileNameOf(resume, format), body, format);
        } catch (IOException | ResumeExportException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, exportFailure(exception));
        }
    }

    /** 文件名用简历标题，去掉文件系统不认的字符；标题为空时退回 id。 */
    private static String fileNameOf(ResumeEntity resume, ResumeExportFormat format) {
        String title = resume.getTitle() == null ? "" : resume.getTitle().strip();
        String base = title.replaceAll("[\\\\/:*?\"<>|\\r\\n\\t]", "-").strip();
        if (base.isEmpty()) base = "resume-" + resume.getId();
        if (base.length() > 60) base = base.substring(0, 60);
        return base + "." + format.extension();
    }

    private static String exportFailure(Exception exception) {
        if (exception instanceof ResumeExportException) return exception.getMessage();
        String reason = exception.getMessage();
        return "导出失败：" + (reason == null || reason.isBlank()
                ? exception.getClass().getSimpleName() : LogSanitizer.truncate(reason, 200));
    }

    // ---------- 内部 ----------

    private ResumeEntity requireOwned(String ownerId, UUID id) {
        return resumes.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Resume not found"));
    }

    private static String trimmed(String text) {
        String value = text == null ? "" : text.strip();
        if (value.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "简历正文不能为空");
        }
        if (value.length() > MAX_TEXT_CHARS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "简历过长（" + value.length() + " 字，上限 " + MAX_TEXT_CHARS + " 字）");
        }
        return value;
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("简历数据序列化失败", exception);
        }
    }

    private <T> T read(String json, com.fasterxml.jackson.core.type.TypeReference<T> type, T fallback) {
        if (json == null || json.isBlank()) return fallback;
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception exception) {
            return fallback;
        }
    }

    private ResumeOptimizationResult readResult(ResumeOptimizationEntity entity) {
        List<ResumeOptimizationResult.Section> sections = read(entity.getChangesJson(),
                new com.fasterxml.jackson.core.type.TypeReference<>() { }, List.of());
        return new ResumeOptimizationResult(
                null,
                sections,
                read(entity.getPlaceholdersJson(),
                        new com.fasterxml.jackson.core.type.TypeReference<>() { }, List.of()),
                null);
    }

    private ResumeDetailView toDetail(ResumeEntity entity) {
        return new ResumeDetailView(entity.getId(), entity.getTitle(), entity.getSource(),
                entity.getFileName(), entity.getVersion(), entity.getRawText(), entity.getUpdatedAt());
    }

    private ResumeOptimizationView toOptimizationView(ResumeOptimizationEntity entity, ResumeOptimizationResult result) {
        List<ResumeOptimizationResult.Section> sections = result != null
                ? result.sections()
                : read(entity.getChangesJson(), new com.fasterxml.jackson.core.type.TypeReference<>() { }, List.of());
        List<String> placeholders = read(entity.getPlaceholdersJson(),
                new com.fasterxml.jackson.core.type.TypeReference<>() { }, List.of());
        return new ResumeOptimizationView(entity.getId(), entity.getResumeId(), entity.getRole(),
                entity.getStatus(), entity.getOptimizedText(),
                result == null ? null : result.summary(),
                sections, placeholders,
                result == null ? null : result.keywordCoverage(),
                entity.getError(), entity.getModel(), entity.getCreatedAt());
    }

    private static String message(RuntimeException exception) {
        String value = exception.getMessage();
        return value == null || value.isBlank() ? exception.getClass().getSimpleName() : value;
    }
}
