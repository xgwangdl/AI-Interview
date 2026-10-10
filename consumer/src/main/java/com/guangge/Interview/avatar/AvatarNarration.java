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
        String voice) { }
