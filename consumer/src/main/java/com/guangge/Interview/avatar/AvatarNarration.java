package com.guangge.Interview.avatar;

/**
 * How the interviewer should present one question: the avatar state, the caption,
 * and where the spoken audio can be fetched. When {@code audioAvailable} is false the
 * client falls back to a plain avatar plus the caption text.
 */
public record AvatarNarration(
        String provider,
        String state,
        String text,
        String interviewerStyle,
        boolean audioAvailable,
        String audioUrl,
        String voice,
        /**
         * 文本超过单次合成上限、语音只念了前面一段时为 true。
         * 客户端据此提示"语音只念了前半段"，而不是让人以为音频坏了。
         */
        boolean truncated) { }
