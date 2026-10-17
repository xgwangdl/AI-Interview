package com.guangge.Interview.profile;

import java.time.Instant;

public record ProfileView(
        String loginName,
        String displayName,
        String email,
        String timezone,
        String fontScale,
        boolean reduceMotion,
        String theme,
        Instant joinedAt) {
}
