# 도란이 챗봇 모놀리식 마이그레이션 설계서

**작성일**: 2026-05-09
**대상**: `Caring_Lambda/chatbot` → `CARING-Server-Monolithic`

## 0. 요약

AWS Lambda 기반 FastAPI 챗봇(도란이)을 Spring Boot 모놀리식 서버로 통합한다.
SQS 비동기 파이프라인은 Spring `ApplicationEvent` + `@Async`로 대체하고,
PostgreSQL+pgvector는 단일 RDS(MySQL)로 흡수한다. 임베딩·복지검색·주간 리포트는 제외한다.

---

## 1. 배경

### 1.1 기존 구조 (Caring_Lambda)
- **AWS Lambda** + FastAPI(Mangum) 기반 서버리스
- **PostgreSQL + pgvector** (1024차원 Bedrock Titan 임베딩 저장)
- **SQS 2개** 큐:
  - `CBT_LOG_SQS_URL` — 대화 로그 비동기 저장
  - `DIARY_TO_CHATBOT_SQS_URL` — 마음일기 분석 완료 → 도란이 선제 대화
- **Vertex AI Gemini 2.5 Pro** — CBT 리프레이밍 LLM

### 1.2 모놀리식 통합 동기
- 분산 시스템 운영 부담 (Lambda × FastAPI × SQS × pgvector × Vertex)
- 마음일기 분석 후속 작업이 SQS 왕복 없이 같은 프로세스 안에서 처리 가능
- DB 단일 RDS 통합으로 운영·모니터링 단순화

### 1.3 마이그레이션 범위

**포함**:
- CBT 리프레이밍 채팅 (텍스트/음성)
- 마음일기 → 도란이 선제 대화 자동 트리거
- 세션·메시지 CRUD

**제외 (이 spec 범위 밖)**:
- 복지/구인 정보 RAG 검색 — 신규 프로젝트 방향 변경으로 폐지
- 주간/월간 마음 소설 리포트 — 프론트 미사용, YAGNI
- 임베딩 생성·저장 — 활용처 없음, 필요 시 별도 spec

---

## 2. 아키텍처 개요

### 2.1 시스템 다이어그램

```
[클라이언트]
  │
  │ ① 음성 일기 업로드
  ▼
[Voice 도메인]
  ├─ S3 PUT (presigned URL)
  ├─ POST /voices → Voice INSERT
  └─ GeminiVoiceAnalyzer.analyzeAsync()  ← @Async (트랜잭션 내부)
       │
       │ ② 분석 완료 후 ApplicationEvent 발행
       ▼
[ApplicationEventPublisher]
       │
       │ VoiceAnalysisCompletedEvent(voiceId)
       ▼
[Chatbot 도메인]  ← @TransactionalEventListener(AFTER_COMMIT) + @Async
   MindDiaryChatTrigger
   ├─ Voice + VoiceComposite + VoiceEmotionLabel 조회
   ├─ MindDiaryPrompt 조립
   ├─ GeminiChatbotClient 호출 (gemini-2.5-pro)
   ├─ ChatSession + ChatMessage INSERT
   └─ (옵션) FCM 푸시 알림

[클라이언트] ◄──── ③ 푸시 수신 후 GET /chatbot/sessions로 새 세션 확인
```

```
[클라이언트]
  │ ④ 텍스트/음성 채팅 (동기)
  ▼
ChatbotApiController
  POST /chatbot/sessions                           # 세션 생성
  POST /chatbot/reframing       { sessionId, ... } # 텍스트
  POST /chatbot/voice-reframing { sessionId, voiceId, ... }  # 음성
       │
       ▼
SendChatMessageUseCase
   ├─ ChatSession 권한 검증 (sessionId.user_id == 요청자)
   ├─ 최근 5턴 조회 → ReframingPrompt 조립
   ├─ GeminiChatbotClient 호출 (responseSchema enum 강제)
   ├─ ChatMessage INSERT (user_input + bot_response JSON)
   └─ ChatMessageResponse 반환
```

### 2.2 핵심 결정 사항 (Architecture Decision Record)

