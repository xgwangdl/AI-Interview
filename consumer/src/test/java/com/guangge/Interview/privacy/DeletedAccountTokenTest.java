package com.guangge.Interview.privacy;

import com.guangge.Interview.auth.Sign;
import com.guangge.Interview.practice.security.OwnerResolver;
import com.guangge.Interview.repository.CandidatesRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeletedAccountTokenTest {

    @Test
    void aValidJwtStopsWorkingImmediatelyAfterItsAccountIsDeleted() {
        String secret = "deleted-account-test-secret";
        CandidatesRepository candidates = mock(CandidatesRepository.class);
        when(candidates.existsByName("alice")).thenReturn(false);
        OwnerResolver resolver = new OwnerResolver(secret, candidates);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> resolver.requireOwner("Bearer " + Sign.getToken("alice", secret)));

        assertEquals(HttpStatus.UNAUTHORIZED, error.getStatusCode());
    }
}
