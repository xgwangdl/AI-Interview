package com.guangge.Interview.resume;

import com.guangge.Interview.resume.dto.ResumeDraftRequests;
import com.guangge.Interview.resume.dto.ResumeDraftResult;

/** 从零创建的供应商（真实实现走大模型，测试可替换）。 */
public interface ResumeDraftProvider {

    /** 素材 → 简历正文 + 最多 3 条追问。 */
    ResumeDraftResult draft(ResumeDraftRequests.Draft materials);

    /** 现有正文 + 用户补充的信息 → 新的完整正文。 */
    ResumeDraftResult refine(String currentText, String supplement, java.util.List<String> openQuestions);

    String modelName();
}