| ADR | 결정 | 이유 |
|---|---|---|
| ADR-1 | 임베딩 제거 | 활용 코드 없음, MySQL 단독으로 단순화 |
| ADR-2 | SQS → ApplicationEvent + @Async | 모놀리식 내부 비동기로 충분, 외부 큐 불필요 |
| ADR-3 | `@TransactionalEventListener(AFTER_COMMIT)` | 분석 결과 커밋 후 트리거 → 유령 읽기 방지 |
| ADR-4 | Gemini 2.5 Pro REST API | 기존 `GeminiVoiceAnalyzer` 패턴 재활용, GCP service account 불필요 |
| ADR-5 | UUID v4 PK | Long auto-increment의 IDOR 노출 위험 회피, 채팅 데이터 보안 강화 |
| ADR-6 | 세션 생성/삭제 명시 API | 클라이언트 임의 sessionId 충돌·권한 모호성 제거 |
| ADR-7 | 피드백 컬럼 별도 (오버라이딩 X) | 봇 분석 vs 사용자 라벨 비교 데이터 보존 |
| ADR-8 | `responseSchema` enum 강제 | 11개 인지왜곡 카테고리·6대 감정 100% 매핑 안전성 |
| ADR-9 | hard delete | 채팅 프라이버시, soft delete 이점 없음 |

---

## 3. 패키지 / 컴포넌트 구조

```
com.caring/
├── api/chatbot/
│   ├── controller/
│   │   └── ChatbotApiController          # 모든 /chatbot/* 엔드포인트
│   ├── dto/
│   │   ├── CreateSessionResponse         # { sessionId }
│   │   ├── ReframingRequest              # { sessionId, userInput, emotion? }
│   │   ├── VoiceReframingRequest         # { sessionId, userInput, voiceId? }
│   │   ├── ReframingResponse             # { messageId, empathy, detected_distortion, ... }
│   │   ├── FeedbackRequest               # { emotion, detail }
│   │   ├── SessionListResponse           # { sessions: [...] }
│   │   ├── ChatSessionItem               # { sessionId, lastMessage, lastUpdated, distortionTags, emotion }
│   │   ├── ChatHistoryResponse           # { sessionId, messages: [...], totalPage, currentPage }
│   │   └── ChatMessageItem               # { role, content, timestamp, ... feedbackEmotion?, feedbackDetail? }
│   └── service/
│       ├── CreateChatSessionUseCase
│       ├── DeleteChatSessionUseCase
│       ├── SendReframingMessageUseCase            # 텍스트
│       ├── SendVoiceReframingMessageUseCase       # 음성
│       ├── GetChatSessionsUseCase
│       ├── GetChatHistoryUseCase
│       └── UpdateMessageFeedbackUseCase
│
├── domain/chatbot/
│   ├── entity/
│   │   ├── ChatSession                   # PK CHAR(36) UUID
│   │   ├── ChatMessage                   # PK BIGINT, FK sessionId UUID
│   │   └── DoranEmotion (enum)           # HAPPY, SAD, NEUTRAL, ANGRY, ANXIETY, SURPRISE
│   ├── repository/
│   │   ├── ChatSessionRepository
│   │   └── ChatMessageRepository
│   ├── adaptor/
│   │   ├── ChatSessionAdaptor / Impl     # query, save, delete
│   │   └── ChatMessageAdaptor / Impl
│   ├── service/
│   │   └── ChatbotDomainService          # 권한 검증, 메시지 저장 헬퍼
│   ├── event/
│   │   └── MindDiaryChatTrigger          # @TransactionalEventListener
│   └── exception/
│       ├── ChatbotErrorStatus            # SESSION_NOT_FOUND, NO_PERMISSION, MESSAGE_NOT_FOUND, INVALID_FEEDBACK
│       └── ChatbotHandler
│
├── common/event/
│   └── VoiceAnalysisCompletedEvent       # record(Long voiceId)
│
└── infra/ai/gemini/
    ├── GeminiChatbotClient               # 도란이 전용 (gemini-2.5-pro + responseSchema)
    ├── prompts/
    │   ├── ReframingPrompt               # 텍스트 채팅
    │   ├── VoiceReframingPrompt          # 음성 채팅
    │   ├── MindDiaryPrompt               # 마음일기 트리거
    │   └── EmotionStrategies             # 감정별 전략 블록 (기존 emotion_strategies.py 이식)
    └── (기존) GeminiVoiceAnalyzer        # 음성 감정 분석 (gemini-2.5-flash) — 그대로 유지
```

