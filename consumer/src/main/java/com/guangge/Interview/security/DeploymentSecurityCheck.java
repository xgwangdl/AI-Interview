package com.guangge.Interview.security;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Set;

/** 生产部署缺少关键安全配置时直接阻止启动。 */
@Component
public class DeploymentSecurityCheck {

    private static final Logger log = LoggerFactory.getLogger(DeploymentSecurityCheck.class);
    private static final Set<String> UNSAFE_SECRETS = Set.of(
            "", "change-this-development-secret", "change-this-in-docker", "changeme");

    private final String environment;
    private final String tokenSecret;

    public DeploymentSecurityCheck(
            @Value("${app.environment:local}") String environment,
            @Value("${auth.token.secret:}") String tokenSecret) {
        this.environment = environment == null ? "local" : environment.trim().toLowerCase();
        this.tokenSecret = tokenSecret;
    }

    @PostConstruct
    public void validate() {
        boolean production = "production".equals(environment) || "prod".equals(environment);
        if (production && (tokenSecret == null || UNSAFE_SECRETS.contains(tokenSecret.trim()))) {
            throw new IllegalStateException("Production requires a strong non-default AUTH_TOKEN_SECRET");
        }
        if (!production && (tokenSecret == null || UNSAFE_SECRETS.contains(tokenSecret.trim()))) {
            log.warn("当前使用开发默认 AUTH_TOKEN_SECRET；部署到公网前必须更换并设置 APP_ENV=production");
        }
    }
}
