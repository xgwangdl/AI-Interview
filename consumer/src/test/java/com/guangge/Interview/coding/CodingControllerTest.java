package com.guangge.Interview.coding;

import com.guangge.Interview.practice.security.OwnerResolver;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CodingControllerTest {

    @Test
    void capabilitiesRequireAuthentication() {
        OwnerResolver owners = mock(OwnerResolver.class);
        when(owners.requireOwner(null))
                .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required"));
        CodingController controller = new CodingController(
                owners, mock(CodingSessionService.class), mock(CodeRunnerService.class));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> controller.capabilities(null));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
    }
}