**클라이언트 분리 이유** (`GeminiVoiceAnalyzer` ≠ `GeminiChatbotClient`):
- 모델이 다름 (flash vs pro), 응답 스키마가 다름, 프롬프트 책임이 다름
- 한 클라이언트에 다목적 메서드를 두면 책임이 비대해짐
- 공통 WebClient 설정만 `GeminiClientConfig`로 추출

---

## 4. 데이터 모델 (요약)

상세 스키마·인덱스·마이그레이션 전략은 별도 문서 참고:
**`docs/superpowers/specs/2026-05-09-doran-data-model.md`**

신규 테이블 2개:
- `chat_session` (PK CHAR(36) UUID, FK user_id BIGINT)
- `chat_message` (PK BIGINT, FK session_id CHAR(36), FK voice_id BIGINT?, JSON bot_response, 피드백 컬럼 3개)

---

## 5. API 명세

모든 엔드포인트 인증: `Authorization: Bearer {accessToken}`
응답 래퍼: `ApiResponseDto<T>` = `{ isSuccess, code, message, result }`

### 5.1 세션 생성

```
POST /v1/api/chatbot/sessions
```

**Request body**: 없음 (인증 헤더만)

**Response**:
```json
{
  "isSuccess": true,
  "code": "COMMON200",
  "message": "성공",
  "result": {
    "sessionId": "550e8400-e29b-41d4-a716-446655440000"
  }
}
```

### 5.2 세션 삭제

```
DELETE /v1/api/chatbot/sessions/{sessionId}
```

**동작**: ChatSession + 연결된 모든 ChatMessage 삭제 (cascade hard delete)

**권한 검증 실패**: `403 NO_PERMISSION`
**없는 세션**: `404 SESSION_NOT_FOUND`

### 5.3 세션 목록

```
GET /v1/api/chatbot/sessions
```

**Response**:
```json
{
  "result": {
    "sessions": [
      {
        "sessionId": "550e8400-...",
        "lastMessage": "오늘 너무 힘들었어요",
        "lastUpdated": "2026-05-09T14:30:00",
        "distortionTags": ["흑백사고"],
        "emotion": "sad"
      }
    ]
  }
}
```

각 세션의 마지막 메시지 1건만 미리보기 + 사용자 피드백이 있으면 피드백 감정으로 표시.

### 5.4 채팅 상세 조회

```
GET /v1/api/chatbot/history/{sessionId}?page=1
```

**페이징**: `size=20` 고정, `page` 1-based, 최신순으로 잘라서 시간순 재정렬

**Response**:
```json
{
  "result": {
    "sessionId": "550e8400-...",
    "messages": [
      {
        "messageId": 101,
        "role": "user",
        "content": "오늘 너무 힘들었어요",
        "timestamp": "2026-05-09T14:30:00",
        "voiceId": null
      },
      {
        "messageId": 101,
        "role": "assistant",
        "empathy": "오늘 하루 많이 힘드셨군요...",
        "detected_distortion": "흑백사고",
        "analysis": "...",
        "socratic_question": "...",
        "alternative_thought": "...",
        "emotion": "sad",
        "feedbackEmotion": "anxiety",
        "feedbackDetail": "사실 슬픔보다 불안이 컸어요",
        "feedbackAt": "2026-05-09T14:35:00",
        "timestamp": "2026-05-09T14:30:05"
      }
    ],
    "totalPage": 3,
    "currentPage": 1
  }
}
```

> `messageId`는 user/assistant 한 쌍이 동일 ID 공유 (DB row 1개에서 두 메시지 파생).

### 5.5 텍스트 채팅

```
POST /v1/api/chatbot/reframing
```

