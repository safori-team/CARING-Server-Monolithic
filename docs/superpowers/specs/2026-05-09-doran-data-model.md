# 도란이 마이그레이션 — 데이터 모델 비교 / 신규 스키마

**작성일**: 2026-05-09
**대상**: `Caring_Lambda` PostgreSQL → `CARING-Server-Monolithic` MySQL
**관련 설계서**: `2026-05-09-doran-migration-design.md`

## 0. 요약

| 항목 | 기존 (PostgreSQL) | 신규 (MySQL) |
|---|---|---|
| 테이블 수 | 2개 (`cbt_logs`, `weekly_reports`) | 2개 (`chat_session`, `chat_message`) |
| PK | BIGSERIAL / TEXT | CHAR(36) UUID v4 / BIGINT AUTO_INCREMENT |
| 임베딩 | `VECTOR(1024)` | **제거** |
| s3_url | TEXT 컬럼 | `voice_id` FK로 간접 참조 |
| user 식별 | TEXT (외부 시스템 user_id) | BIGINT FK → `user.id` |
| 리포트 | `weekly_reports` 테이블 | **제거** (이번 마이그레이션 대상 아님) |
| 피드백 | 없음 | `chat_message`에 컬럼 3개 신규 |

**현재 모놀리식(MySQL)의 챗봇 관련 테이블**: **없음** (clean slate)

---

## 1. 기존 테이블 (Caring_Lambda PostgreSQL)

스키마 정의 SQL 파일이 없어 repository 코드(`chat_repository.py`, `report_repository.py`)에서 역공학.

### 1.1 `cbt_logs`

```sql
CREATE TABLE cbt_logs (
    log_id        BIGSERIAL PRIMARY KEY,
    user_id       TEXT NOT NULL,                    -- 외부 시스템 사용자 ID
    session_id    TEXT NOT NULL,                    -- 6자리 영숫자 (예: "A1B2C3")
    user_input    TEXT,                             -- 사용자 메시지
    bot_response  JSONB,                            -- 도란이 응답 6개 필드
    embedding     VECTOR(1024),                     -- Bedrock Titan v2 임베딩
    s3_url        TEXT,                             -- 음성 파일 S3 URL (텍스트 채팅은 NULL)
    created_at    TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_cbt_logs_user_id ON cbt_logs(user_id);
CREATE INDEX idx_cbt_logs_session_id ON cbt_logs(session_id);
CREATE INDEX idx_cbt_logs_created_at ON cbt_logs(created_at);
```

**`bot_response` JSON 구조**:
```json
{
  "empathy": "...",
  "detected_distortion": "흑백사고",
  "analysis": "...",
  "socratic_question": "...",
  "alternative_thought": "...",
  "emotion": "sad"
}
```

**관찰점**:
- `cbt_logs` 1행 = "사용자 1턴 + 봇 1답변" (메시지 단위 분리 안 됨)
- session_id가 클라이언트 발급 + 서버 발급(마음일기 트리거) 혼재
- embedding은 저장만, 검색 사용처 없음

### 1.2 `weekly_reports` (이번 마이그레이션에서 제외)

```sql
CREATE TABLE weekly_reports (
    report_id        BIGSERIAL PRIMARY KEY,
    user_id          TEXT NOT NULL,
    start_date       DATE NOT NULL,
    end_date         DATE NOT NULL,
    report_title     TEXT,
    report_content   TEXT,
    emotions_summary JSONB,
    created_at       TIMESTAMP DEFAULT NOW()
);
```

**제외 사유**: 프론트에서 사용 중이지 않음. 향후 필요 시 별도 spec으로 도입.

### 1.3 (참고) `welfare_services` — 복지 검색 RAG 테이블

신규 프로젝트에서 복지 검색 기능 자체가 폐지됨. **이 spec에서 다루지 않음**.

---

## 2. 현재 모놀리식(MySQL)에 이미 있는 챗봇 관련 테이블

**없음**. `com.caring.domain` 하위에 chatbot/cbt/doran 패키지가 존재하지 않으므로 entity·테이블 모두 없음.

→ **신규 테이블 생성만 필요. 기존 테이블 변경 없음.**

(참고로 모놀리식에는 OpenAI 기반의 `weekly_emotion_report` 테이블이 별도로 있는데, 이건 음성 일기 감정 분석의 주간 코멘트용으로 도란이 weekly_reports와 완전히 별개의 데이터다.)

