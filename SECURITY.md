# Security Policy

## Supported Versions

Security fixes are applied to the latest `master` branch.

## Reporting a Vulnerability

Please do not open a public issue for vulnerabilities involving authentication,
data isolation, sandbox escape, uploaded media, secrets, or personal data.

Report the issue privately to the repository owner with:

- affected endpoint or component;
- reproduction steps;
- impact assessment;
- suggested mitigation, if available.

Do not include real API keys, recordings, resumes, passwords, or other personal data
in the report.

## Deployment Notes

- Set a strong `AUTH_TOKEN_SECRET`; never use the development default in production.
- Keep `CODE_RUNNER_ENABLED=false` unless the host is dedicated to trusted local use.
- Set `APP_ENV=production` to enable startup checks for insecure defaults.
- Expose only the intended public ports and keep the backend behind the web reverse proxy.
