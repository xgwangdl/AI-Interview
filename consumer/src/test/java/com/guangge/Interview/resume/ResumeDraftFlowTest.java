package com.guangge.Interview.resume;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guangge.Interview.resume.dto.ResumeDraftRequests;
import com.guangge.Interview.resume.dto.ResumeDraftResult;
import com.guangge.Interview.resume.dto.ResumeDraftView;
import com.guangge.Interview.resume.export.ResumeDocxExporter;
import com.guangge.Interview.resume.export.ResumePdfExporter;
import com.guangge.Interview.resume.export.ResumeThemeResolver;
import com.guangge.Interview.quota.ModelCallGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 从零创建的服务行为：不落库、越权 404、模型出错给可读的 502。 */
class ResumeDraftFlowTest {

    private final ResumeLibraryRepository resumes = mock(ResumeLibraryRepository.class);
    private final ResumeOptimizationRepository optimizations = mock(ResumeOptimizationRepository.class);
    private final ResumeOptimizationProvider optimizationProvider = mock(ResumeOptimizationProvider.class);
    private final ResumeThemeResolver themeResolver = mock(ResumeThemeResolver.class);
    private final ResumeDraftProvider draftProvider = mock(ResumeDraftProvider.class);

    private final ResumeLibraryService service = new ResumeLibraryService(
            resumes, optimizations, optimizationProvider, new ObjectMapper(),
            new ResumeDocxExporter(), new ResumePdfExporter("", ""), themeResolver, draftProvider,
            ModelCallGuard.inMemory(true, 10_000, 10));

    @BeforeEach
    void stubModelName() {
        when(draftProvider.modelName()).thenReturn("stub-model");
    }

    @Test
    @DisplayName("生成草稿不写库：用户点保存之前，服务端不留任何东西")
    void draftingWritesNothing() {
        when(draftProvider.draft(any())).thenReturn(new ResumeDraftResult(
                "张三\n求职目标：Java 后端工程师\n\n教育背景\n- 【请补充：学校、专业、起止时间】",
                List.of("补一条量化结果"),
                List.of("【请补充：学校、专业、起止时间】")));

        ResumeDraftView view = service.draft("smoke", emptyMaterials());

        assertThat(view.rawText()).startsWith("张三");
        assertThat(view.questions()).containsExactly("补一条量化结果");
        assertThat(view.placeholders()).containsExactly("【请补充：学校、专业、起止时间】");
        assertThat(view.model()).isEqualTo("stub-model");
        verifyNoInteractions(resumes, optimizations);
    }

    @Test
    @DisplayName("什么素材都没给也能出结果：一份带占位符的骨架")
    void acceptsEmptyMaterials() {
        when(draftProvider.draft(any())).thenReturn(new ResumeDraftResult(
                "【请补充：姓名】\n求职目标：【请补充：目标岗位】",
                List.of("先补上姓名与目标岗位"),
                List.of("【请补充：姓名】", "【请补充：目标岗位】")));

        ResumeDraftView view = service.draft("smoke", emptyMaterials());

        assertThat(view.placeholders()).hasSize(2);
    }

    @Test
    @DisplayName("模型没按约定返回 → 502 且提示可重试，而不是 500 甩锅")
    void modelContractFailureBecomesBadGateway() {
        when(draftProvider.draft(any())).thenThrow(new ResumeFormatException("resumeText 不能为空"));

        assertThatThrownBy(() -> service.draft("smoke", emptyMaterials()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("请重试")
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
                .isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    @Test
    @DisplayName("补全：正文或补充内容为空是 400，且完全不碰数据库")
    void refineValidatesInput() {
        assertThatThrownBy(() -> service.refine("smoke", "   ", "补一条数据", List.of()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        assertThatThrownBy(() -> service.refine("smoke", "张三\n教育背景", "   ", List.of()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verifyNoInteractions(resumes, optimizations);
    }

    @Test
    @DisplayName("补全：现有正文与补充信息一起交给模型，返回的新正文同样不落库")
    void refineReturnsTheNewTextWithoutSaving() {
        when(draftProvider.refine(anyString(), anyString(), any())).thenReturn(new ResumeDraftResult(
                "张三\n工作经历\n- 时间：2021.03 - 2022.01\n- 结果：接口耗时从 800ms 降到 120ms",
                List.of(),
                List.of()));

        ResumeDraftView view = service.refine("smoke",
                "张三\n工作经历\n- 时间：2021.03 - 2022.01\n- 结果：【请补充：量化结果】",
                "接口耗时从 800ms 降到 120ms", List.of("补一条量化结果"));

        assertThat(view.rawText()).contains("800ms");
        assertThat(view.placeholders()).isEmpty();
        verifyNoInteractions(resumes, optimizations);
    }

    private static ResumeDraftRequests.Draft emptyMaterials() {
        return new ResumeDraftRequests.Draft(null, null, List.of(), List.of(), List.of(), null, null);
    }

}
