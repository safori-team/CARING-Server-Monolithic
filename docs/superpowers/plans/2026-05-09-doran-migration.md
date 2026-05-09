# 도란이 챗봇 마이그레이션 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Caring_Lambda(FastAPI/PostgreSQL)의 도란이 CBT 리프레이밍 챗봇을 모놀리식 Spring Boot 서버로 통합하고, 마음일기 분석 완료 → 도란이 선제 대화를 SQS 대신 ApplicationEvent 기반 비동기로 전환한다.

**Architecture:** `chatbot` 도메인 신규 생성 (`api.chatbot`, `domain.chatbot`). UUID v4를 PK로 쓰는 `chat_session` + `chat_message` 두 테이블. Gemini 2.5 Pro를 `com.google.genai.Client` SDK + `responseSchema` enum 강제로 호출. `GeminiVoiceAnalyzer`가 분석 완료 시 `VoiceAnalysisCompletedEvent`를 발행하고, `MindDiaryChatTrigger`가 `@TransactionalEventListener(AFTER_COMMIT) + @Async`로 구독해 첫 봇 메시지를 생성한다.

**Tech Stack:** Spring Boot 3.4.1 / Java 17 / JPA `ddl-auto: update` / MySQL / `com.google.genai.Client` (Gemini SDK) / Mockito + JUnit 5 + AssertJ / H2 통합 테스트

**Spec 참조:**
- 설계서: `docs/superpowers/specs/2026-05-09-doran-migration-design.md`
- 데이터 모델: `docs/superpowers/specs/2026-05-09-doran-data-model.md`

---

## File Structure (생성/수정 대상)

### 신규 생성
```
src/main/java/com/caring/
├── api/chatbot/
│   ├── controller/ChatbotApiController.java
│   ├── dto/
│   │   ├── CreateSessionResponse.java
│   │   ├── ChatSessionItemResponse.java
│   │   ├── SessionListResponse.java
│   │   ├── ReframingRequest.java
│   │   ├── VoiceReframingRequest.java
│   │   ├── ReframingResponse.java
│   │   ├── ChatHistoryResponse.java
│   │   ├── ChatMessageItemResponse.java
│   │   └── FeedbackRequest.java
│   └── service/
│       ├── CreateChatSessionUseCase.java
│       ├── DeleteChatSessionUseCase.java
│       ├── GetChatSessionsUseCase.java
│       ├── GetChatHistoryUseCase.java
│       ├── SendReframingMessageUseCase.java
│       ├── SendVoiceReframingMessageUseCase.java
│       └── UpdateMessageFeedbackUseCase.java
├── domain/chatbot/
│   ├── entity/
│   │   ├── ChatSession.java
│   │   ├── ChatMessage.java
│   │   ├── MessageOrigin.java          (enum)
│   │   └── DoranEmotion.java           (enum)
│   ├── repository/
│   │   ├── ChatSessionRepository.java
│   │   └── ChatMessageRepository.java
│   ├── adaptor/
│   │   ├── ChatSessionAdaptor.java
│   │   ├── ChatSessionAdaptorImpl.java
│   │   ├── ChatMessageAdaptor.java
│   │   └── ChatMessageAdaptorImpl.java
│   ├── service/
│   │   ├── ChatbotDomainService.java
│   │   └── ChatbotDomainServiceImpl.java
│   ├── event/
│   │   └── MindDiaryChatTrigger.java
│   └── exception/
│       └── ChatbotHandler.java
├── common/event/
│   └── VoiceAnalysisCompletedEvent.java
└── infra/ai/gemini/
    ├── GeminiChatbotClient.java
    └── prompts/
        ├── ReframingPrompt.java
        ├── VoiceReframingPrompt.java
        ├── MindDiaryPrompt.java
        ├── EmotionStrategies.java
        └── DoranResponse.java          (응답 DTO record)

src/test/java/com/caring/
├── domain/chatbot/
│   ├── entity/ChatSessionTest.java
│   ├── entity/ChatMessageTest.java
│   ├── adaptor/ChatSessionAdaptorImplTest.java
│   ├── adaptor/ChatMessageAdaptorImplTest.java
│   └── event/MindDiaryChatTriggerTest.java
├── api/chatbot/service/
│   ├── CreateChatSessionUseCaseTest.java
│   ├── DeleteChatSessionUseCaseTest.java
│   ├── GetChatSessionsUseCaseTest.java
│   ├── GetChatHistoryUseCaseTest.java
│   ├── SendReframingMessageUseCaseTest.java
│   ├── SendVoiceReframingMessageUseCaseTest.java
│   └── UpdateMessageFeedbackUseCaseTest.java
└── infra/ai/gemini/
    ├── GeminiChatbotClientTest.java
    └── prompts/
        ├── ReframingPromptTest.java
        ├── VoiceReframingPromptTest.java
        └── MindDiaryPromptTest.java
```

### 수정
```
src/main/java/com/caring/
├── common/exception/ErrorStatus.java        (chatbot 에러 코드 5개 추가)
└── infra/ai/gemini/GeminiVoiceAnalyzer.java (이벤트 발행 1줄 추가)
```

---

## Task 1: ErrorStatus 확장 + ChatbotHandler

**Files:**
- Modify: `src/main/java/com/caring/common/exception/ErrorStatus.java`
- Create: `src/main/java/com/caring/domain/chatbot/exception/ChatbotHandler.java`

- [ ] **Step 1: ErrorStatus enum에 chatbot 에러 5개 추가**

기존 voice 영역(4150-4199) 다음 영역(4200-4249)을 chatbot에 할당.

`ErrorStatus.java` 의 `VOICE_NO_PERMISSION` 라인 다음에 추가:

```java
    //chatbot(4200-4249)
    CHAT_SESSION_NOT_FOUND(BAD_REQUEST, 4200, "존재하지 않는 챗봇 세션입니다."),
    CHAT_SESSION_NO_PERMISSION(BAD_REQUEST, 4201, "챗봇 세션의 접근권한이 없습니다."),
    CHAT_MESSAGE_NOT_FOUND(BAD_REQUEST, 4202, "존재하지 않는 채팅 메시지입니다."),
    CHAT_MESSAGE_NO_PERMISSION(BAD_REQUEST, 4203, "채팅 메시지의 접근권한이 없습니다."),
    CHAT_FEEDBACK_INVALID_EMOTION(BAD_REQUEST, 4204, "유효하지 않은 감정 피드백 값입니다.");
```

마지막 enum 항목의 세미콜론을 콤마로 바꾸고, 위 5개를 추가한 뒤 마지막에 세미콜론을 둔다.

또한 주석 `// chatbot (4200-4249)`를 주석 영역 마지막에 추가.

- [ ] **Step 2: ChatbotHandler 작성**

```java
// src/main/java/com/caring/domain/chatbot/exception/ChatbotHandler.java
package com.caring.domain.chatbot.exception;

import com.caring.common.exception.BaseErrorCode;
import com.caring.common.exception.ErrorStatus;
import com.caring.common.exception.GeneralException;

public class ChatbotHandler extends GeneralException {

    public static final GeneralException SESSION_NOT_FOUND =
            new ChatbotHandler(ErrorStatus.CHAT_SESSION_NOT_FOUND);
    public static final GeneralException SESSION_NO_PERMISSION =
            new ChatbotHandler(ErrorStatus.CHAT_SESSION_NO_PERMISSION);
    public static final GeneralException MESSAGE_NOT_FOUND =
            new ChatbotHandler(ErrorStatus.CHAT_MESSAGE_NOT_FOUND);
    public static final GeneralException MESSAGE_NO_PERMISSION =
            new ChatbotHandler(ErrorStatus.CHAT_MESSAGE_NO_PERMISSION);
    public static final GeneralException FEEDBACK_INVALID_EMOTION =
            new ChatbotHandler(ErrorStatus.CHAT_FEEDBACK_INVALID_EMOTION);

    public ChatbotHandler(BaseErrorCode code) {
        super(code);
    }
}
```

- [ ] **Step 3: 컴파일 확인**

```bash
./gradlew compileJava -x test --quiet
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 커밋**

```bash
git add src/main/java/com/caring/common/exception/ErrorStatus.java \
        src/main/java/com/caring/domain/chatbot/exception/ChatbotHandler.java
