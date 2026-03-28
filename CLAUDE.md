# CLAUDE.md

## 프로젝트
CARING Server - 돌봄 서비스 모놀리식 백엔드. Spring Boot 3.4.1, Java 17.

## 구조
```
com.caring/
├── api/          # 컨트롤러, UseCase, DTO (도메인별 하위 패키지)
├── domain/       # 엔티티, 리포지토리, 도메인 서비스, 어댑터
├── common/       # 어노테이션, 설정, 예외, 유틸
├── security/     # SecurityConfig, JWT 필터, TokenService
└── infra/        # 외부 연동 (AI 서버 등)
```

## 태스크 라우팅

요청을 아래 태스크로 라우팅한다.

| task | spec | trigger |
|---|---|---|
| `branch_compare` | `agents/branch_compare.md` | 브랜치 비교, main과 차이, 머지 위험, 변경점 분석 |
| `pr_create` | `agents/pr_create.md` | PR 생성, PR 요약, PR 작성 |

- 브랜치 비교 + PR 생성이 같이 요청되면 `branch_compare` → `pr_create` 순서.
- 불명확하면 사용자에게 확인.

## 빌드 & 실행
```bash
# 빌드
./gradlew build -x test

# Docker 실행 (기본)
docker compose up --build

# Docker 종료
docker compose down
```

## 환경 변수
`.env` 파일 필요. 필수값:
- `TOKEN_SECRET_USER` - JWT 시크릿 (Base64, 최소 256bit)

선택값:
- `FCM_KEY_BASE64` - Firebase 서비스 계정 JSON (Base64)
- `AI_SERVICE_BASE_URL` - AI 서버 주소

DB, Redis는 compose에서 함께 띄움.

## 코드 컨벤션
- 컨트롤러: `{Domain}ApiController`
- 유스케이스: `{Action}UseCase` (api 레이어)
- 도메인 서비스: `{Domain}DomainService` / `{Domain}DomainServiceImpl`
- 어댑터: `{Domain}Adaptor` / `{Domain}AdaptorImpl`
- 커스텀 어노테이션: `@UseCase`, `@DomainService`, `@Adaptor`, `@Validator`
