package com.guangge.Interview.avatar;

/** Presentation states a digital interviewer can be in. */
public final class AvatarState {

    public static final String IDLE = "idle";
    public static final String THINKING = "thinking";
    public static final String ASKING = "asking";
    public static final String LISTENING = "listening";
    public static final String FINISHED = "finished";

    private AvatarState() { }
}
