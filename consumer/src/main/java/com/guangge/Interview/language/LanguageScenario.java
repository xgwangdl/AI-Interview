package com.guangge.Interview.language;

/**
 * 一个练习场景。
 *
 * 开场白是**写死**的：第一句不需要模型，省一次调用也让"点开始就能听到人说话"这件事不依赖模型是否在线。
 */
public record LanguageScenario(
        String id,
        String title,
        String chineseHint,
        String goal,
        String opening) {
}