**Request**:
```json
{
  "sessionId": "550e8400-...",
  "userInput": "시험을 망쳐서 너무 우울해요",
  "emotion": "sad"  // optional, 클라이언트가 감정 힌트 줄 수 있음
}
```

**Response (도란이 응답 형상 100% 유지)**:
```json
{
  "result": {
    "messageId": 102,
    "empathy": "시험 결과로 마음이 많이 무거우셨겠어요...",
    "detected_distortion": "흑백사고",
    "analysis": "한 번의 시험 결과로 모든 것을 판단하시는 모습이...",
    "socratic_question": "이번 시험 외에 다른 영역에서는 어떠세요?",
    "alternative_thought": "한 번의 결과가 당신 전체를 정의하지 않아요.",
    "emotion": "sad"
  }
}
```

### 5.6 음성 채팅

```
POST /v1/api/chatbot/voice-reframing
```

**Request**:
```json
{
  "sessionId": "550e8400-...",
  "userInput": "오늘 정말 힘들었어",   // STT 결과
  "voiceId": 42                       // 모놀리식 Voice 엔티티 ID
}
```

**서버 처리**:
1. `voiceId`로 Voice + VoiceComposite + VoiceEmotionLabel 조회
2. 모놀리식 감정 분석 결과를 `VoiceReframingPrompt`에 주입 (Hume의 prosody 궤적 대신 Gemini의 6대 감정 분포 + 세부 감정 top 3 사용)
3. 응답은 텍스트 채팅과 동일 형상

**voiceId 없거나 분석 미완료 시**: 텍스트 모드로 폴백 (감정 분석 정보 없이 진행)

### 5.7 피드백 (진짜 마음 입력)

```
PUT /v1/api/chatbot/messages/{messageId}/feedback
```

**Request**:
```json
{
  "emotion": "anxiety",
  "detail": "사실 슬픔보다 불안이 컸어요"   // optional
}
```

**Response**:
```json
{
  "result": {
    "messageId": 102,
    "feedbackEmotion": "anxiety",
    "feedbackDetail": "사실 슬픔보다 불안이 컸어요",
    "feedbackAt": "2026-05-09T14:35:00"
  }
}
```

**제약**:
- `emotion`은 `happy/sad/neutral/angry/anxiety/surprise` 중 하나 (그 외 `400 INVALID_FEEDBACK`)
- 같은 messageId에 재호출 가능 → 마지막 값으로 덮어쓰기 (`feedback_at` 갱신)
- 권한 검증: 메시지가 속한 ChatSession의 owner 확인

---

## 6. 비동기 트리거 흐름

### 6.1 이벤트 정의

```java
// com.caring.common.event
public record VoiceAnalysisCompletedEvent(Long voiceId) {}
```

### 6.2 발행 (Voice 도메인)

`GeminiVoiceAnalyzer.analyzeAsync()` 끝부분에서:
```java
voiceCompositeAdaptor.save(composite);
voiceEmotionLabelAdaptor.saveAll(labels);
voice.markAnalysisCompleted();
voiceRepository.save(voice);

eventPublisher.publishEvent(new VoiceAnalysisCompletedEvent(voice.getId()));
```

### 6.3 구독 (Chatbot 도메인)

```java
@Component
@RequiredArgsConstructor
public class MindDiaryChatTrigger {

    private final VoiceAdaptor voiceAdaptor;
    private final VoiceCompositeAdaptor voiceCompositeAdaptor;
    private final VoiceEmotionLabelAdaptor voiceEmotionLabelAdaptor;
    private final ChatbotDomainService chatbotDomainService;
    private final GeminiChatbotClient geminiChatbotClient;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onVoiceAnalysisCompleted(VoiceAnalysisCompletedEvent event) {
        try {
            Voice voice = voiceAdaptor.queryById(event.voiceId());
            VoiceComposite composite = voiceCompositeAdaptor
                    .queryByVoiceIds(List.of(voice.getId()))
                    .stream().findFirst().orElse(null);
            List<VoiceEmotionLabel> labels = voiceEmotionLabelAdaptor
                    .findByVoiceId(voice.getId());

            String prompt = MindDiaryPrompt.build(voice, composite, labels);
            DoranResponse response = geminiChatbotClient.generate(prompt);

            chatbotDomainService.createSessionWithFirstMessage(
                    voice.getUser(), voice.getId(), response);
            // (옵션) FCM 푸시 발송
        } catch (Exception e) {
            log.error("MindDiaryChatTrigger 실패: voiceId={}", event.voiceId(), e);
            // silent fail — 일기 자체는 이미 저장됨
        }
    }
}
```

