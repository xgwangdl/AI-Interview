package com.guangge.Interview.coding.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 答题内容。
 *
 * 各题型的 answer 形态：
 * <ul>
 *   <li>coding / sql：源码字符串</li>
 *   <li>fill_blank：JSON 数组字符串，按空顺序，如 {@code ["i++","return a;"]}</li>
 *   <li>choice：选项 key，多个用逗号分隔，如 {@code "A,C"}</li>
 *   <li>short_answer：回答正文</li>
 * </ul>
 */
public record CodingAnswerRequest(@NotNull @Size(max = 200000) String answer) {
}
