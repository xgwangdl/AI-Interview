package com.guangge.Interview.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DeploymentSecurityCheckTest {

    @Test
    void productionRejectsTheDefaultSecret() {
        DeploymentSecurityCheck check = new DeploymentSecurityCheck(
                "production", "change-this-development-secret");

        assertThrows(IllegalStateException.class, check::validate);
    }

    @Test
    void productionAcceptsAStrongSecret() {
        DeploymentSecurityCheck check = new DeploymentSecurityCheck(
                "production", "this-is-a-long-production-secret-123");

        assertDoesNotThrow(check::validate);
    }

    @Test
    void localDevelopmentKeepsTheDefaultForConvenience() {
        DeploymentSecurityCheck check = new DeploymentSecurityCheck(
                "local", "change-this-development-secret");

        assertDoesNotThrow(check::validate);
    }
}
