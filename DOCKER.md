## Docker Run

### Prerequisites

`.env` 파일에 필요한 값을 채워야 합니다:

```dotenv
# --- 필수 ---
TOKEN_SECRET_USER=<Base64 encoded, 최소 256bit>

# --- 선택 (사용 시 필수) ---
FCM_KEY_BASE64=<Firebase service account JSON을 Base64 인코딩>
AI_SERVICE_BASE_URL=http://host.docker.internal:8090
```

DB, Redis는 compose에서 함께 띄우므로 기본값으로 동작합니다.

### Start

```bash
docker compose up --build
```

### Stop

```bash
docker compose down
```

볼륨까지 삭제:

```bash
docker compose down -v
```

### Notes

- `SPRING_PROFILES_ACTIVE=docker`로 실행됩니다.
- MySQL, Redis가 compose에 포함되어 별도 설치가 필요 없습니다.
- `.env`의 `DB_URL`은 로컬 직접 실행용이며, Docker에서는 compose가 오버라이드합니다.
