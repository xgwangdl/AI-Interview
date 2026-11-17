package com.guangge.Interview.privacy;

import com.guangge.Interview.practice.security.OwnerResolver;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/me")
public class AccountDataController {

    private final OwnerResolver owners;
    private final AccountDataService accountData;

    public AccountDataController(OwnerResolver owners, AccountDataService accountData) {
        this.owners = owners;
        this.accountData = accountData;
    }

    @GetMapping("/data-export")
    public ResponseEntity<Map<String, Object>> export(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        String ownerId = owners.requireOwner(authorization);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename("light-interview-export-" + safeFilename(ownerId) + ".json", StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(accountData.export(ownerId));
    }

    @DeleteMapping
    public ResponseEntity<Void> delete(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody AccountDeleteRequest request) {
        String ownerId = owners.requireOwner(authorization);
        accountData.deleteAccount(ownerId, request.confirmation());
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    private static String safeFilename(String ownerId) {
        String safe = ownerId.replaceAll("[^A-Za-z0-9._-]", "_");
        return safe.isBlank() ? "account" : safe;
    }
}