### 6.4 보장사항

- **AFTER_COMMIT**: 분석 결과 트랜잭션 커밋 후 실행 → 트리거 안에서 분석 결과 조회 시 NULL 위험 없음
- **@Async**: 사용자 응답 시간에 영향 없음 (감정 분석 자체도 이미 비동기였으므로 추가 지연 없음)
- **silent fail**: 트리거 실패해도 일기·분석 결과는 보존, 사용자 경험 영향 없음
- **idempotency**: 같은 voiceId로 두 번 트리거되면 ChatSession 두 개 생성됨. Voice 도메인이 중복 발행하지 않으므로 신경 쓸 일 없음 (단위 테스트에서만 검증)

---

## 7. LLM 통합 (`GeminiChatbotClient`)

### 7.1 호출 스펙

```
POST https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-pro:generateContent
?key={GEMINI_API_KEY}
```

**Request body**:
```json
{
  "contents": [{ "role": "user", "parts": [{ "text": "<프롬프트 전체>" }] }],
  "generationConfig": {
    "temperature": 0.7,
    "maxOutputTokens": 2048,
    "responseMimeType": "application/json",
    "responseSchema": { ... }   // 6.2 참고
  }
}
```

### 7.2 responseSchema (인지 왜곡·감정 enum 강제)

```json
{
  "type": "OBJECT",
  "properties": {
    "empathy": { "type": "STRING" },
    "detected_distortion": {
      "type": "STRING",
      "enum": [
        "흑백사고", "선택적 추상", "자의적 추론", "과잉일반화", "확대/축소",
        "개인화", "정서적 추론", "긍정 격하", "파국화", "잘못된 별칭 붙이기",
        "긍정 정서 강화", "위기 상황", "없음"
      ]
    },
    "analysis": { "type": "STRING" },
    "socratic_question": { "type": "STRING" },
    "alternative_thought": { "type": "STRING" },
    "top_emotion": {
      "type": "STRING",
      "enum": ["happy", "sad", "neutral", "angry", "anxiety", "surprise"]
    }
  },
  "required": [
    "empathy", "detected_distortion", "analysis",
    "socratic_question", "alternative_thought", "top_emotion"
  ]
}
```

### 7.3 프롬프트 (기존 도란이 거의 그대로 이식)

| 프롬프트 | 출처 | 변경 사항 |
|---|---|---|
| `ReframingPrompt` (텍스트) | `prompts/reframing.py` `REFRAMING_PROMPT_TEMPLATE` | 그대로 |
| `VoiceReframingPrompt` (음성) | `prompts/reframing.py` `VOICE_REFRAMING_PROMPT_TEMPLATE` | Hume 궤적 부분 → 모놀리식 6대 감정 분포로 대체 |
| `MindDiaryPrompt` (트리거) | `prompts/mind_diary.py` `MIND_DIARY_PROMPT_TEMPLATE` | 동일 (감정 컨텍스트만 모놀리식 데이터로 변환) |
| `EmotionStrategies` | `prompts/emotion_strategies.py` | 그대로 (감정별 CBT 전략 블록) |

**음성 채팅의 감정 컨텍스트 변환** (Hume → Gemini):
- `prosody.summary` 53개 감정 → `VoiceComposite.emotionDistributionBps` 6개 카테고리(bps 비율)
- `prosody.utterances` 발화별 궤적 → **제거** (Gemini 분석엔 시간 정보 없음)
- 세부 감정 top 3 → `VoiceEmotionLabel`에서 intensity 상위 3개

### 7.4 에러 처리

