# Dependency Notes

## Spring AI Alibaba

`consumer/pom.xml` currently pins `com.alibaba.cloud.ai:spring-ai-alibaba:1.1.2.4-security-fix`.

This is a project-specific security-fix pin. Before changing it:

1. confirm the artifact exists in the configured Maven repository;
2. inspect the upstream release notes and checksum/signature;
3. run the full backend test suite and the real-provider smoke script;
4. record the upgrade and rollback version here.

## PostgreSQL JDBC

The project uses PostgreSQL JDBC `42.6.0`. The comment in the POM describes it as a
compatible baseline, not the latest available release. Upgrade it only with the database
integration job enabled.

## Sandbox Images

The code runner language images are intentionally configured in `CodeLanguage` rather than
automatically tracking `latest`. Change them together with sandbox integration tests.
