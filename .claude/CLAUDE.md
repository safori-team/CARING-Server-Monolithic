# CLAUDE.md

## Project
CARING Server - a monolithic backend for a care service. Spring Boot 3.4.1, Java 17.

## Structure
```
com.caring/
├── api/          # Controllers, UseCases, DTOs (with sub-packages by domain)
├── domain/       # Entities, repositories, domain services, adaptors
├── common/       # Annotations, configuration, exceptions, utilities
├── security/     # SecurityConfig, JWT filters, TokenService
└── infra/        # External integrations (AI server, etc.)
```

## Task Routing

Route requests to the tasks below.

| task | spec | trigger |
|---|---|---|
| `branch_compare` | `agents/branch_compare.md` | branch comparison, differences from `main`, merge risk, change analysis |
| `pr_create` | `agents/pr_create.md` | creating a PR, PR summary, PR write-up |

- If branch comparison and PR creation are requested together, run `branch_compare` first, then `pr_create`.
- If the request is ambiguous, ask the user for clarification.

## Build & Run
```bash
# Build
./gradlew build -x test

# Run with Docker (default)
docker compose up --build

# Stop Docker
docker compose down
```

## Environment Variables
An `.env` file is required. Required value:
- `TOKEN_SECRET_USER` - JWT secret (Base64, minimum 256 bits)

Optional values:
- `FCM_KEY_BASE64` - Firebase service account JSON (Base64)
- `AI_SERVICE_BASE_URL` - AI server URL

The database and Redis are started together via Compose.

## Code Conventions
- Controller: `{Domain}ApiController`
- Use case: `{Action}UseCase` (API layer)
- Domain service: `{Domain}DomainService` / `{Domain}DomainServiceImpl`
- Adaptor: `{Domain}Adaptor` / `{Domain}AdaptorImpl`
- Custom annotations: `@UseCase`, `@DomainService`, `@Adaptor`, `@Validator`
