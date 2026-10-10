package com.guangge.Interview.avatar;

import java.util.Optional;

public interface AvatarProvider {

    /** Describes the avatar state, caption and audio location for a question. */
    AvatarNarration narrate(AvatarNarrationRequest request);

    /** Synthesized narration audio, or empty when this provider cannot speak. */
    Optional<SpeechAudio> speech(AvatarNarrationRequest request);
}
