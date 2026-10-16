package com.guangge.Interview.practice.archive;

import com.guangge.Interview.practice.dto.PracticeArchiveView;
import com.guangge.Interview.practice.security.OwnerResolver;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 训练档案接口：跨场次的成长与短板。 */
@RestController
@RequestMapping("/api/v1/practice-archive")
public class PracticeArchiveController {

    private final OwnerResolver owners;
    private final PracticeArchiveService archive;

    public PracticeArchiveController(OwnerResolver owners, PracticeArchiveService archive) {
        this.owners = owners;
        this.archive = archive;
    }

    @GetMapping
    public PracticeArchiveView summary(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestParam(defaultValue = "5") int limit) {
        return archive.summary(owners.requireOwner(authorization), limit);
    }
}
