package com.guangge.Interview.notification;

import com.guangge.Interview.practice.security.OwnerResolver;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class NotificationController {

    private final OwnerResolver owners;
    private final NotificationService notifications;

    public NotificationController(OwnerResolver owners, NotificationService notifications) {
        this.owners = owners;
        this.notifications = notifications;
    }

    @GetMapping("/api/v1/me/notifications")
    public NotificationPreferencesView get(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return notifications.get(owners.requireOwner(authorization));
    }

    @PutMapping("/api/v1/me/notifications")
    public NotificationPreferencesView update(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody NotificationPreferencesRequest request) {
        return notifications.update(owners.requireOwner(authorization), request);
    }

    @org.springframework.web.bind.annotation.PostMapping("/api/v1/me/notifications/test")
    public Map<String, Boolean> test(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return Map.of("sent", notifications.sendTest(owners.requireOwner(authorization)));
    }

    /** 邮件底部的退订链接不带登录态，用 owner + HMAC token 验证。 */
    @GetMapping("/api/v1/notifications/unsubscribe")
    public ResponseEntity<String> unsubscribe(@RequestParam String owner, @RequestParam String token) {
        boolean ok = notifications.unsubscribe(owner, token);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(ok ? "<html><body>已关闭邮件提醒。可以回到设置页重新打开。</body></html>"
                        : "<html><body>退订链接无效。</body></html>");
    }
}