git commit -m "feat(chatbot): add ErrorStatus codes and ChatbotHandler"
```

---

## Task 2: Enum (DoranEmotion, MessageOrigin)

**Files:**
- Create: `src/main/java/com/caring/domain/chatbot/entity/DoranEmotion.java`
- Create: `src/main/java/com/caring/domain/chatbot/entity/MessageOrigin.java`
- Create: `src/test/java/com/caring/domain/chatbot/entity/DoranEmotionTest.java`

- [ ] **Step 1: 실패 테스트 작성**

```java
// src/test/java/com/caring/domain/chatbot/entity/DoranEmotionTest.java
package com.caring.domain.chatbot.entity;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DoranEmotionTest {

    @Test
    void fromCode_validCodes_returnsEnum() {
        assertThat(DoranEmotion.fromCode("happy")).isEqualTo(DoranEmotion.HAPPY);
        assertThat(DoranEmotion.fromCode("sad")).isEqualTo(DoranEmotion.SAD);
        assertThat(DoranEmotion.fromCode("neutral")).isEqualTo(DoranEmotion.NEUTRAL);
        assertThat(DoranEmotion.fromCode("angry")).isEqualTo(DoranEmotion.ANGRY);
        assertThat(DoranEmotion.fromCode("anxiety")).isEqualTo(DoranEmotion.ANXIETY);
        assertThat(DoranEmotion.fromCode("surprise")).isEqualTo(DoranEmotion.SURPRISE);
    }

    @Test
    void fromCode_caseInsensitive() {
        assertThat(DoranEmotion.fromCode("HAPPY")).isEqualTo(DoranEmotion.HAPPY);
        assertThat(DoranEmotion.fromCode("Sad")).isEqualTo(DoranEmotion.SAD);
    }

    @Test
    void fromCode_invalid_throws() {
        assertThatThrownBy(() -> DoranEmotion.fromCode("joy"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getCode_returnsLowercaseName() {
        assertThat(DoranEmotion.HAPPY.getCode()).isEqualTo("happy");
        assertThat(DoranEmotion.ANXIETY.getCode()).isEqualTo("anxiety");
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
./gradlew test --tests "com.caring.domain.chatbot.entity.DoranEmotionTest" --quiet
```
Expected: FAIL — DoranEmotion 클래스 없음 (compile error)

- [ ] **Step 3: DoranEmotion 작성**

```java
// src/main/java/com/caring/domain/chatbot/entity/DoranEmotion.java
package com.caring.domain.chatbot.entity;

import java.util.Locale;

public enum DoranEmotion {
    HAPPY, SAD, NEUTRAL, ANGRY, ANXIETY, SURPRISE;

    public String getCode() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static DoranEmotion fromCode(String code) {
        if (code == null) {
            throw new IllegalArgumentException("emotion code is null");
        }
        return DoranEmotion.valueOf(code.toUpperCase(Locale.ROOT));
    }
}
```

- [ ] **Step 4: MessageOrigin 작성**

```java
// src/main/java/com/caring/domain/chatbot/entity/MessageOrigin.java
package com.caring.domain.chatbot.entity;

public enum MessageOrigin {
    USER_TEXT,    // 텍스트 채팅
    USER_VOICE,   // 음성 채팅 (voice_id 첨부)
    MIND_DIARY    // 마음일기 트리거 (사용자 발화 없이 봇이 먼저 보낸 메시지)
}
```

- [ ] **Step 5: 테스트 통과 확인**

```bash
./gradlew test --tests "com.caring.domain.chatbot.entity.DoranEmotionTest" --quiet
```
Expected: PASS

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/com/caring/domain/chatbot/entity/DoranEmotion.java \
        src/main/java/com/caring/domain/chatbot/entity/MessageOrigin.java \
        src/test/java/com/caring/domain/chatbot/entity/DoranEmotionTest.java
git commit -m "feat(chatbot): add DoranEmotion and MessageOrigin enums"
```

---

## Task 3: ChatSession 엔티티

**Files:**
- Create: `src/main/java/com/caring/domain/chatbot/entity/ChatSession.java`
- Create: `src/test/java/com/caring/domain/chatbot/entity/ChatSessionTest.java`

- [ ] **Step 1: 실패 테스트 작성**

```java
// src/test/java/com/caring/domain/chatbot/entity/ChatSessionTest.java
package com.caring.domain.chatbot.entity;

import com.caring.domain.user.entity.User;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class ChatSessionTest {

    @Test
    void create_assignsRandomUuid() {
        User user = User.builder().build();
        ChatSession s = ChatSession.create(user);
        assertThat(s.getId()).isNotBlank();
        assertThat(UUID.fromString(s.getId())).isNotNull();
    }

    @Test
    void create_uniqueIdsPerCall() {
        User user = User.builder().build();
        ChatSession s1 = ChatSession.create(user);
        ChatSession s2 = ChatSession.create(user);
        assertThat(s1.getId()).isNotEqualTo(s2.getId());
    }

    @Test
    void create_assignsUser() {
        User user = User.builder().build();
        ChatSession s = ChatSession.create(user);
        assertThat(s.getUser()).isSameAs(user);
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
./gradlew test --tests "com.caring.domain.chatbot.entity.ChatSessionTest" --quiet
```
Expected: FAIL — compile error

- [ ] **Step 3: ChatSession 엔티티 작성**

```java
// src/main/java/com/caring/domain/chatbot/entity/ChatSession.java
package com.caring.domain.chatbot.entity;

import com.caring.domain.common.entity.BaseTimeEntity;
import com.caring.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
@Table(name = "chat_session", indexes = {
        @Index(name = "idx_chat_session_user_modified",
               columnList = "user_id, lastModifiedDate DESC")
})
public class ChatSession extends BaseTimeEntity {

    @Id
    @Column(columnDefinition = "CHAR(36)")
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    public static ChatSession create(User user) {
        return ChatSession.builder()
                .id(UUID.randomUUID().toString())
                .user(user)
                .build();
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

```bash
./gradlew test --tests "com.caring.domain.chatbot.entity.ChatSessionTest" --quiet
```
Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/caring/domain/chatbot/entity/ChatSession.java \
        src/test/java/com/caring/domain/chatbot/entity/ChatSessionTest.java
git commit -m "feat(chatbot): add ChatSession entity with UUID PK"
```

---

## Task 4: ChatMessage 엔티티

**Files:**
- Create: `src/main/java/com/caring/domain/chatbot/entity/ChatMessage.java`
- Create: `src/test/java/com/caring/domain/chatbot/entity/ChatMessageTest.java`

- [ ] **Step 1: 실패 테스트 작성**

```java
// src/test/java/com/caring/domain/chatbot/entity/ChatMessageTest.java
package com.caring.domain.chatbot.entity;

import com.caring.domain.user.entity.User;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ChatMessageTest {

    private static final ObjectMapper OM = new ObjectMapper();

    @Test
    void applyFeedback_setsFeedbackFields() throws Exception {
        ChatSession session = ChatSession.create(User.builder().build());
        JsonNode botResponse = OM.readTree("{\"empathy\":\"hi\",\"emotion\":\"sad\"}");
        ChatMessage msg = ChatMessage.builder()
                .session(session)
                .userInput("hi")
                .botResponse(botResponse)
                .origin(MessageOrigin.USER_TEXT)
                .build();

        msg.applyFeedback(DoranEmotion.ANXIETY, "사실 불안이 더 컸어요");

        assertThat(msg.getFeedbackEmotion()).isEqualTo(DoranEmotion.ANXIETY);
        assertThat(msg.getFeedbackDetail()).isEqualTo("사실 불안이 더 컸어요");
        assertThat(msg.getFeedbackAt()).isNotNull();
    }

    @Test
    void applyFeedback_overwritesPrevious() throws Exception {
        ChatSession session = ChatSession.create(User.builder().build());
        JsonNode botResponse = OM.readTree("{}");
        ChatMessage msg = ChatMessage.builder()
                .session(session).botResponse(botResponse).origin(MessageOrigin.USER_TEXT).build();

        msg.applyFeedback(DoranEmotion.SAD, "first");
        msg.applyFeedback(DoranEmotion.HAPPY, "changed");

        assertThat(msg.getFeedbackEmotion()).isEqualTo(DoranEmotion.HAPPY);
        assertThat(msg.getFeedbackDetail()).isEqualTo("changed");
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
./gradlew test --tests "com.caring.domain.chatbot.entity.ChatMessageTest" --quiet
```
Expected: FAIL — compile error

- [ ] **Step 3: JsonNodeConverter 작성**

bot_response를 JsonNode로 다루기 위해 AttributeConverter 필요. `domain/chatbot/entity/` 안에 두기보다 공통 위치(`common`)도 가능하나, 도란이만 쓰므로 chatbot 도메인 내에 둔다.

```java
// src/main/java/com/caring/domain/chatbot/entity/JsonNodeConverter.java
package com.caring.domain.chatbot.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class JsonNodeConverter implements AttributeConverter<JsonNode, String> {

    private static final ObjectMapper OM = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(JsonNode attribute) {
        if (attribute == null) return null;
        return attribute.toString();
    }

    @Override
    public JsonNode convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) return null;
        try {
            return OM.readTree(dbData);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse JSON column: " + dbData, e);
        }
    }
}
```

- [ ] **Step 4: ChatMessage 엔티티 작성**

```java
// src/main/java/com/caring/domain/chatbot/entity/ChatMessage.java
package com.caring.domain.chatbot.entity;

import com.caring.domain.common.entity.BaseTimeEntity;
import com.caring.domain.voice.entity.Voice;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
@Table(name = "chat_message", indexes = {
        @Index(name = "idx_chat_message_session_created",
               columnList = "session_id, createdDate DESC"),
        @Index(name = "idx_chat_message_voice", columnList = "voice_id")
})
public class ChatMessage extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "chat_message_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false, columnDefinition = "CHAR(36)")
    private ChatSession session;

    @Column(columnDefinition = "TEXT")
    private String userInput;

    @Convert(converter = JsonNodeConverter.class)
    @Column(name = "bot_response", nullable = false, columnDefinition = "JSON")
    private JsonNode botResponse;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "voice_id")
    private Voice voice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MessageOrigin origin;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
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
```

- [ ] **Step 5: 테스트 통과 확인**

```bash
./gradlew test --tests "com.caring.domain.chatbot.entity.ChatMessageTest" --quiet
```
Expected: PASS

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/com/caring/domain/chatbot/entity/ChatMessage.java \
        src/main/java/com/caring/domain/chatbot/entity/JsonNodeConverter.java \
        src/test/java/com/caring/domain/chatbot/entity/ChatMessageTest.java
git commit -m "feat(chatbot): add ChatMessage entity with JsonNode bot_response"
```

---

## Task 5: Repository

**Files:**
- Create: `src/main/java/com/caring/domain/chatbot/repository/ChatSessionRepository.java`
- Create: `src/main/java/com/caring/domain/chatbot/repository/ChatMessageRepository.java`

- [ ] **Step 1: ChatSessionRepository**

```java
// src/main/java/com/caring/domain/chatbot/repository/ChatSessionRepository.java
package com.caring.domain.chatbot.repository;

import com.caring.domain.chatbot.entity.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatSessionRepository extends JpaRepository<ChatSession, String> {

    List<ChatSession> findByUser_UsernameOrderByLastModifiedDateDesc(String username);
}
```

- [ ] **Step 2: ChatMessageRepository**

```java
// src/main/java/com/caring/domain/chatbot/repository/ChatMessageRepository.java
package com.caring.domain.chatbot.repository;

import com.caring.domain.chatbot.entity.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    Page<ChatMessage> findBySession_IdOrderByCreatedDateDesc(String sessionId, Pageable pageable);

    long countBySession_Id(String sessionId);

    @Query("SELECT m FROM ChatMessage m WHERE m.session.id = :sessionId ORDER BY m.createdDate DESC")
    List<ChatMessage> findRecentBySessionId(String sessionId, Pageable pageable);

    Optional<ChatMessage> findTopBySession_IdOrderByCreatedDateDesc(String sessionId);
}
```

- [ ] **Step 3: 컴파일 확인**

```bash
./gradlew compileJava -x test --quiet
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 커밋**

```bash
git add src/main/java/com/caring/domain/chatbot/repository/
git commit -m "feat(chatbot): add ChatSessionRepository and ChatMessageRepository"
```

---

## Task 6: Adaptor 레이어

**Files:**
- Create: `src/main/java/com/caring/domain/chatbot/adaptor/ChatSessionAdaptor.java`
- Create: `src/main/java/com/caring/domain/chatbot/adaptor/ChatSessionAdaptorImpl.java`
- Create: `src/main/java/com/caring/domain/chatbot/adaptor/ChatMessageAdaptor.java`
- Create: `src/main/java/com/caring/domain/chatbot/adaptor/ChatMessageAdaptorImpl.java`
- Create: `src/test/java/com/caring/domain/chatbot/adaptor/ChatSessionAdaptorImplTest.java`

- [ ] **Step 1: 실패 테스트 작성 (ChatSessionAdaptorImplTest)**

```java
// src/test/java/com/caring/domain/chatbot/adaptor/ChatSessionAdaptorImplTest.java
package com.caring.domain.chatbot.adaptor;

import com.caring.domain.chatbot.entity.ChatSession;
import com.caring.domain.chatbot.exception.ChatbotHandler;
import com.caring.domain.chatbot.repository.ChatSessionRepository;
import com.caring.domain.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ChatSessionAdaptorImplTest {

    @Mock ChatSessionRepository repository;
    @InjectMocks ChatSessionAdaptorImpl adaptor;

    @Test
    void queryById_found_returnsSession() {
        ChatSession s = ChatSession.create(User.builder().build());
        given(repository.findById(s.getId())).willReturn(Optional.of(s));
        assertThat(adaptor.queryById(s.getId())).isSameAs(s);
    }

    @Test
    void queryById_notFound_throws() {
        given(repository.findById("missing")).willReturn(Optional.empty());
        assertThatThrownBy(() -> adaptor.queryById("missing"))
                .isSameAs(ChatbotHandler.SESSION_NOT_FOUND);
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
./gradlew test --tests "com.caring.domain.chatbot.adaptor.ChatSessionAdaptorImplTest" --quiet
```
Expected: FAIL — compile error

- [ ] **Step 3: ChatSessionAdaptor 인터페이스/구현**

```java
// src/main/java/com/caring/domain/chatbot/adaptor/ChatSessionAdaptor.java
package com.caring.domain.chatbot.adaptor;

import com.caring.domain.chatbot.entity.ChatSession;
import java.util.List;

public interface ChatSessionAdaptor {
    ChatSession queryById(String sessionId);
    List<ChatSession> queryByUsername(String username);
    ChatSession save(ChatSession session);
    void delete(ChatSession session);
}
```

```java
// src/main/java/com/caring/domain/chatbot/adaptor/ChatSessionAdaptorImpl.java
package com.caring.domain.chatbot.adaptor;

import com.caring.common.annotation.Adaptor;
import com.caring.domain.chatbot.entity.ChatSession;
import com.caring.domain.chatbot.exception.ChatbotHandler;
import com.caring.domain.chatbot.repository.ChatSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Adaptor
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ChatSessionAdaptorImpl implements ChatSessionAdaptor {

    private final ChatSessionRepository repository;

    @Override
    public ChatSession queryById(String sessionId) {
        return repository.findById(sessionId)
                .orElseThrow(() -> ChatbotHandler.SESSION_NOT_FOUND);
    }

    @Override
    public List<ChatSession> queryByUsername(String username) {
        return repository.findByUser_UsernameOrderByLastModifiedDateDesc(username);
    }

    @Override
    @Transactional
    public ChatSession save(ChatSession session) {
        return repository.save(session);
    }

    @Override
    @Transactional
    public void delete(ChatSession session) {
        repository.delete(session);
    }
}
```

- [ ] **Step 4: ChatMessageAdaptor 인터페이스/구현**

```java
// src/main/java/com/caring/domain/chatbot/adaptor/ChatMessageAdaptor.java
package com.caring.domain.chatbot.adaptor;

import com.caring.domain.chatbot.entity.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface ChatMessageAdaptor {
    ChatMessage queryById(Long messageId);
    Page<ChatMessage> queryBySessionId(String sessionId, Pageable pageable);
    List<ChatMessage> queryRecentBySessionId(String sessionId, int limit);
    long countBySessionId(String sessionId);
    Optional<ChatMessage> queryLatestBySessionId(String sessionId);
    ChatMessage save(ChatMessage message);
}
```

```java
// src/main/java/com/caring/domain/chatbot/adaptor/ChatMessageAdaptorImpl.java
package com.caring.domain.chatbot.adaptor;

import com.caring.common.annotation.Adaptor;
import com.caring.domain.chatbot.entity.ChatMessage;
import com.caring.domain.chatbot.exception.ChatbotHandler;
import com.caring.domain.chatbot.repository.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Adaptor
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ChatMessageAdaptorImpl implements ChatMessageAdaptor {

    private final ChatMessageRepository repository;

    @Override
    public ChatMessage queryById(Long messageId) {
        return repository.findById(messageId)
                .orElseThrow(() -> ChatbotHandler.MESSAGE_NOT_FOUND);
    }

    @Override
    public Page<ChatMessage> queryBySessionId(String sessionId, Pageable pageable) {
        return repository.findBySession_IdOrderByCreatedDateDesc(sessionId, pageable);
    }

    @Override
    public List<ChatMessage> queryRecentBySessionId(String sessionId, int limit) {
        return repository.findRecentBySessionId(sessionId, PageRequest.of(0, limit));
    }

    @Override
    public long countBySessionId(String sessionId) {
        return repository.countBySession_Id(sessionId);
    }

    @Override
    public Optional<ChatMessage> queryLatestBySessionId(String sessionId) {
        return repository.findTopBySession_IdOrderByCreatedDateDesc(sessionId);
    }

    @Override
    @Transactional
    public ChatMessage save(ChatMessage message) {
        return repository.save(message);
    }
}
```

- [ ] **Step 5: 테스트 통과 확인**

```bash
./gradlew test --tests "com.caring.domain.chatbot.adaptor.ChatSessionAdaptorImplTest" --quiet
```
Expected: PASS

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/com/caring/domain/chatbot/adaptor/ \
        src/test/java/com/caring/domain/chatbot/adaptor/
git commit -m "feat(chatbot): add adaptor layer for ChatSession and ChatMessage"
```

---

## Task 7: 프롬프트 (DoranResponse + EmotionStrategies + 3개 프롬프트)

**Files:**
- Create: `src/main/java/com/caring/infra/ai/gemini/prompts/DoranResponse.java`
- Create: `src/main/java/com/caring/infra/ai/gemini/prompts/EmotionStrategies.java`
- Create: `src/main/java/com/caring/infra/ai/gemini/prompts/ReframingPrompt.java`
- Create: `src/main/java/com/caring/infra/ai/gemini/prompts/VoiceReframingPrompt.java`
- Create: `src/main/java/com/caring/infra/ai/gemini/prompts/MindDiaryPrompt.java`
- Create: `src/test/java/com/caring/infra/ai/gemini/prompts/ReframingPromptTest.java`

- [ ] **Step 1: DoranResponse record 작성**

Gemini 응답 6개 필드를 담을 record. Jackson 자동 매핑.

```java
// src/main/java/com/caring/infra/ai/gemini/prompts/DoranResponse.java
package com.caring.infra.ai.gemini.prompts;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DoranResponse(
        String empathy,
        @JsonProperty("detected_distortion") String detectedDistortion,
        String analysis,
        @JsonProperty("socratic_question") String socraticQuestion,
        @JsonProperty("alternative_thought") String alternativeThought,
        @JsonProperty("top_emotion") String topEmotion
) {
    public static DoranResponse fallback() {
        return new DoranResponse(
                "죄송해요, 잠시 생각이 꼬였나 봐요.",
                "없음",
                "내용을 불러오지 못했습니다.",
                "오늘 하루는 어떠셨나요?",
                "항상 응원합니다.",
                "neutral"
        );
    }
}
```

- [ ] **Step 2: EmotionStrategies 작성**

기존 `prompts/emotion_strategies.py`의 핵심을 Java로 이식. 감정별 CBT 전략 블록.

```java
// src/main/java/com/caring/infra/ai/gemini/prompts/EmotionStrategies.java
package com.caring.infra.ai.gemini.prompts;

import java.util.Map;

public final class EmotionStrategies {

    private EmotionStrategies() {}

    private static final Map<String, String> STRATEGY = Map.of(
            "happy",
            "[전략] 기쁨을 더 생생하게 느낄 수 있도록 그 감정의 구체적인 원인과 의미를 함께 탐색하세요.",
            "sad",
            "[전략] 슬픔을 충분히 인정하고 공감한 뒤, 자신을 향한 따뜻한 시선을 회복할 수 있도록 도우세요.",
            "neutral",
            "[전략] 표면 아래 감정이 숨어 있을 수 있으니, 행간과 맥락에서 미묘한 감정을 읽어주세요.",
            "angry",
            "[전략] 분노 뒤에 숨은 좌절·상처를 짚어주고, 비난이 아닌 공감으로 상황을 재구성하세요.",
            "anxiety",
            "[전략] 불안의 대상을 구체화해서 다룰 수 있는 작은 단위로 나누어 보도록 안내하세요.",
            "surprise",
            "[전략] 놀람의 원인을 짚고, 긍정/부정 어느 쪽으로 흘러가는 감정인지 확인하세요."
    );

    public static String block(String emotionEn) {
        if (emotionEn == null) return "";
        String s = STRATEGY.get(emotionEn.toLowerCase());
        return s == null ? "" : "\n" + s + "\n";
    }

    public static String block(String primaryEn, String secondaryEn) {
        StringBuilder sb = new StringBuilder();
        if (primaryEn != null) sb.append(block(primaryEn));
        if (secondaryEn != null) sb.append(block(secondaryEn));
        return sb.toString();
    }
}
```

- [ ] **Step 3: 11개 카테고리 가이드라인 상수 추출**

`ReframingPrompt`, `VoiceReframingPrompt`, `MindDiaryPrompt` 모두에서 동일한 11개 카테고리 가이드라인을 사용. 중복 제거.

EmotionStrategies와 같은 파일에 추가:

```java
// EmotionStrategies.java 끝부분에 추가
    public static final String DISTORTION_GUIDE = """
            [상담사 분석 가이드라인 (CBT 기반)]
            1. 흑백사고: 모든 것을 '성공 아니면 실패'로만 보는 이분법적 사고.
            2. 선택적 추상: 긍정적인 면은 무시하고 사소한 부정적 세부 사항에만 집착하는 것.
            3. 자의적 추론: 증거 없이 상황을 부정적으로 해석하는 것 (독심술, 점쟁이 오류).
            4. 과잉일반화: 한 번의 실수를 영원한 실패로 간주하는 것.
            5. 확대/축소: 자신의 실수는 크게 부풀리고, 장점은 의미 없게 축소하는 것.
            6. 개인화: 자신과 무관한 외부 사건을 자신의 탓으로 돌리는 것.
            7. 정서적 추론: "내가 그렇게 느끼니까 그건 사실이야"라고 믿는 것.
            8. 긍정 격하: 칭찬이나 성취를 "운이 좋았을 뿐"이라며 가치를 깎아내리는 것.
            9. 파국화: 미래에 일어날 일을 끔찍한 재앙으로 미리 단정 짓는 것.
            10. 잘못된 별칭 붙이기: 실수한 자신에게 "나는 패배자야"라고 꼬리표를 붙이는 것.
            11. 긍정 정서 강화: 인지 오류가 없고, 내담자가 통찰을 얻었거나 안정을 찾은 상태.
            """;
```

- [ ] **Step 4: ReframingPrompt 테스트 작성**

```java
// src/test/java/com/caring/infra/ai/gemini/prompts/ReframingPromptTest.java
package com.caring.infra.ai.gemini.prompts;

import org.junit.jupiter.api.Test;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReframingPromptTest {

    @Test
    void build_includesUserInputAndTurnCount() {
        String prompt = ReframingPrompt.build(
                "오늘 너무 힘들어요",
                List.of(),
                3,
                null
        );
        assertThat(prompt).contains("오늘 너무 힘들어요");
        assertThat(prompt).contains("3번째 대화");
    }

    @Test
    void build_includesDistortionGuide() {
        String prompt = ReframingPrompt.build("test", List.of(), 1, null);
        assertThat(prompt).contains("흑백사고");
        assertThat(prompt).contains("긍정 정서 강화");
    }

    @Test
    void build_includesEmotionStrategy_whenEmotionProvided() {
        String prompt = ReframingPrompt.build("test", List.of(), 1, "sad");
        assertThat(prompt).contains("슬픔을 충분히 인정");
    }

    @Test
    void build_emptyHistory_includesNoneMarker() {
        String prompt = ReframingPrompt.build("test", List.of(), 1, null);
        assertThat(prompt).contains("(없음. 대화 시작)");
    }

    @Test
    void build_withHistory_formatsTurns() {
        List<ReframingPrompt.HistoryTurn> history = List.of(
                new ReframingPrompt.HistoryTurn("어제 우울했어", "오늘은 어떠세요?")
        );
        String prompt = ReframingPrompt.build("좀 나아졌어요", history, 2, null);
        assertThat(prompt).contains("Turn 1:");
        assertThat(prompt).contains("어제 우울했어");
        assertThat(prompt).contains("오늘은 어떠세요?");
    }
}
```

- [ ] **Step 5: 테스트 실패 확인**

```bash
./gradlew test --tests "com.caring.infra.ai.gemini.prompts.ReframingPromptTest" --quiet
```
Expected: FAIL — compile error

- [ ] **Step 6: ReframingPrompt 작성**

```java
// src/main/java/com/caring/infra/ai/gemini/prompts/ReframingPrompt.java
package com.caring.infra.ai.gemini.prompts;

import java.util.List;

public final class ReframingPrompt {

    public record HistoryTurn(String userInput, String botMessage) {}

    private ReframingPrompt() {}

    public static String build(String userInput, List<HistoryTurn> history, int turnCount, String emotionHint) {
        return """
                당신은 따뜻하고 통찰력 있는 전문 심리상담사 '도란이'입니다.
                내담자(User)는 현재 심리적인 어려움을 겪고 있거나, 마음의 정리가 필요해 찾아왔습니다.
                **[호칭 가이드]** 아래 [이전 대화 맥락]을 참고하여 내담자의 이름을 유추할 수 있다면 그 이름을 사용하고, 알 수 없다면 '내담자'라고 지칭하세요.
                현재 이 세션의 **%d번째 대화**가 진행 중입니다.

                [이전 대화 맥락]
                %s

                [현재 내담자의 말]
                "%s"
                %s
                **⭐⭐[핵심 지시사항: 텍스트 심층 분석]⭐⭐**
                내담자의 텍스트 표면에 드러난 말이 아닌, **행간에 숨겨진 감정**을 포착하세요.
                1. **'Neutral' 지양:** 특별한 감정 단어가 없더라도, 상황이 부정적이라면(예: "시험을 망쳤어") 'neutral' 대신 'sad'나 'anxiety'를 적극적으로 추론하세요.
                2. **방어기제 파악:** 내담자가 "괜찮아요", "상관없어요"라고 말하더라도, 이전 맥락상 포기나 체념이 느껴진다면 'sad'로 판단하고 위로하세요.

                %s

                **⭐⭐[최우선 지시사항 - 위기 개입]⭐⭐**
                만약 내담자의 말에서 **자살, 자해, 죽음, 살인, 심각한 범죄** 암시가 감지되면:
                - 모든 상담 기법을 중단하세요.
                - `empathy`: "지금 많이 힘든 마음이 느껴져서 걱정이 됩니다. 혼자서 감당하기 어렵다면 전문가나 도움 기관에 연락해보시는 건 어떨까요? (자살예방상담전화 109)" 와 같이 안전을 최우선으로 하는 답변을 작성하세요.
                - `detected_distortion`: "위기 상황"
                - `top_emotion`: "anxiety"

                **[일반 상담 지시사항]**
                위기 상황이 아니라면 아래 단계에 따라 답변을 생성하세요.

                1. **마무리 판단:** 6턴 초과 + 감정이 긍정/안정이거나 15턴 초과 시 부드럽게 종료를 권유.
                2. **반영적 경청:** 사실과 감정을 연결해 읽어주기.
                3. **인지 오류 탐지 및 분석:** 가이드라인에서 해당 항목을 골라 친절하게 설명.
                4. **소크라테스식 질문:** 내담자가 스스로 모순을 깨닫게 하는 질문.

                **⭐⭐[논리적 일관성 검증 (필수)]⭐⭐**
                1. `detected_distortion`이 '위기 상황' → `top_emotion`은 무조건 'anxiety'.
                2. `detected_distortion`이 감지되었는데('없음', '긍정 정서 강화' 제외) → `top_emotion`은 절대 'neutral'일 수 없음.
                3. 'neutral'은 '없음' 또는 '긍정 정서 강화'일 때만 허용.
                """.formatted(
                        turnCount,
                        formatHistory(history),
                        userInput,
                        EmotionStrategies.block(emotionHint),
                        EmotionStrategies.DISTORTION_GUIDE
                );
    }

    private static String formatHistory(List<HistoryTurn> history) {
        if (history == null || history.isEmpty()) {
            return "(없음. 대화 시작)";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < history.size(); i++) {
            HistoryTurn t = history.get(i);
            sb.append("Turn ").append(i + 1).append(":\n");
            sb.append(" - 내담자: ").append(t.userInput()).append("\n");
            sb.append(" - 상담사: ").append(t.botMessage() == null ? "" : t.botMessage()).append("\n");
        }
        return sb.toString();
    }
}
```

- [ ] **Step 7: 테스트 통과 확인**

```bash
./gradlew test --tests "com.caring.infra.ai.gemini.prompts.ReframingPromptTest" --quiet
```
Expected: PASS

- [ ] **Step 8: VoiceReframingPrompt 작성**

```java
// src/main/java/com/caring/infra/ai/gemini/prompts/VoiceReframingPrompt.java
package com.caring.infra.ai.gemini.prompts;

import java.util.List;

public final class VoiceReframingPrompt {

    private VoiceReframingPrompt() {}

    /**
     * @param emotionDesc 음성 감정 분석 컨텍스트 (없으면 "(감정 분석 정보 없음)")
     * @param emotionHint 전략 블록 선택용 (primary 카테고리 영문, 없으면 null)
     */
    public static String build(
            String userInput,
            List<ReframingPrompt.HistoryTurn> history,
            int turnCount,
            String userName,
            String emotionDesc,
            String emotionHint
    ) {
        return """
                당신은 따뜻하고 통찰력 있는 전문 심리상담사 '도란이'입니다.
                현재 내담자 **'%s'님**과 **음성**으로 대화를 나누고 있으며, 이 세션의 **%d번째 대화**가 진행 중입니다.

                [음성 감정 분석 정보]
                %s

                [현재 내담자의 말 (STT)]
                "%s"

                **⭐⭐[핵심 지시사항: 감정의 교차 검증]⭐⭐**
                위 [음성 감정 분석 정보]와 [내담자의 말]을 비교하여 가장 타당한 감정을 도출하세요.
                - 말이 평범한데(Neutral) 음성이 슬픔/불안이면 → 음성 신뢰 (감정 숨김 가능성).
                - 말이 명확히 부정인데 음성이 긍정/중립이면 → 텍스트 신뢰 (음성 모델 오류 가능성).
                - 'Neutral'은 텍스트와 음성 모두 사무적일 때만 선택.
                - 자살/자해/범죄 암시가 보이면 음성 결과 무관하게 위기 개입.

                [이전 대화 맥락]
                %s
                %s
                %s

                **[일반 상담 지시사항]**
                1. 마무리 판단: 6턴 초과 + 안정 / 15턴 초과 시 부드럽게 종료 권유.
                2. 반영적 경청: 교차 검증 결과 감정 기반 공감.
                3. 인지 오류 탐지 및 분석.
                4. 소크라테스식 질문.

                **⭐⭐[논리적 일관성 검증]⭐⭐**
                1. '위기 상황' → top_emotion='anxiety'.
                2. 인지 왜곡 감지('없음', '긍정 정서 강화' 제외) → top_emotion≠neutral.
                3. neutral은 '없음' 또는 '긍정 정서 강화'일 때만.
                """.formatted(
                        userName,
                        turnCount,
                        emotionDesc == null || emotionDesc.isBlank() ? "(감정 분석 정보 없음)" : emotionDesc,
                        userInput,
                        formatHistory(history),
                        EmotionStrategies.block(emotionHint),
                        EmotionStrategies.DISTORTION_GUIDE
                );
    }

    private static String formatHistory(List<ReframingPrompt.HistoryTurn> history) {
        if (history == null || history.isEmpty()) {
            return "(없음. 대화 시작)";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < history.size(); i++) {
            ReframingPrompt.HistoryTurn t = history.get(i);
            sb.append("Turn ").append(i + 1).append(":\n");
            sb.append(" - 내담자: ").append(t.userInput()).append("\n");
            sb.append(" - 상담사: ").append(t.botMessage() == null ? "" : t.botMessage()).append("\n");
        }
        return sb.toString();
    }
}
```

- [ ] **Step 9: MindDiaryPrompt 작성**

```java
// src/main/java/com/caring/infra/ai/gemini/prompts/MindDiaryPrompt.java
package com.caring.infra.ai.gemini.prompts;

public final class MindDiaryPrompt {

    private MindDiaryPrompt() {}

    /**
     * @param emotionDesc 모놀리식 VoiceComposite + VoiceEmotionLabel을 가공한 텍스트 블록
     * @param emotionHint 전략 블록 선택용
     */
    public static String build(
            String userName,
            String question,
            String content,
            String recordedAt,
            String emotionDesc,
            String emotionHint
    ) {
        String contentDisplay = (content == null || content.isBlank())
                ? "(음성으로 기록됨 — 텍스트 변환 없음)"
                : content;

        return """
                당신은 전문 심리상담사이자 CBT(인지행동치료) 전문가 '도란이'입니다.
                사용자 '%s'님이 작성한 '마음일기'를 읽고, 먼저 다가가서 대화를 시작해야 합니다.

                [마음일기 정보]
                - 주제(질문): %s
                - 작성 내용: "%s"
                - 작성 일시: %s

                [감정 분석 결과]
                %s
                %s

                %s

                **지시사항:**
                1. 복합 감정 읽기: 두드러지는 다른 감정도 함께 읽기.
                2. 공감(Empathy): 사용자 이름을 부르며 따뜻한 첫인사.
                3. 왜곡 탐지: 1~10번에 해당하면 명칭 기입, 긍정적이면 '긍정 정서 강화', 별다른 특징 없으면 '없음'.
                4. 분석(Analysis): 심리적 배경을 부드럽게.
                5. 질문(Question): 인지 오류면 자기성찰 질문, 긍정 정서 강화면 그 기분을 더 느낄 수 있는 질문.
                6. 대안적 사고(Alternative): 객관적/긍정적 시각, 또는 응원의 말.
                """.formatted(
                        userName,
                        question == null ? "(자유 일기)" : question,
                        contentDisplay,
                        recordedAt == null ? "알 수 없음" : recordedAt,
                        emotionDesc == null ? "(감정 분석 정보 없음)" : emotionDesc,
                        EmotionStrategies.block(emotionHint),
                        EmotionStrategies.DISTORTION_GUIDE
                );
    }
}
```

- [ ] **Step 10: 컴파일/테스트 확인**

```bash
./gradlew test --tests "com.caring.infra.ai.gemini.prompts.*" --quiet
```
Expected: PASS

- [ ] **Step 11: 커밋**

```bash
git add src/main/java/com/caring/infra/ai/gemini/prompts/ \
        src/test/java/com/caring/infra/ai/gemini/prompts/
git commit -m "feat(chatbot): add Doran prompts (reframing, voice, mind-diary)"
```

---

## Task 8: GeminiChatbotClient

**Files:**
- Create: `src/main/java/com/caring/infra/ai/gemini/GeminiChatbotClient.java`
- Create: `src/test/java/com/caring/infra/ai/gemini/GeminiChatbotClientTest.java`

이 컴포넌트는 도란이 전용 Gemini 호출. `GeminiVoiceAnalyzer`와 같은 SDK(`com.google.genai.Client`)를 쓰되, 모델은 `gemini-2.5-pro`, responseSchema는 도란이용 6개 필드.

- [ ] **Step 1: 실패 테스트 작성**

```java
// src/test/java/com/caring/infra/ai/gemini/GeminiChatbotClientTest.java
package com.caring.infra.ai.gemini;

import com.caring.infra.ai.gemini.prompts.DoranResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class GeminiChatbotClientTest {

    @Test
    void generate_clientNotConfigured_returnsFallback() {
        GeminiChatbotClient client = new GeminiChatbotClient(
                Optional.empty(), "gemini-2.5-pro", new ObjectMapper());
        DoranResponse r = client.generate("any prompt");
        assertThat(r.empathy()).contains("죄송");
        assertThat(r.topEmotion()).isEqualTo("neutral");
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
./gradlew test --tests "com.caring.infra.ai.gemini.GeminiChatbotClientTest" --quiet
```
Expected: FAIL

- [ ] **Step 3: GeminiChatbotClient 작성**

```java
// src/main/java/com/caring/infra/ai/gemini/GeminiChatbotClient.java
package com.caring.infra.ai.gemini;

import com.caring.infra.ai.gemini.prompts.DoranResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import com.google.genai.types.Schema;
import com.google.genai.types.Type;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
public class GeminiChatbotClient {

    private static final List<String> DISTORTION_ENUM = List.of(
            "흑백사고", "선택적 추상", "자의적 추론", "과잉일반화", "확대/축소",
            "개인화", "정서적 추론", "긍정 격하", "파국화", "잘못된 별칭 붙이기",
            "긍정 정서 강화", "위기 상황", "없음"
    );

    private static final List<String> EMOTION_ENUM = List.of(
            "happy", "sad", "neutral", "angry", "anxiety", "surprise"
    );

    private final Optional<Client> geminiClient;
    private final String modelName;
    private final ObjectMapper objectMapper;

    public GeminiChatbotClient(
            Optional<Client> geminiClient,
            @Value("${gemini.chatbot-model:gemini-2.5-pro}") String modelName,
            ObjectMapper objectMapper
    ) {
        this.geminiClient = geminiClient;
        this.modelName = modelName;
        this.objectMapper = objectMapper;
    }

    public DoranResponse generate(String prompt) {
        if (geminiClient.isEmpty()) {
            log.warn("GeminiChatbotClient: no Gemini client configured, returning fallback");
            return DoranResponse.fallback();
        }
        try {
            GenerateContentResponse response = geminiClient.get().models.generateContent(
                    modelName,
                    Content.builder()
                            .role("user")
                            .parts(List.of(Part.builder().text(prompt).build()))
                            .build(),
                    GenerateContentConfig.builder()
                            .temperature(0.7f)
                            .maxOutputTokens(2048)
                            .responseMimeType("application/json")
                            .responseSchema(buildResponseSchema())
                            .build()
            );

            String text = response.text();
            if (text == null || text.isBlank()) {
                log.warn("GeminiChatbotClient: empty response, returning fallback");
                return DoranResponse.fallback();
            }
            return objectMapper.readValue(text, DoranResponse.class);
        } catch (Exception e) {
            log.error("GeminiChatbotClient.generate failed", e);
            return DoranResponse.fallback();
        }
    }

    private Schema buildResponseSchema() {
        Map<String, Schema> props = new HashMap<>();
        props.put("empathy", Schema.builder().type(Type.Known.STRING).build());
        props.put("detected_distortion", Schema.builder()
                .type(Type.Known.STRING)
                .enum_(DISTORTION_ENUM)
                .build());
        props.put("analysis", Schema.builder().type(Type.Known.STRING).build());
        props.put("socratic_question", Schema.builder().type(Type.Known.STRING).build());
        props.put("alternative_thought", Schema.builder().type(Type.Known.STRING).build());
        props.put("top_emotion", Schema.builder()
                .type(Type.Known.STRING)
                .enum_(EMOTION_ENUM)
                .build());

        return Schema.builder()
                .type(Type.Known.OBJECT)
                .properties(props)
                .required(List.of("empathy", "detected_distortion", "analysis",
                        "socratic_question", "alternative_thought", "top_emotion"))
                .build();
    }
}
```

> **참고**: `Schema.builder().enum_(...)` 메서드명이 SDK 버전에 따라 다를 수 있다. 현재 사용 중인 `com.google.genai` 버전에서 메서드명을 `Schema.java` 소스로 확인하고 맞지 않으면 `format(JsonValue)` 또는 다른 enum 지정 방식 사용. `GeminiVoiceAnalyzer.buildResponseSchema()`의 기존 패턴을 참고.

- [ ] **Step 4: 테스트 통과 확인**

```bash
./gradlew test --tests "com.caring.infra.ai.gemini.GeminiChatbotClientTest" --quiet
```
Expected: PASS

- [ ] **Step 5: 컴파일 확인 (SDK enum 메서드 호환)**

```bash
./gradlew compileJava -x test --quiet
```
Expected: BUILD SUCCESSFUL.
실패 시 `Schema.builder().enum_(...)` 부분을 SDK 실제 시그니처에 맞게 수정 (예: `.enumValues(...)` 등). 빌드 통과까지 SDK 메서드 확인 후 재컴파일.

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/com/caring/infra/ai/gemini/GeminiChatbotClient.java \
        src/test/java/com/caring/infra/ai/gemini/GeminiChatbotClientTest.java
git commit -m "feat(chatbot): add GeminiChatbotClient with responseSchema enum enforcement"
```

---

## Task 9: ApplicationEvent + GeminiVoiceAnalyzer 발행

**Files:**
- Create: `src/main/java/com/caring/common/event/VoiceAnalysisCompletedEvent.java`
- Modify: `src/main/java/com/caring/infra/ai/gemini/GeminiVoiceAnalyzer.java`

- [ ] **Step 1: 이벤트 record 작성**

```java
// src/main/java/com/caring/common/event/VoiceAnalysisCompletedEvent.java
package com.caring.common.event;

/**
 * 마음일기 음성 감정 분석이 완료되었을 때 발행되는 이벤트.
 * Voice 도메인 → Chatbot 도메인 (선제 대화 트리거) 등 후속 작업이 구독한다.
 */
public record VoiceAnalysisCompletedEvent(Long voiceId) {}
```

- [ ] **Step 2: GeminiVoiceAnalyzer 수정 — `ApplicationEventPublisher` 주입 + 분석 완료 시 발행**

`GeminiVoiceAnalyzer.java` 수정 (현재 261줄 파일).

먼저 import 추가:
```java
import com.caring.common.event.VoiceAnalysisCompletedEvent;
import org.springframework.context.ApplicationEventPublisher;
```

생성자 파라미터에 `ApplicationEventPublisher eventPublisher`를 마지막에 추가하고, 필드도 추가.

`analyzeAsync` 메서드의 성공 처리 마지막(현재 `log.info("Gemini analysis saved..."` 다음)에:
```java
eventPublisher.publishEvent(new VoiceAnalysisCompletedEvent(voiceId));
```

수정 후 `analyzeAsync`의 try 블록 끝 부분이 다음과 같이 되어야 한다:

```java
            voice.markAnalysisCompleted();
            voiceAdaptor.save(voice);
            log.info("Gemini analysis saved for voiceId={}, topEmotion={}, labels={}, transcript={}chars",
                    voiceId, composite.getTopEmotion(), labels.size(),
                    result.transcript() != null ? result.transcript().length() : 0);
            eventPublisher.publishEvent(new VoiceAnalysisCompletedEvent(voiceId));
```

> 이벤트는 분석 실패 시에는 발행하지 않음 (catch 블록엔 추가 안 함). 마음일기 트리거 자체가 분석 결과 의존이므로 자연스럽다.

- [ ] **Step 3: 컴파일 확인**

```bash
./gradlew compileJava -x test --quiet
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 기존 GeminiVoiceAnalyzer 테스트가 깨지지 않는지 확인**

```bash
./gradlew test --tests "com.caring.infra.ai.gemini.*" --quiet
```
기존 테스트의 생성자 인자 mock에 `ApplicationEventPublisher` mock을 추가해야 할 수 있다. 만약 기존 테스트 파일이 있고 컴파일 실패라면, 해당 테스트도 수정.

(참고: GeminiVoiceAnalyzer는 직접적인 단위 테스트가 없을 가능성 높음. UseCase 테스트에서만 mock으로 사용됨. 그 경우 영향 없음.)

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/caring/common/event/VoiceAnalysisCompletedEvent.java \
        src/main/java/com/caring/infra/ai/gemini/GeminiVoiceAnalyzer.java
git commit -m "feat(chatbot): publish VoiceAnalysisCompletedEvent after Gemini analysis"
```

---

## Task 10: ChatbotDomainService

**Files:**
- Create: `src/main/java/com/caring/domain/chatbot/service/ChatbotDomainService.java`
- Create: `src/main/java/com/caring/domain/chatbot/service/ChatbotDomainServiceImpl.java`

세션 권한 검증, 메시지 저장, 세션 lastModifiedDate 갱신 등 도메인 헬퍼.

- [ ] **Step 1: 인터페이스 작성**

```java
// src/main/java/com/caring/domain/chatbot/service/ChatbotDomainService.java
package com.caring.domain.chatbot.service;

import com.caring.domain.chatbot.entity.ChatMessage;
import com.caring.domain.chatbot.entity.ChatSession;
import com.caring.domain.chatbot.entity.DoranEmotion;
import com.caring.domain.user.entity.User;

public interface ChatbotDomainService {

    /**
     * sessionId 소유자가 user인지 검증. 다르면 SESSION_NO_PERMISSION 발생.
     */
    void verifyOwnership(ChatSession session, User user);

    /**
     * messageId가 user 소유 세션의 메시지인지 검증.
     */
    void verifyOwnership(ChatMessage message, User user);

    /**
     * String code → DoranEmotion. 잘못된 값이면 FEEDBACK_INVALID_EMOTION.
     */
    DoranEmotion parseFeedbackEmotion(String emotionCode);
}
```

- [ ] **Step 2: 구현 작성**

```java
// src/main/java/com/caring/domain/chatbot/service/ChatbotDomainServiceImpl.java
package com.caring.domain.chatbot.service;

import com.caring.common.annotation.DomainService;
import com.caring.domain.chatbot.entity.ChatMessage;
import com.caring.domain.chatbot.entity.ChatSession;
import com.caring.domain.chatbot.entity.DoranEmotion;
import com.caring.domain.chatbot.exception.ChatbotHandler;
import com.caring.domain.user.entity.User;

@DomainService
public class ChatbotDomainServiceImpl implements ChatbotDomainService {

    @Override
    public void verifyOwnership(ChatSession session, User user) {
        if (!session.getUser().getId().equals(user.getId())) {
            throw ChatbotHandler.SESSION_NO_PERMISSION;
        }
    }

    @Override
    public void verifyOwnership(ChatMessage message, User user) {
        if (!message.getSession().getUser().getId().equals(user.getId())) {
            throw ChatbotHandler.MESSAGE_NO_PERMISSION;
        }
    }

    @Override
    public DoranEmotion parseFeedbackEmotion(String emotionCode) {
        try {
            return DoranEmotion.fromCode(emotionCode);
        } catch (IllegalArgumentException e) {
            throw ChatbotHandler.FEEDBACK_INVALID_EMOTION;
        }
    }
}
```

- [ ] **Step 3: 컴파일 확인**

```bash
./gradlew compileJava -x test --quiet
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 커밋**

```bash
git add src/main/java/com/caring/domain/chatbot/service/
git commit -m "feat(chatbot): add ChatbotDomainService with ownership/parsing helpers"
```

---

## Task 11: CreateChatSessionUseCase + Controller scaffold

**Files:**
- Create: `src/main/java/com/caring/api/chatbot/dto/CreateSessionResponse.java`
- Create: `src/main/java/com/caring/api/chatbot/service/CreateChatSessionUseCase.java`
- Create: `src/main/java/com/caring/api/chatbot/controller/ChatbotApiController.java`
- Create: `src/test/java/com/caring/api/chatbot/service/CreateChatSessionUseCaseTest.java`

- [ ] **Step 1: 실패 테스트 작성**

```java
// src/test/java/com/caring/api/chatbot/service/CreateChatSessionUseCaseTest.java
package com.caring.api.chatbot.service;

import com.caring.api.chatbot.dto.CreateSessionResponse;
import com.caring.domain.chatbot.adaptor.ChatSessionAdaptor;
import com.caring.domain.chatbot.entity.ChatSession;
import com.caring.domain.user.adaptor.UserAdaptor;
import com.caring.domain.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CreateChatSessionUseCaseTest {

    @Mock UserAdaptor userAdaptor;
    @Mock ChatSessionAdaptor chatSessionAdaptor;
    @InjectMocks CreateChatSessionUseCase useCase;

    @Test
    void execute_savesSessionAndReturnsId() {
        User user = User.builder().id(1L).build();
        given(userAdaptor.queryUserByUsername("u1")).willReturn(user);
        given(chatSessionAdaptor.save(org.mockito.ArgumentMatchers.any(ChatSession.class)))
                .willAnswer(inv -> inv.getArgument(0));

        CreateSessionResponse res = useCase.execute("u1");

        assertThat(res.sessionId()).isNotBlank();
        ArgumentCaptor<ChatSession> captor = ArgumentCaptor.forClass(ChatSession.class);
        verify(chatSessionAdaptor).save(captor.capture());
        assertThat(captor.getValue().getUser()).isSameAs(user);
        assertThat(captor.getValue().getId()).isEqualTo(res.sessionId());
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
./gradlew test --tests "com.caring.api.chatbot.service.CreateChatSessionUseCaseTest" --quiet
```
Expected: FAIL

- [ ] **Step 3: CreateSessionResponse 작성**

```java
// src/main/java/com/caring/api/chatbot/dto/CreateSessionResponse.java
package com.caring.api.chatbot.dto;

public record CreateSessionResponse(String sessionId) {}
```

- [ ] **Step 4: CreateChatSessionUseCase 작성**

```java
// src/main/java/com/caring/api/chatbot/service/CreateChatSessionUseCase.java
package com.caring.api.chatbot.service;

import com.caring.api.chatbot.dto.CreateSessionResponse;
import com.caring.common.annotation.UseCase;
import com.caring.domain.chatbot.adaptor.ChatSessionAdaptor;
import com.caring.domain.chatbot.entity.ChatSession;
import com.caring.domain.user.adaptor.UserAdaptor;
import com.caring.domain.user.entity.User;
import lombok.RequiredArgsConstructor;

@UseCase
@RequiredArgsConstructor
public class CreateChatSessionUseCase {

    private final UserAdaptor userAdaptor;
    private final ChatSessionAdaptor chatSessionAdaptor;

    public CreateSessionResponse execute(String username) {
        User user = userAdaptor.queryUserByUsername(username);
        ChatSession session = ChatSession.create(user);
        ChatSession saved = chatSessionAdaptor.save(session);
        return new CreateSessionResponse(saved.getId());
    }
}
```

- [ ] **Step 5: ChatbotApiController scaffold (POST /sessions만)**

```java
// src/main/java/com/caring/api/chatbot/controller/ChatbotApiController.java
package com.caring.api.chatbot.controller;

import com.caring.api.chatbot.dto.CreateSessionResponse;
import com.caring.api.chatbot.service.CreateChatSessionUseCase;
import com.caring.api.common.dto.ApiResponseDto;
import com.caring.common.annotation.UserCode;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/api/chatbot")
public class ChatbotApiController {

    private final CreateChatSessionUseCase createChatSessionUseCase;

    @PostMapping("/sessions")
    public ApiResponseDto<CreateSessionResponse> createSession(@UserCode String username) {
        return ApiResponseDto.onSuccess(createChatSessionUseCase.execute(username));
    }
}
```

- [ ] **Step 6: 테스트 통과 + 컴파일 확인**

```bash
./gradlew test --tests "com.caring.api.chatbot.service.CreateChatSessionUseCaseTest" --quiet
./gradlew compileJava -x test --quiet
```
Expected: PASS, BUILD SUCCESSFUL

- [ ] **Step 7: 커밋**

```bash
git add src/main/java/com/caring/api/chatbot/ \
        src/test/java/com/caring/api/chatbot/service/CreateChatSessionUseCaseTest.java
git commit -m "feat(chatbot): add CreateChatSessionUseCase and controller scaffold"
```

---

## Task 12: DeleteChatSessionUseCase

**Files:**
- Create: `src/main/java/com/caring/api/chatbot/service/DeleteChatSessionUseCase.java`
- Modify: `src/main/java/com/caring/api/chatbot/controller/ChatbotApiController.java`
- Create: `src/test/java/com/caring/api/chatbot/service/DeleteChatSessionUseCaseTest.java`

- [ ] **Step 1: 실패 테스트 작성**

```java
// src/test/java/com/caring/api/chatbot/service/DeleteChatSessionUseCaseTest.java
package com.caring.api.chatbot.service;

import com.caring.domain.chatbot.adaptor.ChatSessionAdaptor;
import com.caring.domain.chatbot.entity.ChatSession;
import com.caring.domain.chatbot.exception.ChatbotHandler;
import com.caring.domain.chatbot.service.ChatbotDomainService;
import com.caring.domain.user.adaptor.UserAdaptor;
import com.caring.domain.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DeleteChatSessionUseCaseTest {

    @Mock UserAdaptor userAdaptor;
    @Mock ChatSessionAdaptor chatSessionAdaptor;
    @Mock ChatbotDomainService chatbotDomainService;
    @InjectMocks DeleteChatSessionUseCase useCase;

    @Test
    void execute_owner_deletes() {
        User user = User.builder().id(1L).build();
        ChatSession session = ChatSession.create(user);

        given(userAdaptor.queryUserByUsername("u1")).willReturn(user);
        given(chatSessionAdaptor.queryById(session.getId())).willReturn(session);

        useCase.execute("u1", session.getId());

        verify(chatbotDomainService).verifyOwnership(session, user);
        verify(chatSessionAdaptor).delete(session);
    }

    @Test
    void execute_notOwner_throws() {
        User user = User.builder().id(2L).build();
        ChatSession session = ChatSession.create(User.builder().id(1L).build());

        given(userAdaptor.queryUserByUsername("u2")).willReturn(user);
        given(chatSessionAdaptor.queryById(session.getId())).willReturn(session);
        doThrow(ChatbotHandler.SESSION_NO_PERMISSION)
                .when(chatbotDomainService).verifyOwnership(session, user);

        assertThatThrownBy(() -> useCase.execute("u2", session.getId()))
                .isSameAs(ChatbotHandler.SESSION_NO_PERMISSION);
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
./gradlew test --tests "com.caring.api.chatbot.service.DeleteChatSessionUseCaseTest" --quiet
```
Expected: FAIL

- [ ] **Step 3: DeleteChatSessionUseCase 작성**

```java
// src/main/java/com/caring/api/chatbot/service/DeleteChatSessionUseCase.java
package com.caring.api.chatbot.service;

import com.caring.common.annotation.UseCase;
import com.caring.domain.chatbot.adaptor.ChatSessionAdaptor;
import com.caring.domain.chatbot.entity.ChatSession;
import com.caring.domain.chatbot.service.ChatbotDomainService;
import com.caring.domain.user.adaptor.UserAdaptor;
import com.caring.domain.user.entity.User;
import lombok.RequiredArgsConstructor;

@UseCase
@RequiredArgsConstructor
public class DeleteChatSessionUseCase {

    private final UserAdaptor userAdaptor;
    private final ChatSessionAdaptor chatSessionAdaptor;
    private final ChatbotDomainService chatbotDomainService;

    public void execute(String username, String sessionId) {
        User user = userAdaptor.queryUserByUsername(username);
        ChatSession session = chatSessionAdaptor.queryById(sessionId);
        chatbotDomainService.verifyOwnership(session, user);
        chatSessionAdaptor.delete(session);
    }
}
```

- [ ] **Step 4: Controller에 DELETE endpoint 추가**

`ChatbotApiController.java`에 필드와 메서드 추가:

```java
    private final DeleteChatSessionUseCase deleteChatSessionUseCase;

    @DeleteMapping("/sessions/{sessionId}")
    public ApiResponseDto<Void> deleteSession(
            @UserCode String username,
            @PathVariable String sessionId
    ) {
        deleteChatSessionUseCase.execute(username, sessionId);
        return ApiResponseDto.onSuccess(null);
    }
```

import 추가:
```java
import com.caring.api.chatbot.service.DeleteChatSessionUseCase;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
```

- [ ] **Step 5: 테스트 통과 + 컴파일**

```bash
./gradlew test --tests "com.caring.api.chatbot.service.DeleteChatSessionUseCaseTest" --quiet
./gradlew compileJava -x test --quiet
```
Expected: PASS, BUILD SUCCESSFUL

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/com/caring/api/chatbot/service/DeleteChatSessionUseCase.java \
        src/main/java/com/caring/api/chatbot/controller/ChatbotApiController.java \
        src/test/java/com/caring/api/chatbot/service/DeleteChatSessionUseCaseTest.java
git commit -m "feat(chatbot): add DeleteChatSessionUseCase with cascade hard delete"
```

> **참고**: cascade 동작은 JPA의 `@OnDelete(action = OnDeleteAction.CASCADE)` 또는 DB 레벨 FK constraint(`ON DELETE CASCADE`)가 필요. `chat_message.session_id` FK는 ddl-auto가 자동으로 만들지만 cascade 옵션은 안 줄 가능성. 만약 통합 테스트에서 cascade 안 되면 ChatMessage에 `@ManyToOne` 위에 `@OnDelete(action = OnDeleteAction.CASCADE)` 추가.

---

## Task 13: GetChatSessionsUseCase

**Files:**
- Create: `src/main/java/com/caring/api/chatbot/dto/SessionListResponse.java`
- Create: `src/main/java/com/caring/api/chatbot/dto/ChatSessionItemResponse.java`
- Create: `src/main/java/com/caring/api/chatbot/service/GetChatSessionsUseCase.java`
- Modify: `src/main/java/com/caring/api/chatbot/controller/ChatbotApiController.java`
- Create: `src/test/java/com/caring/api/chatbot/service/GetChatSessionsUseCaseTest.java`

- [ ] **Step 1: DTO 작성**

```java
// src/main/java/com/caring/api/chatbot/dto/ChatSessionItemResponse.java
package com.caring.api.chatbot.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ChatSessionItemResponse(
        String sessionId,
        String lastMessage,
        LocalDateTime lastUpdated,
        List<String> distortionTags,
        String emotion
) {}
```

```java
// src/main/java/com/caring/api/chatbot/dto/SessionListResponse.java
package com.caring.api.chatbot.dto;

import java.util.List;

public record SessionListResponse(List<ChatSessionItemResponse> sessions) {}
```

- [ ] **Step 2: 실패 테스트 작성**

```java
// src/test/java/com/caring/api/chatbot/service/GetChatSessionsUseCaseTest.java
package com.caring.api.chatbot.service;

import com.caring.api.chatbot.dto.SessionListResponse;
import com.caring.domain.chatbot.adaptor.ChatMessageAdaptor;
import com.caring.domain.chatbot.adaptor.ChatSessionAdaptor;
import com.caring.domain.chatbot.entity.ChatMessage;
import com.caring.domain.chatbot.entity.ChatSession;
import com.caring.domain.chatbot.entity.MessageOrigin;
import com.caring.domain.user.entity.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class GetChatSessionsUseCaseTest {

    @Mock ChatSessionAdaptor chatSessionAdaptor;
    @Mock ChatMessageAdaptor chatMessageAdaptor;
    @InjectMocks GetChatSessionsUseCase useCase;

    private static final ObjectMapper OM = new ObjectMapper();

    @Test
    void execute_emptyList_returnsEmpty() {
        given(chatSessionAdaptor.queryByUsername("u1")).willReturn(List.of());
        SessionListResponse res = useCase.execute("u1");
        assertThat(res.sessions()).isEmpty();
    }

    @Test
    void execute_withSessions_includesPreview() throws Exception {
        ChatSession s = ChatSession.create(User.builder().id(1L).build());
        ChatMessage latest = ChatMessage.builder()
                .session(s)
                .userInput("오늘 너무 힘들어요")
                .botResponse(OM.readTree("{\"detected_distortion\":\"흑백사고\",\"emotion\":\"sad\"}"))
                .origin(MessageOrigin.USER_TEXT)
                .build();

        given(chatSessionAdaptor.queryByUsername("u1")).willReturn(List.of(s));
        given(chatMessageAdaptor.queryLatestBySessionId(s.getId())).willReturn(Optional.of(latest));

        SessionListResponse res = useCase.execute("u1");

        assertThat(res.sessions()).hasSize(1);
        assertThat(res.sessions().get(0).sessionId()).isEqualTo(s.getId());
        assertThat(res.sessions().get(0).lastMessage()).isEqualTo("오늘 너무 힘들어요");
        assertThat(res.sessions().get(0).distortionTags()).containsExactly("흑백사고");
        assertThat(res.sessions().get(0).emotion()).isEqualTo("sad");
    }
}
```

- [ ] **Step 3: 테스트 실패 확인**

```bash
./gradlew test --tests "com.caring.api.chatbot.service.GetChatSessionsUseCaseTest" --quiet
```
Expected: FAIL

- [ ] **Step 4: GetChatSessionsUseCase 작성**

```java
// src/main/java/com/caring/api/chatbot/service/GetChatSessionsUseCase.java
package com.caring.api.chatbot.service;

import com.caring.api.chatbot.dto.ChatSessionItemResponse;
import com.caring.api.chatbot.dto.SessionListResponse;
import com.caring.common.annotation.UseCase;
import com.caring.domain.chatbot.adaptor.ChatMessageAdaptor;
import com.caring.domain.chatbot.adaptor.ChatSessionAdaptor;
import com.caring.domain.chatbot.entity.ChatMessage;
import com.caring.domain.chatbot.entity.ChatSession;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@UseCase
@RequiredArgsConstructor
public class GetChatSessionsUseCase {

    private final ChatSessionAdaptor chatSessionAdaptor;
    private final ChatMessageAdaptor chatMessageAdaptor;

    public SessionListResponse execute(String username) {
        List<ChatSession> sessions = chatSessionAdaptor.queryByUsername(username);
        List<ChatSessionItemResponse> items = new ArrayList<>(sessions.size());
        for (ChatSession s : sessions) {
            Optional<ChatMessage> latest = chatMessageAdaptor.queryLatestBySessionId(s.getId());
            String lastMessage = latest.map(ChatMessage::getUserInput).orElse(null);
            String emotion = latest.map(m -> readEmotion(m.getBotResponse())).orElse(null);
            List<String> tags = latest.map(m -> readDistortionTags(m.getBotResponse())).orElse(List.of());

            // 사용자 피드백이 있으면 그 감정 우선
            if (latest.isPresent() && latest.get().getFeedbackEmotion() != null) {
                emotion = latest.get().getFeedbackEmotion().getCode();
            }

            items.add(new ChatSessionItemResponse(
                    s.getId(),
                    lastMessage,
                    s.getLastModifiedDate(),
                    tags,
                    emotion
            ));
        }
        return new SessionListResponse(items);
    }

    private String readEmotion(JsonNode botResponse) {
        if (botResponse == null) return null;
        JsonNode n = botResponse.get("emotion");
        if (n == null) n = botResponse.get("top_emotion");
        return n == null || n.isNull() ? null : n.asText();
    }

    private List<String> readDistortionTags(JsonNode botResponse) {
        if (botResponse == null) return List.of();
        JsonNode n = botResponse.get("detected_distortion");
        if (n == null || n.isNull()) return List.of();
        String value = n.asText();
        if (value.isBlank() || "없음".equals(value)) return List.of();
        return List.of(value);
    }
}
```

- [ ] **Step 5: Controller endpoint 추가**

```java
    private final GetChatSessionsUseCase getChatSessionsUseCase;

    @GetMapping("/sessions")
    public ApiResponseDto<SessionListResponse> getSessions(@UserCode String username) {
        return ApiResponseDto.onSuccess(getChatSessionsUseCase.execute(username));
    }
```

import 추가: `import org.springframework.web.bind.annotation.GetMapping;` `import com.caring.api.chatbot.dto.SessionListResponse;` `import com.caring.api.chatbot.service.GetChatSessionsUseCase;`

- [ ] **Step 6: 테스트 통과 확인**

```bash
./gradlew test --tests "com.caring.api.chatbot.service.GetChatSessionsUseCaseTest" --quiet
```
Expected: PASS

- [ ] **Step 7: 커밋**

```bash
git add src/main/java/com/caring/api/chatbot/dto/SessionListResponse.java \
        src/main/java/com/caring/api/chatbot/dto/ChatSessionItemResponse.java \
        src/main/java/com/caring/api/chatbot/service/GetChatSessionsUseCase.java \
        src/main/java/com/caring/api/chatbot/controller/ChatbotApiController.java \
        src/test/java/com/caring/api/chatbot/service/GetChatSessionsUseCaseTest.java
git commit -m "feat(chatbot): add GetChatSessionsUseCase with last message preview"
```

---

## Task 14: GetChatHistoryUseCase

**Files:**
- Create: `src/main/java/com/caring/api/chatbot/dto/ChatMessageItemResponse.java`
- Create: `src/main/java/com/caring/api/chatbot/dto/ChatHistoryResponse.java`
- Create: `src/main/java/com/caring/api/chatbot/service/GetChatHistoryUseCase.java`
- Modify: `src/main/java/com/caring/api/chatbot/controller/ChatbotApiController.java`
- Create: `src/test/java/com/caring/api/chatbot/service/GetChatHistoryUseCaseTest.java`

- [ ] **Step 1: DTO 작성**

```java
// src/main/java/com/caring/api/chatbot/dto/ChatMessageItemResponse.java
package com.caring.api.chatbot.dto;

import java.time.LocalDateTime;

public record ChatMessageItemResponse(
        Long messageId,
        String role,                     // "user" or "assistant"
        String content,                  // user role일 때 발화 텍스트
        Long voiceId,                    // user role일 때 첨부 voice
        String empathy,                  // assistant role 필드들
        String detectedDistortion,
        String analysis,
        String socraticQuestion,
        String alternativeThought,
        String emotion,
        String feedbackEmotion,
        String feedbackDetail,
        LocalDateTime feedbackAt,
        LocalDateTime timestamp
) {}
```

```java
// src/main/java/com/caring/api/chatbot/dto/ChatHistoryResponse.java
package com.caring.api.chatbot.dto;

import java.util.List;

public record ChatHistoryResponse(
        String sessionId,
        List<ChatMessageItemResponse> messages,
        int totalPage,
        int currentPage
) {}
```

- [ ] **Step 2: 실패 테스트 작성**

```java
// src/test/java/com/caring/api/chatbot/service/GetChatHistoryUseCaseTest.java
package com.caring.api.chatbot.service;

import com.caring.api.chatbot.dto.ChatHistoryResponse;
import com.caring.domain.chatbot.adaptor.ChatMessageAdaptor;
import com.caring.domain.chatbot.adaptor.ChatSessionAdaptor;
import com.caring.domain.chatbot.entity.ChatMessage;
import com.caring.domain.chatbot.entity.ChatSession;
import com.caring.domain.chatbot.entity.MessageOrigin;
import com.caring.domain.chatbot.service.ChatbotDomainService;
import com.caring.domain.user.adaptor.UserAdaptor;
import com.caring.domain.user.entity.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class GetChatHistoryUseCaseTest {

    @Mock UserAdaptor userAdaptor;
    @Mock ChatSessionAdaptor chatSessionAdaptor;
    @Mock ChatMessageAdaptor chatMessageAdaptor;
    @Mock ChatbotDomainService chatbotDomainService;
    @InjectMocks GetChatHistoryUseCase useCase;

    private static final ObjectMapper OM = new ObjectMapper();

    @Test
    void execute_singleMessage_returnsUserAndAssistantPair() throws Exception {
        User user = User.builder().id(1L).build();
        ChatSession s = ChatSession.create(user);
        ChatMessage m = ChatMessage.builder()
                .session(s)
                .userInput("test")
                .botResponse(OM.readTree(
                        "{\"empathy\":\"ok\",\"detected_distortion\":\"흑백사고\",\"analysis\":\"a\"," +
                        "\"socratic_question\":\"q\",\"alternative_thought\":\"alt\",\"emotion\":\"sad\"}"))
                .origin(MessageOrigin.USER_TEXT)
                .build();

        given(userAdaptor.queryUserByUsername("u1")).willReturn(user);
        given(chatSessionAdaptor.queryById(s.getId())).willReturn(s);
        given(chatMessageAdaptor.queryBySessionId(eq(s.getId()), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(m)));

        ChatHistoryResponse res = useCase.execute("u1", s.getId(), 1);

        assertThat(res.sessionId()).isEqualTo(s.getId());
        assertThat(res.messages()).hasSize(2);
        assertThat(res.messages().get(0).role()).isEqualTo("user");
        assertThat(res.messages().get(0).content()).isEqualTo("test");
        assertThat(res.messages().get(1).role()).isEqualTo("assistant");
        assertThat(res.messages().get(1).empathy()).isEqualTo("ok");
        assertThat(res.messages().get(1).detectedDistortion()).isEqualTo("흑백사고");
    }
}
```

- [ ] **Step 3: 테스트 실패 확인**

```bash
./gradlew test --tests "com.caring.api.chatbot.service.GetChatHistoryUseCaseTest" --quiet
```
Expected: FAIL

- [ ] **Step 4: GetChatHistoryUseCase 작성**

```java
// src/main/java/com/caring/api/chatbot/service/GetChatHistoryUseCase.java
package com.caring.api.chatbot.service;

import com.caring.api.chatbot.dto.ChatHistoryResponse;
import com.caring.api.chatbot.dto.ChatMessageItemResponse;
import com.caring.common.annotation.UseCase;
import com.caring.domain.chatbot.adaptor.ChatMessageAdaptor;
import com.caring.domain.chatbot.adaptor.ChatSessionAdaptor;
import com.caring.domain.chatbot.entity.ChatMessage;
import com.caring.domain.chatbot.entity.ChatSession;
import com.caring.domain.chatbot.service.ChatbotDomainService;
import com.caring.domain.user.adaptor.UserAdaptor;
import com.caring.domain.user.entity.User;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@UseCase
@RequiredArgsConstructor
public class GetChatHistoryUseCase {

    private static final int PAGE_SIZE = 20;

    private final UserAdaptor userAdaptor;
    private final ChatSessionAdaptor chatSessionAdaptor;
    private final ChatMessageAdaptor chatMessageAdaptor;
    private final ChatbotDomainService chatbotDomainService;

    public ChatHistoryResponse execute(String username, String sessionId, int page) {
        if (page < 1) page = 1;
        User user = userAdaptor.queryUserByUsername(username);
        ChatSession session = chatSessionAdaptor.queryById(sessionId);
        chatbotDomainService.verifyOwnership(session, user);

        Page<ChatMessage> p = chatMessageAdaptor.queryBySessionId(
                sessionId, PageRequest.of(page - 1, PAGE_SIZE));
        // 최신순 → 시간순 재정렬
        List<ChatMessage> ordered = new ArrayList<>(p.getContent());
        Collections.reverse(ordered);

        List<ChatMessageItemResponse> messages = new ArrayList<>(ordered.size() * 2);
        for (ChatMessage m : ordered) {
            // 사용자 발화 (USER_TEXT/USER_VOICE만 노출, MIND_DIARY는 user 메시지 없음)
            if (m.getOrigin() != com.caring.domain.chatbot.entity.MessageOrigin.MIND_DIARY) {
                messages.add(new ChatMessageItemResponse(
                        m.getId(),
                        "user",
                        m.getUserInput(),
                        m.getVoice() == null ? null : m.getVoice().getId(),
                        null, null, null, null, null, null, null, null, null,
                        m.getCreatedDate()
                ));
            }
            // 봇 응답
            JsonNode bot = m.getBotResponse();
            messages.add(new ChatMessageItemResponse(
                    m.getId(),
                    "assistant",
                    null, null,
                    text(bot, "empathy"),
                    text(bot, "detected_distortion"),
                    text(bot, "analysis"),
                    text(bot, "socratic_question"),
                    text(bot, "alternative_thought"),
                    text(bot, "emotion") != null ? text(bot, "emotion") : text(bot, "top_emotion"),
                    m.getFeedbackEmotion() == null ? null : m.getFeedbackEmotion().getCode(),
                    m.getFeedbackDetail(),
                    m.getFeedbackAt(),
                    m.getCreatedDate()
            ));
        }

        return new ChatHistoryResponse(
                sessionId,
                messages,
                p.getTotalPages() == 0 ? 1 : p.getTotalPages(),
                page
        );
    }

    private String text(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }
}
```

- [ ] **Step 5: Controller endpoint 추가**

```java
    private final GetChatHistoryUseCase getChatHistoryUseCase;

    @GetMapping("/history/{sessionId}")
    public ApiResponseDto<ChatHistoryResponse> getHistory(
            @UserCode String username,
            @PathVariable String sessionId,
            @RequestParam(defaultValue = "1") int page
    ) {
        return ApiResponseDto.onSuccess(getChatHistoryUseCase.execute(username, sessionId, page));
    }
```

import 추가: `import org.springframework.web.bind.annotation.RequestParam;` 등

- [ ] **Step 6: 테스트 통과 + 커밋**

```bash
./gradlew test --tests "com.caring.api.chatbot.service.GetChatHistoryUseCaseTest" --quiet
git add src/main/java/com/caring/api/chatbot/dto/ChatMessageItemResponse.java \
        src/main/java/com/caring/api/chatbot/dto/ChatHistoryResponse.java \
        src/main/java/com/caring/api/chatbot/service/GetChatHistoryUseCase.java \
        src/main/java/com/caring/api/chatbot/controller/ChatbotApiController.java \
        src/test/java/com/caring/api/chatbot/service/GetChatHistoryUseCaseTest.java
git commit -m "feat(chatbot): add GetChatHistoryUseCase with paginated user/assistant pairs"
```

---

## Task 15: SendReframingMessageUseCase (텍스트 채팅)

**Files:**
- Create: `src/main/java/com/caring/api/chatbot/dto/ReframingRequest.java`
- Create: `src/main/java/com/caring/api/chatbot/dto/ReframingResponse.java`
- Create: `src/main/java/com/caring/api/chatbot/service/SendReframingMessageUseCase.java`
- Modify: `src/main/java/com/caring/api/chatbot/controller/ChatbotApiController.java`
- Create: `src/test/java/com/caring/api/chatbot/service/SendReframingMessageUseCaseTest.java`

- [ ] **Step 1: DTO 작성**

```java
// src/main/java/com/caring/api/chatbot/dto/ReframingRequest.java
package com.caring.api.chatbot.dto;

import jakarta.validation.constraints.NotBlank;

public record ReframingRequest(
        @NotBlank String sessionId,
        @NotBlank String userInput,
        String emotion          // optional 감정 힌트
) {}
```

```java
// src/main/java/com/caring/api/chatbot/dto/ReframingResponse.java
package com.caring.api.chatbot.dto;

public record ReframingResponse(
        Long messageId,
        String empathy,
        String detectedDistortion,
        String analysis,
        String socraticQuestion,
        String alternativeThought,
        String emotion
) {}
```

- [ ] **Step 2: 실패 테스트 작성**

```java
// src/test/java/com/caring/api/chatbot/service/SendReframingMessageUseCaseTest.java
package com.caring.api.chatbot.service;

import com.caring.api.chatbot.dto.ReframingRequest;
import com.caring.api.chatbot.dto.ReframingResponse;
import com.caring.domain.chatbot.adaptor.ChatMessageAdaptor;
import com.caring.domain.chatbot.adaptor.ChatSessionAdaptor;
import com.caring.domain.chatbot.entity.ChatMessage;
import com.caring.domain.chatbot.entity.ChatSession;
import com.caring.domain.chatbot.service.ChatbotDomainService;
import com.caring.domain.user.adaptor.UserAdaptor;
import com.caring.domain.user.entity.User;
import com.caring.infra.ai.gemini.GeminiChatbotClient;
import com.caring.infra.ai.gemini.prompts.DoranResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SendReframingMessageUseCaseTest {

    @Mock UserAdaptor userAdaptor;
    @Mock ChatSessionAdaptor chatSessionAdaptor;
    @Mock ChatMessageAdaptor chatMessageAdaptor;
    @Mock ChatbotDomainService chatbotDomainService;
    @Mock GeminiChatbotClient geminiChatbotClient;
    @InjectMocks SendReframingMessageUseCase useCase;

    @Test
    void execute_returnsResponseAndSavesMessage() {
        User user = User.builder().id(1L).build();
        ChatSession session = ChatSession.create(user);
        DoranResponse llm = new DoranResponse(
                "위로 멘트", "흑백사고", "분석", "질문", "대안", "sad");

        given(userAdaptor.queryUserByUsername("u1")).willReturn(user);
        given(chatSessionAdaptor.queryById(session.getId())).willReturn(session);
        given(chatMessageAdaptor.countBySessionId(session.getId())).willReturn(0L);
        given(chatMessageAdaptor.queryRecentBySessionId(session.getId(), 5))
                .willReturn(List.of());
        given(geminiChatbotClient.generate(anyString())).willReturn(llm);
        given(chatMessageAdaptor.save(any(ChatMessage.class)))
                .willAnswer(inv -> {
                    ChatMessage m = inv.getArgument(0);
                    // simulate id assignment
                    java.lang.reflect.Field f = ChatMessage.class.getDeclaredField("id");
                    f.setAccessible(true);
                    f.set(m, 100L);
                    return m;
                });

        ReframingResponse res = useCase.execute(
                "u1", new ReframingRequest(session.getId(), "오늘 너무 힘들어요", null));

        assertThat(res.messageId()).isEqualTo(100L);
        assertThat(res.empathy()).isEqualTo("위로 멘트");
        assertThat(res.detectedDistortion()).isEqualTo("흑백사고");
        assertThat(res.emotion()).isEqualTo("sad");
        verify(chatbotDomainService).verifyOwnership(session, user);
    }
}
```

- [ ] **Step 3: 테스트 실패 확인**

```bash
./gradlew test --tests "com.caring.api.chatbot.service.SendReframingMessageUseCaseTest" --quiet
```
Expected: FAIL

- [ ] **Step 4: SendReframingMessageUseCase 작성**

```java
// src/main/java/com/caring/api/chatbot/service/SendReframingMessageUseCase.java
package com.caring.api.chatbot.service;

import com.caring.api.chatbot.dto.ReframingRequest;
import com.caring.api.chatbot.dto.ReframingResponse;
import com.caring.common.annotation.UseCase;
import com.caring.domain.chatbot.adaptor.ChatMessageAdaptor;
import com.caring.domain.chatbot.adaptor.ChatSessionAdaptor;
import com.caring.domain.chatbot.entity.ChatMessage;
import com.caring.domain.chatbot.entity.ChatSession;
import com.caring.domain.chatbot.entity.MessageOrigin;
import com.caring.domain.chatbot.service.ChatbotDomainService;
import com.caring.domain.user.adaptor.UserAdaptor;
import com.caring.domain.user.entity.User;
import com.caring.infra.ai.gemini.GeminiChatbotClient;
import com.caring.infra.ai.gemini.prompts.DoranResponse;
import com.caring.infra.ai.gemini.prompts.ReframingPrompt;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@UseCase
@RequiredArgsConstructor
public class SendReframingMessageUseCase {

    private static final int HISTORY_LIMIT = 5;
    private static final ObjectMapper OM = new ObjectMapper();

    private final UserAdaptor userAdaptor;
    private final ChatSessionAdaptor chatSessionAdaptor;
    private final ChatMessageAdaptor chatMessageAdaptor;
    private final ChatbotDomainService chatbotDomainService;
    private final GeminiChatbotClient geminiChatbotClient;

    @Transactional
    public ReframingResponse execute(String username, ReframingRequest request) {
        User user = userAdaptor.queryUserByUsername(username);
        ChatSession session = chatSessionAdaptor.queryById(request.sessionId());
        chatbotDomainService.verifyOwnership(session, user);

        long turnCount = chatMessageAdaptor.countBySessionId(session.getId()) + 1;
        List<ChatMessage> recent = chatMessageAdaptor.queryRecentBySessionId(
                session.getId(), HISTORY_LIMIT);
        Collections.reverse(recent);  // 시간순으로

        List<ReframingPrompt.HistoryTurn> history = new ArrayList<>(recent.size());
        for (ChatMessage m : recent) {
            String botMsg = readBotMessage(m.getBotResponse());
            history.add(new ReframingPrompt.HistoryTurn(m.getUserInput(), botMsg));
        }

        String prompt = ReframingPrompt.build(
                request.userInput(), history, (int) turnCount, request.emotion());

        DoranResponse llmResponse = geminiChatbotClient.generate(prompt);

        JsonNode botResponseJson = OM.valueToTree(toMap(llmResponse));
        ChatMessage saved = chatMessageAdaptor.save(ChatMessage.builder()
                .session(session)
                .userInput(request.userInput())
                .botResponse(botResponseJson)
                .origin(MessageOrigin.USER_TEXT)
                .build());

        return new ReframingResponse(
                saved.getId(),
                llmResponse.empathy(),
                llmResponse.detectedDistortion(),
                llmResponse.analysis(),
                llmResponse.socraticQuestion(),
                llmResponse.alternativeThought(),
                llmResponse.topEmotion()
        );
    }

    private String readBotMessage(JsonNode bot) {
        if (bot == null) return "";
        JsonNode q = bot.get("socratic_question");
        if (q != null && !q.isNull()) return q.asText();
        JsonNode e = bot.get("empathy");
        return e == null || e.isNull() ? "" : e.asText();
    }

    private java.util.Map<String, String> toMap(DoranResponse r) {
        java.util.Map<String, String> m = new java.util.LinkedHashMap<>();
        m.put("empathy", r.empathy());
        m.put("detected_distortion", r.detectedDistortion());
        m.put("analysis", r.analysis());
        m.put("socratic_question", r.socraticQuestion());
        m.put("alternative_thought", r.alternativeThought());
        m.put("emotion", r.topEmotion());
        return m;
    }
}
```

- [ ] **Step 5: Controller endpoint 추가**

```java
    private final SendReframingMessageUseCase sendReframingMessageUseCase;

    @PostMapping("/reframing")
    public ApiResponseDto<ReframingResponse> reframing(
            @UserCode String username,
            @RequestBody @Valid ReframingRequest request
    ) {
        return ApiResponseDto.onSuccess(sendReframingMessageUseCase.execute(username, request));
    }
```

import 추가: `@RequestBody`, `@Valid`, `ReframingRequest`, `ReframingResponse`, `SendReframingMessageUseCase`

- [ ] **Step 6: 테스트 통과 + 커밋**

```bash
./gradlew test --tests "com.caring.api.chatbot.service.SendReframingMessageUseCaseTest" --quiet
git add src/main/java/com/caring/api/chatbot/dto/ReframingRequest.java \
        src/main/java/com/caring/api/chatbot/dto/ReframingResponse.java \
        src/main/java/com/caring/api/chatbot/service/SendReframingMessageUseCase.java \
        src/main/java/com/caring/api/chatbot/controller/ChatbotApiController.java \
        src/test/java/com/caring/api/chatbot/service/SendReframingMessageUseCaseTest.java
git commit -m "feat(chatbot): add SendReframingMessageUseCase for text reframing"
```

---

## Task 16: SendVoiceReframingMessageUseCase (음성 채팅)

**Files:**
- Create: `src/main/java/com/caring/api/chatbot/dto/VoiceReframingRequest.java`
- Create: `src/main/java/com/caring/api/chatbot/service/SendVoiceReframingMessageUseCase.java`
- Modify: `src/main/java/com/caring/api/chatbot/controller/ChatbotApiController.java`
- Create: `src/test/java/com/caring/api/chatbot/service/SendVoiceReframingMessageUseCaseTest.java`

음성 채팅은 텍스트 채팅과 거의 동일하나:
- `voiceId`로 Voice + VoiceComposite + VoiceEmotionLabel 조회
- `VoiceReframingPrompt`에 감정 컨텍스트 주입
- `MessageOrigin.USER_VOICE`, `voice` 필드 set
- voiceId 없거나 voice 분석 미완료면 텍스트 모드로 폴백

- [ ] **Step 1: DTO 작성**

```java
// src/main/java/com/caring/api/chatbot/dto/VoiceReframingRequest.java
package com.caring.api.chatbot.dto;

import jakarta.validation.constraints.NotBlank;

public record VoiceReframingRequest(
        @NotBlank String sessionId,
        @NotBlank String userInput,
        Long voiceId           // optional
) {}
```

- [ ] **Step 2: 실패 테스트 작성**

```java
// src/test/java/com/caring/api/chatbot/service/SendVoiceReframingMessageUseCaseTest.java
package com.caring.api.chatbot.service;

import com.caring.api.chatbot.dto.ReframingResponse;
import com.caring.api.chatbot.dto.VoiceReframingRequest;
import com.caring.domain.chatbot.adaptor.ChatMessageAdaptor;
import com.caring.domain.chatbot.adaptor.ChatSessionAdaptor;
import com.caring.domain.chatbot.entity.ChatMessage;
import com.caring.domain.chatbot.entity.ChatSession;
import com.caring.domain.chatbot.service.ChatbotDomainService;
import com.caring.domain.user.adaptor.UserAdaptor;
import com.caring.domain.user.entity.User;
import com.caring.infra.ai.gemini.GeminiChatbotClient;
import com.caring.infra.ai.gemini.prompts.DoranResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class SendVoiceReframingMessageUseCaseTest {

    @Mock UserAdaptor userAdaptor;
    @Mock ChatSessionAdaptor chatSessionAdaptor;
    @Mock ChatMessageAdaptor chatMessageAdaptor;
    @Mock ChatbotDomainService chatbotDomainService;
    @Mock GeminiChatbotClient geminiChatbotClient;
    @Mock com.caring.domain.voice.adaptor.VoiceAdaptor voiceAdaptor;
    @Mock com.caring.domain.voice.adaptor.VoiceCompositeAdaptor voiceCompositeAdaptor;
    @Mock com.caring.domain.voice.adaptor.VoiceEmotionLabelAdaptor voiceEmotionLabelAdaptor;
    @InjectMocks SendVoiceReframingMessageUseCase useCase;

    @Test
    void execute_noVoiceId_fallsBackToTextMode() {
        User user = User.builder().id(1L).build();
        ChatSession session = ChatSession.create(user);
        DoranResponse llm = new DoranResponse(
                "공감", "없음", "ana", "q", "alt", "neutral");

        given(userAdaptor.queryUserByUsername("u1")).willReturn(user);
        given(chatSessionAdaptor.queryById(session.getId())).willReturn(session);
        given(chatMessageAdaptor.countBySessionId(session.getId())).willReturn(0L);
        given(chatMessageAdaptor.queryRecentBySessionId(session.getId(), 5))
                .willReturn(List.of());
        given(geminiChatbotClient.generate(anyString())).willReturn(llm);
        given(chatMessageAdaptor.save(any(ChatMessage.class)))
                .willAnswer(inv -> {
                    ChatMessage m = inv.getArgument(0);
                    java.lang.reflect.Field f = ChatMessage.class.getDeclaredField("id");
                    f.setAccessible(true);
                    f.set(m, 200L);
                    return m;
                });

        ReframingResponse res = useCase.execute(
                "u1", new VoiceReframingRequest(session.getId(), "ok", null));

        assertThat(res.messageId()).isEqualTo(200L);
        assertThat(res.empathy()).isEqualTo("공감");
    }
}
```

- [ ] **Step 3: 테스트 실패 확인**

```bash
./gradlew test --tests "com.caring.api.chatbot.service.SendVoiceReframingMessageUseCaseTest" --quiet
```
Expected: FAIL

- [ ] **Step 4: SendVoiceReframingMessageUseCase 작성**

```java
// src/main/java/com/caring/api/chatbot/service/SendVoiceReframingMessageUseCase.java
package com.caring.api.chatbot.service;

import com.caring.api.chatbot.dto.ReframingResponse;
import com.caring.api.chatbot.dto.VoiceReframingRequest;
import com.caring.common.annotation.UseCase;
import com.caring.domain.chatbot.adaptor.ChatMessageAdaptor;
import com.caring.domain.chatbot.adaptor.ChatSessionAdaptor;
import com.caring.domain.chatbot.entity.ChatMessage;
import com.caring.domain.chatbot.entity.ChatSession;
import com.caring.domain.chatbot.entity.MessageOrigin;
import com.caring.domain.chatbot.service.ChatbotDomainService;
import com.caring.domain.user.adaptor.UserAdaptor;
import com.caring.domain.user.entity.User;
import com.caring.domain.voice.adaptor.VoiceAdaptor;
import com.caring.domain.voice.adaptor.VoiceCompositeAdaptor;
import com.caring.domain.voice.adaptor.VoiceEmotionLabelAdaptor;
import com.caring.domain.voice.entity.Voice;
import com.caring.domain.voice.entity.VoiceComposite;
import com.caring.domain.voice.entity.VoiceEmotionLabel;
import com.caring.infra.ai.gemini.GeminiChatbotClient;
import com.caring.infra.ai.gemini.prompts.DoranResponse;
import com.caring.infra.ai.gemini.prompts.ReframingPrompt;
import com.caring.infra.ai.gemini.prompts.VoiceReframingPrompt;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@UseCase
@RequiredArgsConstructor
public class SendVoiceReframingMessageUseCase {

    private static final int HISTORY_LIMIT = 5;
    private static final ObjectMapper OM = new ObjectMapper();

    private final UserAdaptor userAdaptor;
    private final ChatSessionAdaptor chatSessionAdaptor;
    private final ChatMessageAdaptor chatMessageAdaptor;
    private final ChatbotDomainService chatbotDomainService;
    private final GeminiChatbotClient geminiChatbotClient;
    private final VoiceAdaptor voiceAdaptor;
    private final VoiceCompositeAdaptor voiceCompositeAdaptor;
    private final VoiceEmotionLabelAdaptor voiceEmotionLabelAdaptor;

    @Transactional
    public ReframingResponse execute(String username, VoiceReframingRequest request) {
        User user = userAdaptor.queryUserByUsername(username);
        ChatSession session = chatSessionAdaptor.queryById(request.sessionId());
        chatbotDomainService.verifyOwnership(session, user);

        long turnCount = chatMessageAdaptor.countBySessionId(session.getId()) + 1;
        List<ChatMessage> recent = chatMessageAdaptor.queryRecentBySessionId(
                session.getId(), HISTORY_LIMIT);
        Collections.reverse(recent);

        List<ReframingPrompt.HistoryTurn> history = new ArrayList<>(recent.size());
        for (ChatMessage m : recent) {
            history.add(new ReframingPrompt.HistoryTurn(
                    m.getUserInput(), readBotMessage(m.getBotResponse())));
        }

        Voice voice = null;
        String emotionDesc = null;
        String emotionHint = null;
        if (request.voiceId() != null) {
            try {
                voice = voiceAdaptor.queryById(request.voiceId());
                Optional<VoiceComposite> composite = voiceCompositeAdaptor
                        .queryByVoiceIds(List.of(voice.getId())).stream().findFirst();
                List<VoiceEmotionLabel> labels = voiceEmotionLabelAdaptor
                        .findByVoiceId(voice.getId());
                emotionDesc = formatEmotionDesc(composite.orElse(null), labels);
                emotionHint = composite.map(c -> c.getTopEmotion().name().toLowerCase()).orElse(null);
            } catch (Exception e) {
                // voice 조회 실패 시 텍스트 모드로 폴백
                voice = null;
            }
        }

        String prompt = VoiceReframingPrompt.build(
                request.userInput(), history, (int) turnCount,
                user.getName() == null ? "내담자" : user.getName(),
                emotionDesc, emotionHint);

        DoranResponse llmResponse = geminiChatbotClient.generate(prompt);
        JsonNode botResponseJson = OM.valueToTree(toMap(llmResponse));

        ChatMessage saved = chatMessageAdaptor.save(ChatMessage.builder()
                .session(session)
                .userInput(request.userInput())
                .botResponse(botResponseJson)
                .voice(voice)
                .origin(MessageOrigin.USER_VOICE)
                .build());

        return new ReframingResponse(
                saved.getId(),
                llmResponse.empathy(),
                llmResponse.detectedDistortion(),
                llmResponse.analysis(),
                llmResponse.socraticQuestion(),
                llmResponse.alternativeThought(),
                llmResponse.topEmotion()
        );
    }

    private String formatEmotionDesc(VoiceComposite composite, List<VoiceEmotionLabel> labels) {
        if (composite == null) return "(음성 분석 미완료)";
        StringBuilder sb = new StringBuilder();
        sb.append("- 주된 감정: **").append(composite.getTopEmotion().name().toLowerCase()).append("**");
        sb.append(" (강도 bps: ").append(composite.getTopEmotionConfidenceBps()).append(")\n");
        if (labels != null && !labels.isEmpty()) {
            List<VoiceEmotionLabel> top3 = labels.stream()
                    .sorted((a, b) -> Integer.compare(b.getIntensityX1000(), a.getIntensityX1000()))
                    .limit(3)
                    .toList();
            sb.append("- 세부 감정 top: ");
            for (int i = 0; i < top3.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(top3.get(i).getLabel())
                        .append("(").append(top3.get(i).getIntensityX1000() / 1000.0).append(")");
            }
        }
        return sb.toString();
    }

    private String readBotMessage(JsonNode bot) {
        if (bot == null) return "";
        JsonNode q = bot.get("socratic_question");
        if (q != null && !q.isNull()) return q.asText();
        JsonNode e = bot.get("empathy");
        return e == null || e.isNull() ? "" : e.asText();
    }

    private java.util.Map<String, String> toMap(DoranResponse r) {
        java.util.Map<String, String> m = new java.util.LinkedHashMap<>();
        m.put("empathy", r.empathy());
        m.put("detected_distortion", r.detectedDistortion());
        m.put("analysis", r.analysis());
        m.put("socratic_question", r.socraticQuestion());
        m.put("alternative_thought", r.alternativeThought());
        m.put("emotion", r.topEmotion());
        return m;
    }
}
```

> **컴파일 주의**: `VoiceComposite.getTopEmotion()`이 `EmotionType` enum 반환, `.getTopEmotionConfidenceBps()`, `VoiceEmotionLabel.getLabel()`, `getIntensityX1000()`. 실제 시그니처가 맞는지 컴파일로 확인 후 안 맞으면 조정.

- [ ] **Step 5: Controller endpoint 추가**

```java
    private final SendVoiceReframingMessageUseCase sendVoiceReframingMessageUseCase;

    @PostMapping("/voice-reframing")
    public ApiResponseDto<ReframingResponse> voiceReframing(
            @UserCode String username,
            @RequestBody @Valid VoiceReframingRequest request
    ) {
        return ApiResponseDto.onSuccess(sendVoiceReframingMessageUseCase.execute(username, request));
    }
```

- [ ] **Step 6: 테스트 + 컴파일**

```bash
./gradlew test --tests "com.caring.api.chatbot.service.SendVoiceReframingMessageUseCaseTest" --quiet
./gradlew compileJava -x test --quiet
```
Expected: PASS, BUILD SUCCESSFUL. 컴파일 에러 시 `VoiceComposite`/`VoiceEmotionLabel` 메서드명을 실제로 확인 후 수정.

- [ ] **Step 7: 커밋**

```bash
git add src/main/java/com/caring/api/chatbot/dto/VoiceReframingRequest.java \
        src/main/java/com/caring/api/chatbot/service/SendVoiceReframingMessageUseCase.java \
        src/main/java/com/caring/api/chatbot/controller/ChatbotApiController.java \
        src/test/java/com/caring/api/chatbot/service/SendVoiceReframingMessageUseCaseTest.java
git commit -m "feat(chatbot): add SendVoiceReframingMessageUseCase with voice emotion context"
```

---

## Task 17: UpdateMessageFeedbackUseCase

**Files:**
- Create: `src/main/java/com/caring/api/chatbot/dto/FeedbackRequest.java`
- Create: `src/main/java/com/caring/api/chatbot/service/UpdateMessageFeedbackUseCase.java`
- Modify: `src/main/java/com/caring/api/chatbot/controller/ChatbotApiController.java`
- Create: `src/test/java/com/caring/api/chatbot/service/UpdateMessageFeedbackUseCaseTest.java`

- [ ] **Step 1: DTO 작성**

```java
// src/main/java/com/caring/api/chatbot/dto/FeedbackRequest.java
package com.caring.api.chatbot.dto;

import jakarta.validation.constraints.NotBlank;

public record FeedbackRequest(
        @NotBlank String emotion,
        String detail
) {}
```

피드백 응답은 ChatMessageItemResponse 재활용 또는 단순히 메시지 ID만 반환. 여기선 단순히 messageId + 적용된 피드백 정보만 반환:

```java
// src/main/java/com/caring/api/chatbot/dto/FeedbackResponse.java
package com.caring.api.chatbot.dto;

import java.time.LocalDateTime;

public record FeedbackResponse(
        Long messageId,
        String feedbackEmotion,
        String feedbackDetail,
        LocalDateTime feedbackAt
) {}
```

- [ ] **Step 2: 실패 테스트 작성**

```java
// src/test/java/com/caring/api/chatbot/service/UpdateMessageFeedbackUseCaseTest.java
package com.caring.api.chatbot.service;

import com.caring.api.chatbot.dto.FeedbackRequest;
import com.caring.api.chatbot.dto.FeedbackResponse;
import com.caring.domain.chatbot.adaptor.ChatMessageAdaptor;
import com.caring.domain.chatbot.entity.ChatMessage;
import com.caring.domain.chatbot.entity.ChatSession;
import com.caring.domain.chatbot.entity.DoranEmotion;
import com.caring.domain.chatbot.entity.MessageOrigin;
import com.caring.domain.chatbot.exception.ChatbotHandler;
import com.caring.domain.chatbot.service.ChatbotDomainService;
import com.caring.domain.user.adaptor.UserAdaptor;
import com.caring.domain.user.entity.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class UpdateMessageFeedbackUseCaseTest {

    @Mock UserAdaptor userAdaptor;
    @Mock ChatMessageAdaptor chatMessageAdaptor;
    @Mock ChatbotDomainService chatbotDomainService;
    @InjectMocks UpdateMessageFeedbackUseCase useCase;

    private static final ObjectMapper OM = new ObjectMapper();

    @Test
    void execute_validFeedback_appliesAndReturns() throws Exception {
        User user = User.builder().id(1L).build();
        ChatSession session = ChatSession.create(user);
        ChatMessage msg = ChatMessage.builder()
                .session(session).botResponse(OM.readTree("{}")).origin(MessageOrigin.USER_TEXT).build();

        given(userAdaptor.queryUserByUsername("u1")).willReturn(user);
        given(chatMessageAdaptor.queryById(10L)).willReturn(msg);
        given(chatbotDomainService.parseFeedbackEmotion("anxiety"))
                .willReturn(DoranEmotion.ANXIETY);

        FeedbackResponse res = useCase.execute("u1", 10L,
                new FeedbackRequest("anxiety", "사실 불안이 더 컸어요"));

        assertThat(res.feedbackEmotion()).isEqualTo("anxiety");
        assertThat(res.feedbackDetail()).isEqualTo("사실 불안이 더 컸어요");
        assertThat(msg.getFeedbackEmotion()).isEqualTo(DoranEmotion.ANXIETY);
    }

    @Test
    void execute_invalidEmotion_throws() throws Exception {
        User user = User.builder().id(1L).build();
        ChatSession session = ChatSession.create(user);
        ChatMessage msg = ChatMessage.builder()
                .session(session).botResponse(OM.readTree("{}")).origin(MessageOrigin.USER_TEXT).build();

        given(userAdaptor.queryUserByUsername("u1")).willReturn(user);
        given(chatMessageAdaptor.queryById(10L)).willReturn(msg);
        given(chatbotDomainService.parseFeedbackEmotion("invalid"))
                .willThrow(ChatbotHandler.FEEDBACK_INVALID_EMOTION);

        assertThatThrownBy(() -> useCase.execute("u1", 10L,
                new FeedbackRequest("invalid", null)))
                .isSameAs(ChatbotHandler.FEEDBACK_INVALID_EMOTION);
    }
}
```

- [ ] **Step 3: 테스트 실패 확인**

```bash
./gradlew test --tests "com.caring.api.chatbot.service.UpdateMessageFeedbackUseCaseTest" --quiet
```
Expected: FAIL

- [ ] **Step 4: UpdateMessageFeedbackUseCase 작성**

```java
// src/main/java/com/caring/api/chatbot/service/UpdateMessageFeedbackUseCase.java
package com.caring.api.chatbot.service;

import com.caring.api.chatbot.dto.FeedbackRequest;
import com.caring.api.chatbot.dto.FeedbackResponse;
import com.caring.common.annotation.UseCase;
import com.caring.domain.chatbot.adaptor.ChatMessageAdaptor;
import com.caring.domain.chatbot.entity.ChatMessage;
import com.caring.domain.chatbot.entity.DoranEmotion;
import com.caring.domain.chatbot.service.ChatbotDomainService;
import com.caring.domain.user.adaptor.UserAdaptor;
import com.caring.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@UseCase
@RequiredArgsConstructor
public class UpdateMessageFeedbackUseCase {

    private final UserAdaptor userAdaptor;
    private final ChatMessageAdaptor chatMessageAdaptor;
    private final ChatbotDomainService chatbotDomainService;

    @Transactional
    public FeedbackResponse execute(String username, Long messageId, FeedbackRequest request) {
        User user = userAdaptor.queryUserByUsername(username);
        ChatMessage message = chatMessageAdaptor.queryById(messageId);
        chatbotDomainService.verifyOwnership(message, user);

        DoranEmotion emotion = chatbotDomainService.parseFeedbackEmotion(request.emotion());
        message.applyFeedback(emotion, request.detail());

        return new FeedbackResponse(
                message.getId(),
                emotion.getCode(),
                message.getFeedbackDetail(),
                message.getFeedbackAt()
        );
    }
}
```

- [ ] **Step 5: Controller endpoint 추가**

```java
    private final UpdateMessageFeedbackUseCase updateMessageFeedbackUseCase;

    @PutMapping("/messages/{messageId}/feedback")
    public ApiResponseDto<FeedbackResponse> updateFeedback(
            @UserCode String username,
            @PathVariable Long messageId,
            @RequestBody @Valid FeedbackRequest request
    ) {
        return ApiResponseDto.onSuccess(
                updateMessageFeedbackUseCase.execute(username, messageId, request));
    }
```

import 추가: `@PutMapping`, `FeedbackRequest`, `FeedbackResponse`, `UpdateMessageFeedbackUseCase`

- [ ] **Step 6: 테스트 통과 + 커밋**

```bash
./gradlew test --tests "com.caring.api.chatbot.service.UpdateMessageFeedbackUseCaseTest" --quiet
git add src/main/java/com/caring/api/chatbot/dto/FeedbackRequest.java \
        src/main/java/com/caring/api/chatbot/dto/FeedbackResponse.java \
        src/main/java/com/caring/api/chatbot/service/UpdateMessageFeedbackUseCase.java \
        src/main/java/com/caring/api/chatbot/controller/ChatbotApiController.java \
        src/test/java/com/caring/api/chatbot/service/UpdateMessageFeedbackUseCaseTest.java
git commit -m "feat(chatbot): add UpdateMessageFeedbackUseCase for 진짜 마음 피드백"
```

---

## Task 18: MindDiaryChatTrigger (이벤트 구독 + 선제 대화 생성)

**Files:**
- Create: `src/main/java/com/caring/domain/chatbot/event/MindDiaryChatTrigger.java`
- Create: `src/test/java/com/caring/domain/chatbot/event/MindDiaryChatTriggerTest.java`

- [ ] **Step 1: 실패 테스트 작성**

```java
// src/test/java/com/caring/domain/chatbot/event/MindDiaryChatTriggerTest.java
package com.caring.domain.chatbot.event;

import com.caring.common.event.VoiceAnalysisCompletedEvent;
import com.caring.domain.chatbot.adaptor.ChatMessageAdaptor;
import com.caring.domain.chatbot.adaptor.ChatSessionAdaptor;
import com.caring.domain.chatbot.entity.ChatMessage;
import com.caring.domain.chatbot.entity.ChatSession;
import com.caring.domain.user.entity.User;
import com.caring.domain.voice.adaptor.VoiceAdaptor;
import com.caring.domain.voice.adaptor.VoiceCompositeAdaptor;
import com.caring.domain.voice.adaptor.VoiceEmotionLabelAdaptor;
import com.caring.domain.voice.entity.Voice;
import com.caring.infra.ai.gemini.GeminiChatbotClient;
import com.caring.infra.ai.gemini.prompts.DoranResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MindDiaryChatTriggerTest {

    @Mock VoiceAdaptor voiceAdaptor;
    @Mock VoiceCompositeAdaptor voiceCompositeAdaptor;
    @Mock VoiceEmotionLabelAdaptor voiceEmotionLabelAdaptor;
    @Mock ChatSessionAdaptor chatSessionAdaptor;
    @Mock ChatMessageAdaptor chatMessageAdaptor;
    @Mock GeminiChatbotClient geminiChatbotClient;
    @InjectMocks MindDiaryChatTrigger trigger;

    @Test
    void onVoiceAnalysisCompleted_createsSessionAndFirstMessage() {
        User user = User.builder().id(1L).name("park").build();
        Voice voice = Voice.builder().id(42L).user(user).build();
        DoranResponse llm = new DoranResponse(
                "park님, 오늘 마음이 무거우셨네요.", "긍정 정서 강화",
                "분석", "오늘 하루는 어떠셨나요?", "응원합니다", "neutral");

        given(voiceAdaptor.queryById(42L)).willReturn(voice);
        given(voiceCompositeAdaptor.queryByVoiceIds(List.of(42L))).willReturn(List.of());
        given(voiceEmotionLabelAdaptor.findByVoiceId(42L)).willReturn(List.of());
        given(geminiChatbotClient.generate(anyString())).willReturn(llm);
        given(chatSessionAdaptor.save(any(ChatSession.class)))
                .willAnswer(inv -> inv.getArgument(0));
        given(chatMessageAdaptor.save(any(ChatMessage.class)))
                .willAnswer(inv -> inv.getArgument(0));

        trigger.onVoiceAnalysisCompleted(new VoiceAnalysisCompletedEvent(42L));

        ArgumentCaptor<ChatSession> sCaptor = ArgumentCaptor.forClass(ChatSession.class);
        verify(chatSessionAdaptor).save(sCaptor.capture());
        assertThat(sCaptor.getValue().getUser()).isSameAs(user);

        ArgumentCaptor<ChatMessage> mCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageAdaptor).save(mCaptor.capture());
        assertThat(mCaptor.getValue().getOrigin().name()).isEqualTo("MIND_DIARY");
        assertThat(mCaptor.getValue().getVoice()).isSameAs(voice);
    }

    @Test
    void onVoiceAnalysisCompleted_voiceNotFound_silentlyReturns() {
        given(voiceAdaptor.queryById(99L))
                .willThrow(new RuntimeException("not found"));

        trigger.onVoiceAnalysisCompleted(new VoiceAnalysisCompletedEvent(99L));

        verify(chatSessionAdaptor, never()).save(any());
        verify(chatMessageAdaptor, never()).save(any());
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
./gradlew test --tests "com.caring.domain.chatbot.event.MindDiaryChatTriggerTest" --quiet
```
Expected: FAIL

- [ ] **Step 3: MindDiaryChatTrigger 작성**

```java
// src/main/java/com/caring/domain/chatbot/event/MindDiaryChatTrigger.java
package com.caring.domain.chatbot.event;

import com.caring.common.event.VoiceAnalysisCompletedEvent;
import com.caring.domain.chatbot.adaptor.ChatMessageAdaptor;
import com.caring.domain.chatbot.adaptor.ChatSessionAdaptor;
import com.caring.domain.chatbot.entity.ChatMessage;
import com.caring.domain.chatbot.entity.ChatSession;
import com.caring.domain.chatbot.entity.MessageOrigin;
import com.caring.domain.user.entity.User;
import com.caring.domain.voice.adaptor.VoiceAdaptor;
import com.caring.domain.voice.adaptor.VoiceCompositeAdaptor;
import com.caring.domain.voice.adaptor.VoiceEmotionLabelAdaptor;
import com.caring.domain.voice.entity.Voice;
import com.caring.domain.voice.entity.VoiceComposite;
import com.caring.domain.voice.entity.VoiceEmotionLabel;
import com.caring.infra.ai.gemini.GeminiChatbotClient;
import com.caring.infra.ai.gemini.prompts.DoranResponse;
import com.caring.infra.ai.gemini.prompts.MindDiaryPrompt;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class MindDiaryChatTrigger {

    private static final ObjectMapper OM = new ObjectMapper();

    private final VoiceAdaptor voiceAdaptor;
    private final VoiceCompositeAdaptor voiceCompositeAdaptor;
    private final VoiceEmotionLabelAdaptor voiceEmotionLabelAdaptor;
    private final ChatSessionAdaptor chatSessionAdaptor;
    private final ChatMessageAdaptor chatMessageAdaptor;
    private final GeminiChatbotClient geminiChatbotClient;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onVoiceAnalysisCompleted(VoiceAnalysisCompletedEvent event) {
        try {
            Voice voice = voiceAdaptor.queryById(event.voiceId());
            User user = voice.getUser();

            Optional<VoiceComposite> composite = voiceCompositeAdaptor
                    .queryByVoiceIds(List.of(voice.getId())).stream().findFirst();
            List<VoiceEmotionLabel> labels = voiceEmotionLabelAdaptor
                    .findByVoiceId(voice.getId());

            String emotionDesc = formatEmotionDesc(composite.orElse(null), labels);
            String emotionHint = composite
                    .map(c -> c.getTopEmotion().name().toLowerCase())
                    .orElse(null);

            String prompt = MindDiaryPrompt.build(
                    user.getName() == null ? "내담자" : user.getName(),
                    "(자유 일기)",
                    null,                       // content는 voice_content에서 가져올 수도 있으나 spec 외 — null
                    voice.getCreatedDate() == null ? "알 수 없음" : voice.getCreatedDate().toString(),
                    emotionDesc, emotionHint);

            DoranResponse llm = geminiChatbotClient.generate(prompt);
            JsonNode botResponseJson = OM.valueToTree(toMap(llm));

            ChatSession session = chatSessionAdaptor.save(ChatSession.create(user));
            chatMessageAdaptor.save(ChatMessage.builder()
                    .session(session)
                    .userInput(null)
                    .botResponse(botResponseJson)
                    .voice(voice)
                    .origin(MessageOrigin.MIND_DIARY)
                    .build());

            log.info("MindDiaryChatTrigger: created session={} for voiceId={}, user={}",
                    session.getId(), voice.getId(), user.getUsername());

        } catch (Exception e) {
            log.error("MindDiaryChatTrigger failed for voiceId={} (silent fail)",
                    event.voiceId(), e);
        }
    }

    private String formatEmotionDesc(VoiceComposite composite, List<VoiceEmotionLabel> labels) {
        if (composite == null) return "(음성 분석 미완료)";
        StringBuilder sb = new StringBuilder();
        sb.append("- 주된 감정: **").append(composite.getTopEmotion().name().toLowerCase()).append("**");
        sb.append(" (강도 bps: ").append(composite.getTopEmotionConfidenceBps()).append(")\n");
        if (labels != null && !labels.isEmpty()) {
            List<VoiceEmotionLabel> top3 = labels.stream()
                    .sorted((a, b) -> Integer.compare(b.getIntensityX1000(), a.getIntensityX1000()))
                    .limit(3)
                    .toList();
            sb.append("- 세부 감정 top: ");
            for (int i = 0; i < top3.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(top3.get(i).getLabel())
                        .append("(").append(top3.get(i).getIntensityX1000() / 1000.0).append(")");
            }
        }
        return sb.toString();
    }

    private Map<String, String> toMap(DoranResponse r) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("empathy", r.empathy());
        m.put("detected_distortion", r.detectedDistortion());
        m.put("analysis", r.analysis());
        m.put("socratic_question", r.socraticQuestion());
        m.put("alternative_thought", r.alternativeThought());
        m.put("emotion", r.topEmotion());
        return m;
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

```bash
./gradlew test --tests "com.caring.domain.chatbot.event.MindDiaryChatTriggerTest" --quiet
```
Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/caring/domain/chatbot/event/MindDiaryChatTrigger.java \
        src/test/java/com/caring/domain/chatbot/event/MindDiaryChatTriggerTest.java
git commit -m "feat(chatbot): add MindDiaryChatTrigger for AFTER_COMMIT @Async event"
```

---

## Task 19: 통합 검증 — 전체 빌드 + 테스트 + DB 스키마 확인

**Files:** 없음 (수동 검증)

- [ ] **Step 1: 전체 빌드 + 테스트**

```bash
./gradlew clean build
```
Expected: BUILD SUCCESSFUL, 모든 테스트 PASS.

실패 시:
- 컴파일 에러: 메시지 보고 import/메서드 시그니처 수정
- 테스트 실패: 해당 테스트 디버깅

- [ ] **Step 2: 로컬 실행 + DB 스키마 확인**

```bash
docker compose up -d
./gradlew bootRun
```

별도 터미널에서:
```bash
docker exec -it <mysql-container-name> mysql -u root -p caring -e "DESCRIBE chat_session; DESCRIBE chat_message; SHOW INDEX FROM chat_session; SHOW INDEX FROM chat_message;"
```

확인 사항:
- `chat_session.id` CHAR(36), PK
- `chat_session.user_id` BIGINT, FK
- `idx_chat_session_user_modified` 존재
- `chat_message.id` BIGINT, PK, AUTO_INCREMENT
- `chat_message.session_id` CHAR(36), FK
- `chat_message.bot_response` JSON NOT NULL
- `chat_message.feedback_emotion` VARCHAR(16) NULL
- `idx_chat_message_session_created` 존재

- [ ] **Step 3: 수동 API 테스트 (curl)**

세션 생성:
```bash
curl -X POST http://localhost:8080/v1/api/chatbot/sessions \
     -H "Authorization: Bearer <test-jwt>"
# Expect: { "isSuccess": true, "result": { "sessionId": "..." } }
```

텍스트 채팅:
```bash
SESSION_ID="<위에서 받은 sessionId>"
curl -X POST http://localhost:8080/v1/api/chatbot/reframing \
     -H "Authorization: Bearer <test-jwt>" \
     -H "Content-Type: application/json" \
     -d "{\"sessionId\":\"$SESSION_ID\",\"userInput\":\"오늘 너무 힘들어요\"}"
# Expect: 6개 필드 응답 (Gemini 호출 결과)
```

세션 목록·상세·피드백 PUT·세션 삭제 DELETE도 동일 패턴으로 검증.

- [ ] **Step 4: 마음일기 트리거 수동 검증**

기존 마음일기 업로드 플로우를 따라 voice 1건 등록 → 분석 완료 후 잠시(5~30초) 대기 → `GET /v1/api/chatbot/sessions` 로 신규 세션 자동 생성 확인.

로그에서:
```
INFO  Gemini analysis saved for voiceId=...
INFO  MindDiaryChatTrigger: created session=... for voiceId=...
```
두 라인이 차례로 찍히면 성공.

- [ ] **Step 5: 정리 커밋 (필요 시)**

수동 검증에서 수정 사항이 있다면 추가 커밋. 없으면 다음으로.

---

## Task 20: 기존 도란이 인프라 정리 가이드 (이번 PR엔 미포함)

**Files:** 없음 (문서 메모만)

이 task는 코드 변경이 아니라 **운영팀에 공유할 정리 가이드** 작성이다. 이번 PR과 별개로 운영 단계에서 진행.

마이그레이션 완료 후 정리해야 할 항목:
1. AWS Lambda 함수 비활성화 (`Caring_Lambda/chatbot`)
2. AWS SQS 큐 2개 삭제: `CBT_LOG_SQS_URL`, `DIARY_TO_CHATBOT_SQS_URL`
3. PostgreSQL RDS 백업 후 폐기 (`pg_dump` → S3 Glacier)
4. 환경변수 정리: `ANTHROPIC_API_KEY`, `GCP_SSM_PARAM_NAME`, `HF_*`, `CBT_LOG_SQS_URL`, `DIARY_TO_CHATBOT_SQS_URL`

이번 plan 범위 외이지만 mention.

- [ ] **Step 1: 마이그레이션 운영 가이드 메모**

`docs/superpowers/specs/2026-05-09-doran-data-model.md`의 섹션 5(데이터 이전 전략)에 이미 기재됨. 추가 변경 없음.

---

## 자가 점검 (Self-Review)

### Spec Coverage

| 설계서 섹션 | 구현 Task |
|---|---|
| 2 아키텍처 (이벤트·비동기) | Task 9, 18 |
| 3 패키지 구조 | Task 1~18 전반 |
| 4 데이터 모델 | Task 3, 4, 5 |
| 5.1 세션 생성 | Task 11 |
| 5.2 세션 삭제 | Task 12 |
| 5.3 세션 목록 | Task 13 |
| 5.4 채팅 상세 | Task 14 |
| 5.5 텍스트 채팅 | Task 15 |
| 5.6 음성 채팅 | Task 16 |
| 5.7 피드백 | Task 17 |
| 6 비동기 트리거 | Task 9, 18 |
| 7 LLM (responseSchema) | Task 8 |
| 7.3 프롬프트 (3종) | Task 7 |
| 8 권한·보안 | Task 10 (verifyOwnership) + 11~17에서 호출 |
| 9 테스트 | 모든 task에 테스트 포함 |
| 10 마이그레이션 | Task 20 (운영 가이드만) |

### Type Consistency
- `DoranEmotion` enum 코드 ↔ `bot_response.emotion` JSON 값 일치 (소문자)
- `ChatSession.id` CHAR(36) ↔ `ChatMessage.session_id` 일치
- `MessageOrigin` 3가지 ↔ Trigger/UseCase에서 사용하는 값 일치
- `DoranResponse` 6필드 ↔ `responseSchema` 6필드 ↔ `bot_response` JSON 키 일치 (`detected_distortion`, `socratic_question`, `alternative_thought`, `top_emotion` snake_case)

### 잠재 리스크

- **`Schema.builder().enum_(...)`**: SDK 버전에 따라 메서드 시그니처 차이 가능 → Task 8 Step 5에서 컴파일로 확인
- **`@OnDelete(CASCADE)`**: `chat_session` 삭제 시 `chat_message` cascade 동작이 JPA `@ManyToOne` 만으로 안 될 가능성. 필요 시 `ChatMessage.session` 위에 `@org.hibernate.annotations.OnDelete(action = OnDeleteAction.CASCADE)` 추가
- **`@Async` 활성화**: 어딘가에 `@EnableAsync` 가 이미 설정돼 있어야 함. `GeminiVoiceAnalyzer.analyzeAsync`가 동작 중이라면 이미 설정됨. 아니라면 `@SpringBootApplication` 클래스에 `@EnableAsync` 추가 필요

---

## 참고

- 설계서: `docs/superpowers/specs/2026-05-09-doran-migration-design.md`
- 데이터 모델: `docs/superpowers/specs/2026-05-09-doran-data-model.md`
- 모놀리식 컨벤션: `.claude/CLAUDE.md`
- 기존 Lambda 챗봇 코드 위치: `E:\Caring_Lambda\chatbot\`