| 상황 | 처리 |
|---|---|
| 5xx / 429 | 지수 백오프 1회 재시도 후 실패 → 폴백 응답 |
| 타임아웃 (30초) | 재시도 없이 폴백 응답 |
| `responseSchema` 위반 (이론상 거의 발생 안 함) | Gemini가 자체 재생성 → 그래도 실패 시 폴백 |
| 폴백 응답 | `{empathy: "죄송해요, 잠시 생각이 꼬였나 봐요...", detected_distortion: "없음", ...}` |

---

## 8. 권한·보안

- **세션 권한**: 모든 세션·메시지·피드백 API에서 `ChatSession.userId == 요청자 user.id` 검증
- **UUID v4 PK**: IDOR 노출 위험 차단
- **JWT 표준 사용**: 모놀리식의 `@UserCode String username` 어노테이션 → `UserAdaptor.queryUserByUsername()`로 User 조회
- **위기 상황 응답**: 프롬프트 자체에 자살/자해/범죄 감지 시 안전 우선 안내 메시지(자살예방상담전화 109) 포함

---

## 9. 테스트 전략

| 레이어 | 대상 | 도구 |
|---|---|---|
| Unit | `ReframingPrompt.build()`, `MindDiaryPrompt.build()` | JUnit 5 |
| Unit | `GeminiChatbotClient` 응답 파싱 | MockWebServer |
| Unit | `MindDiaryChatTrigger` 이벤트 처리 | `@MockBean` + `@RecordApplicationEvents` |
| Unit | 권한 검증 (`UpdateMessageFeedbackUseCase` 등) | Mockito |
| Integration | UseCase + H2 + Mock LLM | `@SpringBootTest` |
| Manual | 실제 Gemini 2.5 Pro 호출 (응답 품질·11개 카테고리 정확도) | dev 환경 수동 |

**LLM은 항상 mock**: 비용 절감 + 결정론적 테스트.

---

## 10. 마이그레이션·호환성

### 10.1 기존 도란이 사용자 영향
- **기존 cbt_logs 데이터는 이전하지 않음** — 사용자별 채팅 기록은 0부터 시작
- 이유:
  - PostgreSQL → MySQL 이전 + 임베딩 컬럼 제거
  - session_id TEXT(6자리) → CHAR(36) UUID 매핑 어려움 (서버가 새 UUID 발급해야 함)
  - 신규 프로젝트 출시 일정과 맞물려 데이터 보존 가치보다 깔끔한 시작이 우선
- 데이터 백업: PostgreSQL 덤프만 보관 (필요 시 분석용)

### 10.2 클라이언트 영향
- **브레이킹 체인지**:
  1. 인증 헤더 필수 (기존: body의 `user_id`)
  2. `POST /sessions`로 명시적 세션 생성 필요
  3. 응답 본문이 `result` 안으로 한 단계 내려감
  4. 음성 채팅 `s3_url` → `voiceId`
- **유지**:
  - `/chatbot/reframing`, `/voice-reframing`, `/sessions`, `/history/{id}` 경로
  - 도란이 응답 6개 필드 (empathy, detected_distortion, analysis, socratic_question, alternative_thought, emotion)
  - `sessionId` 클라이언트 보유·재사용 패턴 (UUID로 형식만 변경)

### 10.3 인프라 정리
- 기존 Lambda 함수: 마이그레이션 완료 후 비활성화 (소스는 보관)
- SQS 큐 2개: 마이그레이션 완료 후 삭제
- pgvector RDS: 별도 삭제 (welfare_services 데이터도 폐기)

---

## 11. 향후 확장 (이 spec 범위 밖)

- **FCM 푸시 알림**: `MindDiaryChatTrigger`에서 트리거 완료 시 발송
- **임베딩 기반 분석**: 핵심 신념 분석 기능 도입 시 별도 spec
- **대화 통계**: 사용자별 인지왜곡 분포·감정 추세
- **봇 피드백 학습**: `feedback_emotion` 데이터로 프롬프트 개선

---

## 12. 참고

- 기존 코드 위치: `E:\Caring_Lambda\chatbot\`
- 데이터 모델 상세: `docs/superpowers/specs/2026-05-09-doran-data-model.md`
- 모놀리식 코딩 컨벤션: `.claude/CLAUDE.md`