---

## 3. 신규 테이블 (MySQL)

### 3.1 `chat_session`

```sql
CREATE TABLE chat_session (
    id           CHAR(36)    NOT NULL PRIMARY KEY,    -- UUID v4
    user_id      BIGINT      NOT NULL,
    created_date DATETIME(6) NOT NULL,
    modified_date DATETIME(6) NOT NULL,

    CONSTRAINT fk_chat_session_user
        FOREIGN KEY (user_id) REFERENCES user(id)
        ON DELETE CASCADE
);

CREATE INDEX idx_chat_session_user_modified
    ON chat_session(user_id, modified_date DESC);
```

**설계 근거**:
- **PK CHAR(36) UUID v4**: IDOR 방어 (Long auto-increment 유추 위험 회피)
- **`user_id` FK**: 모놀리식 다른 도메인과 동일 패턴
- **`modified_date`**: 메시지 추가 시 갱신 → 세션 목록 최신순 정렬에 사용
- **`(user_id, modified_date DESC)` 복합 인덱스**: 세션 목록 조회 (`GET /sessions`) 최적화
- **CASCADE on user delete**: 사용자 탈퇴 시 자동 정리

**JPA 엔티티**:
```java
@Entity
@Table(name = "chat_session", indexes = {
    @Index(name = "idx_chat_session_user_modified",
           columnList = "user_id, modifiedDate DESC")
})
public class ChatSession extends BaseTimeEntity {

    @Id
    @Column(columnDefinition = "CHAR(36)")
    private String id;   // UUID v4 — @PrePersist에서 생성

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
    }
}
```

### 3.2 `chat_message`

```sql
CREATE TABLE chat_message (
    id                BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    session_id        CHAR(36)     NOT NULL,
    user_input        TEXT,                              -- 사용자 발화 (마음일기 트리거의 첫 봇 메시지에는 NULL 가능)
    bot_response      JSON         NOT NULL,             -- 도란이 응답 (6개 필드)
    voice_id          BIGINT       NULL,                 -- 음성 채팅 또는 마음일기 트리거 시
    origin            VARCHAR(16)  NOT NULL,             -- USER_TEXT / USER_VOICE / MIND_DIARY

    -- 사용자 피드백 (진짜 마음 입력)
    feedback_emotion  VARCHAR(16)  NULL,                 -- happy/sad/neutral/angry/anxiety/surprise
    feedback_detail   TEXT         NULL,
    feedback_at       DATETIME(6)  NULL,

    created_date      DATETIME(6)  NOT NULL,
    modified_date     DATETIME(6)  NOT NULL,

    CONSTRAINT fk_chat_message_session
        FOREIGN KEY (session_id) REFERENCES chat_session(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_chat_message_voice
        FOREIGN KEY (voice_id) REFERENCES voice(id)
        ON DELETE SET NULL
);

CREATE INDEX idx_chat_message_session_created
    ON chat_message(session_id, created_date DESC);
CREATE INDEX idx_chat_message_voice
    ON chat_message(voice_id);
```

**설계 근거**:
- **PK BIGINT**: 메시지 수가 많아 효율 우선 (외부 노출은 세션 단위로 충분)
- **`bot_response JSON`**: 6개 필드를 정규화하지 않음 (LLM이 일부 필드 누락하면 NULL 처리 복잡, 기존 도란이도 jsonb로 검증된 패턴)
- **`voice_id` FK nullable**:
  - 텍스트 채팅 → NULL
  - 음성 채팅 → 클라이언트 전달 voiceId
  - 마음일기 트리거 → 트리거를 발화한 voice의 id (역추적용)
  - `s3_url` 직접 저장 대신 FK로 간접 참조 (presigned URL은 그때그때 발급)
- **`origin`**: 메시지 출처 추적 (분석·디버깅용)
  - `USER_TEXT` — 텍스트 채팅
  - `USER_VOICE` — 음성 채팅
  - `MIND_DIARY` — 마음일기 트리거 (사용자 발화 없이 봇이 먼저 보낸 메시지)
- **피드백 컬럼 3개**: 별도 테이블 분리 안 함 (1:1 관계엔 과한 정규화)
- **CASCADE on session delete**: 세션 삭제 시 메시지 자동 삭제
- **`SET NULL on voice delete`**: voice가 삭제돼도 채팅 기록 자체는 보존

