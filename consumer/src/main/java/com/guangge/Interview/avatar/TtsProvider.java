package com.guangge.Interview.avatar;

/** Provider-agnostic text-to-speech contract. */
public interface TtsProvider {

    SpeechAudio synthesize(String text);
}
