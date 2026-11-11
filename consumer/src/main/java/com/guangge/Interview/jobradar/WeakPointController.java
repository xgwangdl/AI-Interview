package com.guangge.Interview.jobradar;

import com.guangge.Interview.practice.security.OwnerResolver;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/training/weak-points")
public class WeakPointController {
    private final OwnerResolver owners;
    private final WeakPointService weakPoints;

    public WeakPointController(OwnerResolver owners, WeakPointService weakPoints) {
        this.owners = owners;
        this.weakPoints = weakPoints;
    }

    @GetMapping
    public Map<String, Object> list(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestParam(required = false) String source,
            @RequestParam(defaultValue = "open") String status,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit) {
        return weakPoints.list(owners.requireOwner(authorization), source, status, offset, limit);
    }

    @PostMapping("/{id}/close")
    public WeakPointView close(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable UUID id) {
        return weakPoints.close(owners.requireOwner(authorization), id);
    }
}