**JPA 엔티티**:
```java
@Entity
@Table(name = "chat_message")
public class ChatMessage extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false,
                columnDefinition = "CHAR(36)")
    private ChatSession session;

    @Column(columnDefinition = "TEXT")
    private String userInput;

    @Column(name = "bot_response", nullable = false, columnDefinition = "JSON")
    @Convert(converter = JsonNodeConverter.class)   // String ↔ JsonNode
    private JsonNode botResponse;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "voice_id")
    private Voice voice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MessageOrigin origin;

    @Column(length = 16)
    @Enumerated(EnumType.STRING)
    private DoranEmotion feedbackEmotion;

    @Column(columnDefinition = "TEXT")
    private String feedbackDetail;

    @Column
    private LocalDateTime feedbackAt;

    public void applyFeedback(DoranEmotion emotion, String detail) {
        this.feedbackEmotion = emotion;
        this.feedbackDetail = detail;
        this.feedbackAt = LocalDateTime.now();
    }
}

public enum MessageOrigin {
    USER_TEXT, USER_VOICE, MIND_DIARY
}

public enum DoranEmotion {
    HAPPY, SAD, NEUTRAL, ANGRY, ANXIETY, SURPRISE
}
```

---

## 4. 매핑 테이블 (기존 → 신규)

### 4.1 `cbt_logs` 컬럼 매핑

| 기존 컬럼 | 신규 위치 | 변환 |
|---|---|---|
| `log_id BIGSERIAL` | `chat_message.id BIGINT` | 그대로 (재발급) |
| `user_id TEXT` | `chat_session.user_id BIGINT FK` | 외부 ID → 내부 User PK 매핑 (이번엔 데이터 미이전이라 적용 X) |
| `session_id TEXT(6)` | `chat_session.id CHAR(36)` | 신규 UUID 발급 |
| `user_input TEXT` | `chat_message.user_input TEXT` | 그대로 |
| `bot_response JSONB` | `chat_message.bot_response JSON` | MySQL JSON 타입으로 변환 |
| `embedding VECTOR(1024)` | **제거** | — |
| `s3_url TEXT` | `chat_message.voice_id BIGINT FK` | URL → Voice ID 매핑 (이번엔 데이터 미이전이라 적용 X) |
| `created_at TIMESTAMP` | `chat_message.created_date DATETIME(6)` | 그대로 |

### 4.2 신규 추가 컬럼 (기존에 없음)

| 컬럼 | 위치 | 도입 사유 |
|---|---|---|
| `chat_session.modified_date` | session | 세션 목록 정렬용 (메시지 추가 시 갱신) |
| `chat_message.origin` | message | 메시지 출처 추적 (분석·디버깅) |
| `chat_message.feedback_emotion` | message | 사용자 진짜 마음 피드백 |
| `chat_message.feedback_detail` | message | 피드백 상세 |
| `chat_message.feedback_at` | message | 피드백 시각 |

---

## 5. 데이터 이전 전략

### 5.1 결정: **기존 cbt_logs 데이터는 이전하지 않음**

**근거**:
- PostgreSQL → MySQL 이전 + 임베딩·s3_url 컬럼 변환·session_id UUID 재발급 등 변환 비용 큼
- 신규 프로젝트 출시 일정과 맞물려 사용자 채팅 기록 보존 우선순위 낮음
- 기존 도란이 사용자에게 "신규 출시로 이전 채팅이 초기화됩니다" 안내로 대응

**실행**:
- 기존 PostgreSQL `cbt_logs` 덤프 백업만 보관 (`pg_dump` → S3 Glacier)
- 마이그레이션 완료 후 30일 경과 시 운영 RDS 폐기

### 5.2 만약 이전이 필요해진다면 (참고)

```python
# 마이그레이션 스크립트 개요 (실제 작성 시 별도 spec)
for old_session_id in distinct_session_ids:
    new_uuid = uuid4()
    user_pk = lookup_user_by_external_id(old_user_id)
    insert chat_session (id=new_uuid, user_id=user_pk, ...)

    for log in cbt_logs where session_id=old_session_id:
        voice_id = lookup_voice_by_s3_url(log.s3_url) if log.s3_url else None
        insert chat_message (
            session_id=new_uuid,
            user_input=log.user_input,
            bot_response=log.bot_response,  # JSONB → JSON
            voice_id=voice_id,
            origin=infer_origin(log),       # s3_url 유무로 추론
            created_date=log.created_at
        )
        # embedding은 폐기
```

