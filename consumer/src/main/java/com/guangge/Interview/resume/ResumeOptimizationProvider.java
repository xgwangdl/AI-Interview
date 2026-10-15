package com.guangge.Interview.resume;

import com.guangge.Interview.resume.dto.ResumeOptimizationResult;

/** 简历优化供应商（真实实现走大模型，测试可替换）。 */
public interface ResumeOptimizationProvider {

    ResumeOptimizationResult optimize(String role, String jobDescription, String resumeText);

    /** 用于落库记录是哪个模型给的结果。 */
    String modelName();
}
