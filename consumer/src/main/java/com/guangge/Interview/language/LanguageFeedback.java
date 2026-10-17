package com.guangge.Interview.language;

/**
 * 一条"这一句可以更好"的建议。
 *
 * {@code original} 必须是用户原话里的片段（校验器会核对）：模型一旦开始编造原文，
 * 用户会照着一条自己没说过的话去"改错"。
 */
public record LanguageFeedback(String original, String better, String why) {
}