---

## 6. 인덱스 전략

| 테이블 | 인덱스 | 사용 쿼리 |
|---|---|---|
| `chat_session` | `idx_chat_session_user_modified` (user_id, modified_date DESC) | `GET /sessions` 최신순 목록 |
| `chat_message` | `idx_chat_message_session_created` (session_id, created_date DESC) | `GET /history/{sessionId}` 페이징 |
| `chat_message` | `idx_chat_message_voice` (voice_id) | 향후 voice 기반 역추적 |
| `chat_message` | (PK BIGINT) | `PUT /messages/{messageId}/feedback` 단건 조회 |

**의도적으로 추가하지 않은 인덱스**:
- `chat_message.feedback_emotion` 인덱스 — 분석 통계용 ad-hoc 쿼리는 풀스캔으로 충분 (사용자당 메시지 수는 많지 않음)
- `chat_message.origin` 인덱스 — 동일 사유

---

## 7. 데이터 정합성 / 제약

### 7.1 권한 검증 패턴

```java
// 모든 메시지·피드백 조회/수정 전:
ChatSession session = chatSessionAdaptor.queryById(sessionId);
if (!session.getUser().getId().equals(currentUser.getId())) {
    throw ChatbotHandler.NO_PERMISSION;
}
```

### 7.2 카스케이드

- `User` 삭제 → `chat_session` cascade → `chat_message` cascade
- `chat_session` 직접 삭제 → `chat_message` cascade
- `Voice` 삭제 → `chat_message.voice_id = NULL` (채팅 기록은 보존)

### 7.3 enum 값 일관성

| 위치 | 허용 값 |
|---|---|
| `chat_message.feedback_emotion` (DB) | `happy`, `sad`, `neutral`, `angry`, `anxiety`, `surprise` |
| `bot_response.emotion` (JSON) | 동일 |
| `bot_response.detected_distortion` (JSON) | 11개 카테고리 + `위기 상황` + `없음` |

DB 측 enum 강제는 하지 않음 (Java enum + Gemini `responseSchema`로 두 단계 보장).

---

## 8. ddl-auto 적용 안전성

모놀리식은 `spring.jpa.hibernate.ddl-auto: update`를 사용한다. 신규 테이블은 안전하게 생성된다:

- `chat_session`, `chat_message` 모두 **신규 테이블** → `CREATE TABLE`만 발생
- 기존 테이블 변경 없음 (`user`, `voice` 테이블에 컬럼 추가 X)
- `Voice` 엔티티에 `@OneToMany List<ChatMessage>` 같은 역참조는 추가하지 않음 (단방향 FK만) → Voice 도메인 쪽 스키마 변경 0

**JPA `@Index` 어노테이션의 한계**:
- Hibernate `ddl-auto: update`는 `@Index`로 정의한 인덱스를 **테이블 생성 시점에만** 만들어준다
- 이미 만들어진 테이블에 인덱스를 추가하려면 수동 ALTER 필요
- 이번엔 신규 테이블이라 자동 생성으로 충분

---

## 9. 향후 변경 시 영향 범위

| 변경 시나리오 | 영향 |
|---|---|
| 임베딩 다시 도입 | `chat_message`에 컬럼 추가 또는 별도 `chat_message_embedding` 테이블 신설 |
| 핵심 신념 분석 추가 | 별도 `core_belief` 테이블 + `chat_message_id` FK |
| 주간 리포트 도입 | `chat_weekly_report` 테이블 신설 (이번 spec엔 미포함) |
| 메시지 첨부 파일 (이미지 등) | `chat_message_attachment` 테이블 신설 |

---

## 10. 참고 자료

- 기존 schema 출처: `E:\Caring_Lambda\chatbot\repository\chat_repository.py`, `report_repository.py`
- 모놀리식 엔티티 컨벤션: `src/main/java/com/caring/domain/voice/entity/Voice.java`
- BaseTimeEntity: `src/main/java/com/caring/common/BaseTimeEntity.java`
- 설계서: `docs/superpowers/specs/2026-05-09-doran-migration-design.md`
