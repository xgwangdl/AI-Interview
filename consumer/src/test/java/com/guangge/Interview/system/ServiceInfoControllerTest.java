package com.guangge.Interview.system;

import com.guangge.Interview.audio.services.AudioConverter;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ServiceInfoControllerTest {

    @Test
    void exposesSafeServiceInformationAtTheRoot() {
        AudioConverter audioConverter = mock(AudioConverter.class);
        when(audioConverter.isAvailable()).thenReturn(true);
        ServiceInfoController controller = new ServiceInfoController(
                "light-interview", "0.0.1-SNAPSHOT", audioConverter);

        Map<String, Object> info = controller.info();

        assertEquals("light-interview", info.get("service"));
        assertEquals("UP", info.get("status"));
        assertEquals("/actuator/health", info.get("health"));
        assertEquals("/api/v1", info.get("api"));
        assertEquals(true, info.get("ffmpegAvailable"));
    }
}
