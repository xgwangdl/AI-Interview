package com.guangge.Interview.practice.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** 对某道题的回答请求一条追问。 */
public record PracticeFollowUpRequest(@NotNull UUID answerId) {
}
