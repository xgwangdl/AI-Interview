# Light-Interview

A **video practice and review tool for job interviews**: pick a role, record one answer per question,
then get a transcription, structured scoring, and a per-question report with evidence anchored to the video timeline.

[中文 README](../README.md) · [Deployment guide](DEPLOYMENT.md) · Apache License 2.0

> Chinese-language deep dives: [USER-GUIDE.md](USER-GUIDE.md) (how to use the system) and
> [ARCHITECTURE.md](ARCHITECTURE.md) (code structure, data flow, extension points).

## Features

- **Practice setup** — target role, company, job description, resume file name, interview type,
  interviewer style, difficulty, question count and duration.
- **Device check** — camera and microphone permission, live preview, ambient volume check.
- **AI question planning** — the uploaded PDF/Word resume is parsed and combined with the target role,
  company and job description to generate questions, with a silent fallback to the built-in bank when
  the model is unavailable. The resume text is used for planning only and is never persisted.
- **Mock interview** — a digital interviewer narrates each question (with mouth animation, or a
  caption-only fallback), and every answer is recorded separately, with pause and retake.
- **AI review** — speech-to-text, then structured scoring (relevance, completeness, professionalism,
  STAR structure, logic, speech rate, pauses, filler words) surfaced per question with timeline evidence.
- **Retention and deletion** — recordings are kept for 7 days by default and can be deleted at any
  time, which also removes the media, transcripts and scores.

## Stack

| Layer | Choice |
| --- | --- |
| Backend | Java 21 · Spring Boot 3.4 · Spring AI 1.1.8 · Spring AI Alibaba 1.1.2.4 (DashScope) |
| Frontend | Vue 3.5 · Vite 6 · Element Plus · Pinia |
| Storage | PostgreSQL 16 + pgvector · Redis 7 · S3-compatible object storage (local disk / MinIO / AWS S3) |
| Media | Browser `MediaRecorder` · ffmpeg audio extraction · Paraformer ASR · Sambert TTS |

## Quick start

```bash
cp .env.example .env          # set Ali_API_KEY and AUTH_TOKEN_SECRET at minimum
# the backend auto-loads .env from the repository root; no manual export needed
docker compose up -d

mvn -B clean package
java -jar consumer/target/consumer-0.0.1-SNAPSHOT.jar   # Flyway migrations run on first start

# Migrations create the table but no users; provision a login after the backend has started once.
docker compose exec -T postgres psql -U postgres -d interview \
  -c "INSERT INTO candidates (candidate_name, invitation_code, status) VALUES ('demo','123456',1);"

cd web && npm install && npm run dev
```

Open <http://localhost:5173> and sign in with `demo / 123456`.
See [DEPLOYMENT.md](DEPLOYMENT.md) for the full configuration reference and storage switching.

## Tests

```bash
mvn -B clean package                                    # unit tests; external dependencies skip
mvn -B test -Ddb.integration=true -Ds3.integration=true # against real Postgres / S3
pwsh scripts/smoke-test.ps1 -Name demo -Code 123456     # end-to-end HTTP smoke test
```

## Verification status

Verified: application startup against real PostgreSQL + Redis with all Flyway migrations applied;
the full browser flow (login → setup → device check → five-question recording → report);
both local and S3 media storage; HTTP Range support on the media endpoint; 401 for unauthenticated
requests and 404 for cross-user access; deletion removing media and records.

Not yet verified: a single session that combines a real camera recording with real model scoring
(both halves are verified separately), the two camera acceptance cases (permission denied / missing
device, recording interrupted / offline recovery), and the amplitude-driven mouth animation, which
is build-verified only.

Real model calls were verified end to end on 2026-10-10 with a live `Ali_API_KEY`: AI question
planning, Paraformer transcription, Sambert synthesis and Spring AI evaluation all returned real
results. See "真实供应商验证结果" in [STATUS.md](STATUS.md).

## License

Apache License 2.0. Refactored from [guangge](https://github.com/xgwangdl)'s AI-Interview.
