# Gemini Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Hume AI 음성 감정 분석 엔진을 Gemini 2.5 Flash로 교체하고, 결과를 모놀리식 서버 DB에 직접 저장한다.

**Architecture:** 음성 업로드 시 `@Async` fire-and-forget으로 Gemini 분석을 백그라운드 실행한다. S3에서 음성 파일을 다운로드하여 Gemini Files API에 업로드 후 감정 분석을 수행하고, 결과를 `VoiceComposite` 엔티티로 직접 저장한다. SQS/Lambda/Hume 의존성은 전부 제거한다.

**Tech Stack:** Spring Boot 3.4.1, Java 17, `com.google.genai:google-genai:1.52.0`, AWS SDK S3 v2 (`software.amazon.awssdk:s3`), `@Async` (AsyncConfig에 `@EnableAsync` 이미 설정됨)

---

## 파일 구조

### 신규 생성
```
src/main/java/com/caring/infra/ai/gemini/
├── config/GeminiConfig.java               — Gemini Client 빈 (@ConditionalOnExpression)
├── dto/GeminiAnalysisResult.java          — Gemini 응답 최상위 DTO (record)
├── dto/GeminiSegment.java                 — 발화 구간 DTO (record)
├── dto/GeminiEmotionScore.java            — 감정 점수 DTO (record)
├── GeminiEmotionMapping.java              — 48 라벨 → EmotionType 매핑 상수
├── GeminiEmotionMapper.java               — segments → bps 집계 + VA 계산
└── GeminiVoiceAnalyzer.java               — S3 다운 → Gemini 업 → 분석 → VoiceComposite 저장

src/test/java/com/caring/infra/ai/gemini/
├── GeminiEmotionMapperTest.java
└── GeminiVoiceAnalyzerTest.java
```

### 수정
```
common/config/S3Config.java                — S3Client 빈 추가
domain/voice/adaptor/VoiceCompositeAdaptor.java      — save() 메서드 추가
domain/voice/adaptor/VoiceCompositeAdaptorImpl.java  — save() 구현
api/voice/service/UploadVoiceFileUseCase.java        — GeminiVoiceAnalyzer로 교체
src/main/resources/application.yml         — gemini.* 추가, hume.* / sqs.* 제거
build.gradle                               — google-genai 추가, sqs 제거
```

### 제거 (Task 10)
```
infra/ai/hume/**                           — 전체 패키지 제거
infra/ai/sqs/**                            — SqsConfig, DiarySqsProducer
infra/ai/lambda/dto/DiaryPayload.java      — SQS 페이로드
infra/ai/AiServerClient.java              — 레거시 내부 AI 서버 클라이언트 (미사용)
api/voice/service/TriggerHumeAnalyzeUseCase.java
api/voice/service/PollHumeAnalyzeUseCase.java
api/voice/service/PollHumeAsyncProcessor.java
```

---

## Task 1: 의존성 및 설정 변경

**Files:**
- Modify: `build.gradle`
- Modify: `src/main/resources/application.yml`

- [ ] **Step 1: build.gradle에 Gemini SDK 추가, SQS 제거**

`build.gradle`의 dependencies 블록을 아래와 같이 수정:

```groovy
dependencies {
    // Spring Web
    implementation 'org.springframework.boot:spring-boot-starter-web'
    // Spring JPA
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    // Spring Security
    implementation 'org.springframework.boot:spring-boot-starter-security'
    // Spring Validation
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    // Spring Actuator
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    // Spring Data Redis
    implementation 'org.springframework.boot:spring-boot-starter-data-redis'
    // Spring WebFlux (WebClient - OpenAI 리포트에서 사용 중)
    implementation 'org.springframework.boot:spring-boot-starter-webflux'

    // Swagger / OpenAPI
    implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.0.2'

    // JWT
    implementation 'io.jsonwebtoken:jjwt-api:0.11.5'
    runtimeOnly 'io.jsonwebtoken:jjwt-impl:0.11.5'
    runtimeOnly 'io.jsonwebtoken:jjwt-jackson:0.11.5'

    // MapStruct
    implementation 'org.mapstruct:mapstruct:1.5.5.Final'
    annotationProcessor 'org.mapstruct:mapstruct-processor:1.5.5.Final'

    // Lombok
    compileOnly 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'
    testCompileOnly 'org.projectlombok:lombok'
    testAnnotationProcessor 'org.projectlombok:lombok'

    // FCM
    implementation 'com.google.firebase:firebase-admin:9.6.0'
    implementation 'com.google.guava:guava:32.1.2-jre'

    // Google Gemini SDK (2026-04-30 GA)
    implementation 'com.google.genai:google-genai:1.52.0'

    // AWS SDK S3 (Presigned URL + 파일 다운로드)
    implementation 'software.amazon.awssdk:s3'

    // Prometheus
    runtimeOnly 'io.micrometer:micrometer-registry-prometheus'

    // MySQL
    runtimeOnly 'com.mysql:mysql-connector-j'

    // H2 (test)
    testImplementation 'com.h2database:h2'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}
```

- [ ] **Step 2: application.yml 수정**

`hume.*`, `sqs.*` 블록 제거 후 `gemini.*` 추가:

```yaml
server:
  port: ${SERVER_PORT:8080}

spring:
  config:
    import: optional:file:.env[.properties]
  application:
    name: caring-server
  datasource:
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    driver-class-name: com.mysql.cj.jdbc.Driver
  jpa:
    hibernate:
      ddl-auto: ${JPA_DDL_AUTO:update}
    properties:
      hibernate:
        dialect: org.hibernate.dialect.MySQLDialect
        format_sql: true
    show-sql: ${JPA_SHOW_SQL:false}
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
  cloud:
    aws:
      credentials:
        access-key: ${AWS_ACCESS_KEY:}
        secret-key: ${AWS_SECRET_KEY:}
      region:
        static: ${AWS_REGION:ap-northeast-2}
      s3:
        bucket: ${AWS_S3_BUCKET:}

token:
  secret-user: ${TOKEN_SECRET_USER}

fcm:
  key:
    base64: ${FCM_KEY_BASE64:}

springdoc:
  swagger-ui:
    path: /swagger-ui.html
    enabled: true
  api-docs:
    path: /v3/api-docs

gemini:
  api-key: ${GEMINI_API_KEY:}
  model: ${GEMINI_MODEL:gemini-2.5-flash}

openai:
  api-key: ${OPENAI_API_KEY:}
  model: ${OPENAI_MODEL:gpt-4o-mini}

management:
  server:
    port: ${MANAGEMENT_SERVER_PORT:9082}
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
      base-path: /actuator
  metrics:
    tags:
      application: ${spring.application.name}
  prometheus:
    metrics:
      export:
        enabled: true
```

- [ ] **Step 3: 빌드 확인**

```bash
./gradlew dependencies --configuration compileClasspath | grep genai
```

Expected: `com.google.genai:google-genai:1.52.0` 출력

- [ ] **Step 4: Commit**

```bash
git add build.gradle src/main/resources/application.yml
git commit -m "chore: add google-genai 1.52.0, remove aws-sqs dependency"
```

---

## Task 2: Gemini 응답 DTO

**Files:**
- Create: `src/main/java/com/caring/infra/ai/gemini/dto/GeminiEmotionScore.java`
- Create: `src/main/java/com/caring/infra/ai/gemini/dto/GeminiSegment.java`
- Create: `src/main/java/com/caring/infra/ai/gemini/dto/GeminiAnalysisResult.java`

- [ ] **Step 1: GeminiEmotionScore.java 생성**

```java
package com.caring.infra.ai.gemini.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GeminiEmotionScore(
        String label,
        String category,
        double intensity
) {}
```

- [ ] **Step 2: GeminiSegment.java 생성**

```java
package com.caring.infra.ai.gemini.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record GeminiSegment(
        String timestamp,
        String text,
        List<GeminiEmotionScore> emotions,
        @JsonProperty("prosody_notes") String prosodyNotes
) {}
```

- [ ] **Step 3: GeminiAnalysisResult.java 생성**

```java
package com.caring.infra.ai.gemini.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record GeminiAnalysisResult(
        String transcript,
        String summary,
        List<GeminiSegment> segments,
        @JsonProperty("stability_score") double stabilityScore
) {}
```

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/caring/infra/ai/gemini/dto/
git commit -m "feat: add Gemini response DTOs"
```

---

## Task 3: GeminiEmotionMapping 상수 정의

**Files:**
- Create: `src/main/java/com/caring/infra/ai/gemini/GeminiEmotionMapping.java`

- [ ] **Step 1: GeminiEmotionMapping.java 생성**

48개 라벨 → EmotionType 매핑. `surprised` category 내에서 label로 FEAR/SURPRISE 세분화.

```java
package com.caring.infra.ai.gemini;

import com.caring.domain.emotion.entity.EmotionType;

import java.util.Map;
import java.util.Optional;

public final class GeminiEmotionMapping {

    // category 기반 1차 매핑 (surprised는 label로 세분화)
    private static final Map<String, EmotionType> CATEGORY_MAP = Map.of(
            "neutral",   EmotionType.NEUTRAL,
            "happy",     EmotionType.HAPPY,
            "sad",       EmotionType.SAD,
            "angry",     EmotionType.ANGRY
    );

    // surprised category 내에서 label로 FEAR vs SURPRISE 구분
    private static final Map<String, EmotionType> SURPRISED_LABEL_MAP = Map.of(
            "fear",             EmotionType.FEAR,
            "anxiety",          EmotionType.FEAR,
            "horror",           EmotionType.FEAR,
            "surprise_positive", EmotionType.SURPRISE,
            "surprise_negative", EmotionType.SURPRISE,
            "awe",              EmotionType.SURPRISE,
            "awkwardness",      EmotionType.SURPRISE
    );

    private GeminiEmotionMapping() {}

    public static Optional<EmotionType> resolve(String label, String category) {
        if ("surprised".equals(category)) {
            return Optional.ofNullable(SURPRISED_LABEL_MAP.get(label));
        }
        return Optional.ofNullable(CATEGORY_MAP.get(category));
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/caring/infra/ai/gemini/GeminiEmotionMapping.java
git commit -m "feat: add Gemini emotion label → EmotionType mapping"
```

---

## Task 4: GeminiEmotionMapper (TDD)

**Files:**
- Create: `src/main/java/com/caring/infra/ai/gemini/GeminiEmotionMapper.java`
- Create: `src/test/java/com/caring/infra/ai/gemini/GeminiEmotionMapperTest.java`

- [ ] **Step 1: 테스트 파일 작성**

```java
package com.caring.infra.ai.gemini;

import com.caring.domain.emotion.entity.EmotionType;
import com.caring.domain.voice.entity.VoiceComposite;
import com.caring.domain.voice.entity.Voice;
import com.caring.infra.ai.gemini.dto.GeminiAnalysisResult;
import com.caring.infra.ai.gemini.dto.GeminiEmotionScore;
import com.caring.infra.ai.gemini.dto.GeminiSegment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class GeminiEmotionMapperTest {

    private GeminiEmotionMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new GeminiEmotionMapper();
    }

    @Test
    @DisplayName("단일 세그먼트 happy 감정 → HAPPY topEmotion, bps 합계 10000")
    void toVoiceComposite_singleHappySegment() {
        GeminiAnalysisResult result = new GeminiAnalysisResult(
                "오늘 기분이 좋아요",
                "긍정적인 발화",
                List.of(new GeminiSegment(
                        "00:00",
                        "오늘 기분이 좋아요",
                        List.of(
                                new GeminiEmotionScore("joy", "happy", 0.8),
                                new GeminiEmotionScore("contentment", "happy", 0.6)
                        ),
                        "밝고 경쾌한 톤"
                )),
                8.0
        );
        Voice voice = mock(Voice.class);

        VoiceComposite composite = mapper.toVoiceComposite(result, voice);

        assertThat(composite.getTopEmotion()).isEqualTo(EmotionType.HAPPY);
        int total = composite.getHappyBps() + composite.getSadBps() + composite.getNeutralBps()
                  + composite.getAngryBps() + composite.getFearBps() + composite.getSurpriseBps();
        assertThat(total).isEqualTo(10000);
        assertThat(composite.getHappyBps()).isEqualTo(10000);
    }

    @Test
    @DisplayName("surprised category의 fear label → FEAR로 분류")
    void toVoiceComposite_surprisedCategory_fearLabel_mapsToFear() {
        GeminiAnalysisResult result = new GeminiAnalysisResult(
                "무서워요",
                "두려운 발화",
                List.of(new GeminiSegment(
                        "00:00",
                        "무서워요",
                        List.of(
                                new GeminiEmotionScore("fear", "surprised", 1.0),
                                new GeminiEmotionScore("anxiety", "surprised", 0.5)
                        ),
                        "떨리는 목소리"
                )),
                3.0
        );
        Voice voice = mock(Voice.class);

        VoiceComposite composite = mapper.toVoiceComposite(result, voice);

        assertThat(composite.getTopEmotion()).isEqualTo(EmotionType.FEAR);
        assertThat(composite.getFearBps()).isEqualTo(10000);
    }

    @Test
    @DisplayName("복수 세그먼트 혼합 → bps 합계 정확히 10000")
    void toVoiceComposite_multipleSegments_totalBpsIs10000() {
        GeminiAnalysisResult result = new GeminiAnalysisResult(
                "오늘은 슬프고 무서웠어요",
                "복합 감정",
                List.of(
                        new GeminiSegment("00:00", "오늘은 슬퍼요",
                                List.of(new GeminiEmotionScore("sadness", "sad", 0.7)), "낮은 톤"),
                        new GeminiSegment("00:05", "무서워요",
                                List.of(new GeminiEmotionScore("fear", "surprised", 0.3)), "떨림")
                ),
                4.0
        );
        Voice voice = mock(Voice.class);

        VoiceComposite composite = mapper.toVoiceComposite(result, voice);

        int total = composite.getHappyBps() + composite.getSadBps() + composite.getNeutralBps()
                  + composite.getAngryBps() + composite.getFearBps() + composite.getSurpriseBps();
        assertThat(total).isEqualTo(10000);
    }

    @Test
    @DisplayName("valence는 HAPPY(+0.80), NEUTRAL(0.00) 혼합 시 양수")
    void toVoiceComposite_happyAndNeutral_positiveValence() {
        GeminiAnalysisResult result = new GeminiAnalysisResult(
                "그냥저냥이에요",
                "평온",
                List.of(new GeminiSegment(
                        "00:00",
                        "그냥저냥이에요",
                        List.of(
                                new GeminiEmotionScore("joy", "happy", 0.5),
                                new GeminiEmotionScore("calmness", "neutral", 0.5)
                        ),
                        "평온한 톤"
                )),
                7.0
        );
        Voice voice = mock(Voice.class);

        VoiceComposite composite = mapper.toVoiceComposite(result, voice);

        assertThat(composite.getValenceX1000()).isGreaterThan(0);
        assertThat(composite.getIntensityX1000()).isGreaterThan(0);
    }
}
```

- [ ] **Step 2: 테스트 실행 → 실패 확인**

```bash
./gradlew test --tests "com.caring.infra.ai.gemini.GeminiEmotionMapperTest" 2>&1 | tail -20
```

Expected: FAIL (GeminiEmotionMapper 클래스 없음)

- [ ] **Step 3: GeminiEmotionMapper.java 구현**

```java
package com.caring.infra.ai.gemini;

import com.caring.domain.emotion.entity.EmotionType;
import com.caring.domain.voice.entity.Voice;
import com.caring.domain.voice.entity.VoiceComposite;
import com.caring.infra.ai.gemini.dto.GeminiAnalysisResult;
import com.caring.infra.ai.gemini.dto.GeminiEmotionScore;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

@Component
public class GeminiEmotionMapper {

    // Caring-Voice va_fusion.py의 EMOTION_VA 앵커값
    private static final Map<EmotionType, double[]> VA_ANCHOR = new EnumMap<>(EmotionType.class);

    static {
        VA_ANCHOR.put(EmotionType.HAPPY,   new double[]{+0.80, +0.60});
        VA_ANCHOR.put(EmotionType.SAD,     new double[]{-0.70, -0.40});
        VA_ANCHOR.put(EmotionType.NEUTRAL, new double[]{ 0.00,  0.00});
        VA_ANCHOR.put(EmotionType.ANGRY,   new double[]{-0.70, +0.80});
        VA_ANCHOR.put(EmotionType.FEAR,    new double[]{-0.60, +0.70});
        VA_ANCHOR.put(EmotionType.SURPRISE,new double[]{ 0.00, +0.85});
    }

    public VoiceComposite toVoiceComposite(GeminiAnalysisResult result, Voice voice) {
        Map<EmotionType, Double> intensitySum = aggregateIntensity(result);
        Map<EmotionType, Integer> bps = toBps(intensitySum);

        EmotionType topEmotion = bps.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(EmotionType.NEUTRAL);

        int[] va = computeVA(bps);

        return VoiceComposite.builder()
                .voice(voice)
                .happyBps(bps.getOrDefault(EmotionType.HAPPY, 0))
                .sadBps(bps.getOrDefault(EmotionType.SAD, 0))
                .neutralBps(bps.getOrDefault(EmotionType.NEUTRAL, 0))
                .angryBps(bps.getOrDefault(EmotionType.ANGRY, 0))
                .fearBps(bps.getOrDefault(EmotionType.FEAR, 0))
                .surpriseBps(bps.getOrDefault(EmotionType.SURPRISE, 0))
                .topEmotion(topEmotion)
                .topEmotionConfidenceBps(bps.getOrDefault(topEmotion, 0))
                .valenceX1000(va[0])
                .arousalX1000(va[1])
                .intensityX1000(va[2])
                .build();
    }

    private Map<EmotionType, Double> aggregateIntensity(GeminiAnalysisResult result) {
        Map<EmotionType, Double> sum = new EnumMap<>(EmotionType.class);
        for (var segment : result.segments()) {
            for (GeminiEmotionScore score : segment.emotions()) {
                GeminiEmotionMapping.resolve(score.label(), score.category())
                        .ifPresent(type -> sum.merge(type, score.intensity(), Double::sum));
            }
        }
        return sum;
    }

    private Map<EmotionType, Integer> toBps(Map<EmotionType, Double> intensitySum) {
        double total = intensitySum.values().stream().mapToDouble(Double::doubleValue).sum();
        if (total <= 0) {
            Map<EmotionType, Integer> fallback = new EnumMap<>(EmotionType.class);
            for (EmotionType t : EmotionType.values()) {
                fallback.put(t, 0);
            }
            fallback.put(EmotionType.NEUTRAL, 10000);
            return fallback;
        }

        Map<EmotionType, Integer> bps = new EnumMap<>(EmotionType.class);
        int assigned = 0;
        EmotionType maxType = EmotionType.NEUTRAL;
        int maxVal = -1;

        for (EmotionType type : EmotionType.values()) {
            double intensity = intensitySum.getOrDefault(type, 0.0);
            int val = (int) Math.round(intensity / total * 10000);
            bps.put(type, val);
            assigned += val;
            if (val > maxVal) { maxVal = val; maxType = type; }
        }

        // 반올림 오차 보정
        int diff = 10000 - assigned;
        bps.put(maxType, bps.get(maxType) + diff);
        return bps;
    }

    private int[] computeVA(Map<EmotionType, Integer> bps) {
        double v = 0, a = 0;
        for (EmotionType type : EmotionType.values()) {
            double weight = bps.getOrDefault(type, 0) / 10000.0;
            double[] anchor = VA_ANCHOR.get(type);
            v += weight * anchor[0];
            a += weight * anchor[1];
        }
        double intensity = Math.sqrt(v * v + a * a);
        return new int[]{
                (int) Math.round(v * 1000),
                (int) Math.round(a * 1000),
                (int) Math.round(intensity * 1000)
        };
    }
}
```

- [ ] **Step 4: 테스트 실행 → 통과 확인**

```bash
./gradlew test --tests "com.caring.infra.ai.gemini.GeminiEmotionMapperTest"
```

Expected: BUILD SUCCESSFUL, 4 tests passed

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/caring/infra/ai/gemini/GeminiEmotionMapping.java \
        src/main/java/com/caring/infra/ai/gemini/GeminiEmotionMapper.java \
        src/test/java/com/caring/infra/ai/gemini/GeminiEmotionMapperTest.java
git commit -m "feat: implement GeminiEmotionMapper with VA anchor computation"
```

---

## Task 5: S3Client 빈 추가

**Files:**
- Modify: `src/main/java/com/caring/common/config/S3Config.java`

- [ ] **Step 1: S3Config에 S3Client 빈 추가**

기존 `s3Presigner()` 빈은 유지하고 `s3Client()` 빈을 추가:

```java
package com.caring.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@ConditionalOnExpression("!'${spring.cloud.aws.credentials.access-key:}'.isEmpty()")
public class S3Config {

    @Value("${spring.cloud.aws.credentials.access-key}")
    private String accessKey;

    @Value("${spring.cloud.aws.credentials.secret-key}")
    private String secretKey;

    @Value("${spring.cloud.aws.region.static:ap-northeast-2}")
    private String region;

    @Bean
    public S3Presigner s3Presigner() {
        return S3Presigner.builder()
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .region(Region.of(region))
                .build();
    }

    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .region(Region.of(region))
                .build();
    }
}
```

- [ ] **Step 2: 빌드 확인**

```bash
./gradlew build -x test 2>&1 | tail -5
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/caring/common/config/S3Config.java
git commit -m "feat: add S3Client bean for direct file download"
```

---

## Task 6: GeminiConfig

**Files:**
- Create: `src/main/java/com/caring/infra/ai/gemini/config/GeminiConfig.java`

- [ ] **Step 1: GeminiConfig.java 생성**

GEMINI_API_KEY가 비어있으면 `Client` 빈이 생성되지 않아 로컬 개발 시 정상 기동.  
모델명은 `GeminiVoiceAnalyzer`에서 `@Value`로 직접 받는다 (String 빈 주입 불안정 방지).

```java
package com.caring.infra.ai.gemini.config;

import com.google.genai.Client;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GeminiConfig {

    @Value("${gemini.api-key:}")
    private String apiKey;

    @Bean
    @ConditionalOnExpression("!'${gemini.api-key:}'.isEmpty()")
    public Client geminiClient() {
        return new Client.Builder()
                .apiKey(apiKey)
                .build();
    }
}
```

- [ ] **Step 2: 빌드 확인**

```bash
./gradlew build -x test 2>&1 | tail -5
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/caring/infra/ai/gemini/config/GeminiConfig.java
git commit -m "feat: add GeminiConfig with conditional Client bean"
```

---

## Task 7: VoiceCompositeAdaptor save() 추가

**Files:**
- Modify: `src/main/java/com/caring/domain/voice/adaptor/VoiceCompositeAdaptor.java`
- Modify: `src/main/java/com/caring/domain/voice/adaptor/VoiceCompositeAdaptorImpl.java`

- [ ] **Step 1: VoiceCompositeAdaptor 인터페이스에 save() 추가**

```java
package com.caring.domain.voice.adaptor;

import com.caring.domain.voice.entity.VoiceComposite;

import java.time.LocalDateTime;
import java.util.List;

public interface VoiceCompositeAdaptor {

    List<VoiceComposite> queryByUsernameAndDateRange(String username, LocalDateTime start, LocalDateTime end);
    List<VoiceComposite> queryByVoiceIds(List<Long> voiceIds);
    VoiceComposite save(VoiceComposite voiceComposite);
}
```

- [ ] **Step 2: VoiceCompositeAdaptorImpl에 save() 구현**

```java
package com.caring.domain.voice.adaptor;

import com.caring.common.annotation.Adaptor;
import com.caring.domain.voice.entity.VoiceComposite;
import com.caring.domain.voice.repository.VoiceCompositeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Adaptor
@RequiredArgsConstructor
public class VoiceCompositeAdaptorImpl implements VoiceCompositeAdaptor {

    private final VoiceCompositeRepository voiceCompositeRepository;

    @Override
    @Transactional(readOnly = true)
    public List<VoiceComposite> queryByUsernameAndDateRange(String username, LocalDateTime start, LocalDateTime end) {
        return voiceCompositeRepository.findByVoice_User_UsernameAndCreatedDateBetween(username, start, end);
    }

    @Override
    @Transactional(readOnly = true)
    public List<VoiceComposite> queryByVoiceIds(List<Long> voiceIds) {
        if (voiceIds.isEmpty()) {
            return List.of();
        }
        return voiceCompositeRepository.findByVoice_IdIn(voiceIds);
    }

    @Override
    @Transactional
    public VoiceComposite save(VoiceComposite voiceComposite) {
        return voiceCompositeRepository.save(voiceComposite);
    }
}
```

- [ ] **Step 3: 빌드 확인**

```bash
./gradlew build -x test 2>&1 | tail -5
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/caring/domain/voice/adaptor/VoiceCompositeAdaptor.java \
        src/main/java/com/caring/domain/voice/adaptor/VoiceCompositeAdaptorImpl.java
git commit -m "feat: add VoiceCompositeAdaptor.save() for direct DB write"
```

---

## Task 8: GeminiVoiceAnalyzer (핵심)

**Files:**
- Create: `src/main/java/com/caring/infra/ai/gemini/GeminiVoiceAnalyzer.java`
- Create: `src/test/java/com/caring/infra/ai/gemini/GeminiVoiceAnalyzerTest.java`

- [ ] **Step 1: 테스트 파일 작성**

```java
package com.caring.infra.ai.gemini;

import com.caring.domain.voice.adaptor.VoiceAdaptor;
import com.caring.domain.voice.adaptor.VoiceCompositeAdaptor;
import com.caring.domain.voice.entity.Voice;
import com.caring.domain.voice.entity.VoiceComposite;
import com.caring.infra.ai.gemini.dto.GeminiAnalysisResult;
import com.caring.infra.ai.gemini.dto.GeminiEmotionScore;
import com.caring.infra.ai.gemini.dto.GeminiSegment;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GeminiVoiceAnalyzerTest {

    @Mock VoiceAdaptor voiceAdaptor;
    @Mock VoiceCompositeAdaptor voiceCompositeAdaptor;
    @Mock GeminiEmotionMapper emotionMapper;
    @Mock S3Client s3Client;
    @Mock Voice voice;
    @Mock VoiceComposite voiceComposite;

    private GeminiVoiceAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        // geminiClient Optional.empty() → Gemini 미설정 환경 시뮬레이션
        analyzer = new GeminiVoiceAnalyzer(
                Optional.empty(),
                "gemini-2.5-flash",
                Optional.of(s3Client),
                voiceAdaptor,
                voiceCompositeAdaptor,
                emotionMapper,
                "test-bucket",
                new ObjectMapper()
        );
    }

    @Test
    @DisplayName("Gemini 클라이언트 미설정 시 분석 건너뜀")
    void analyzeAsync_withoutGeminiClient_skipsAnalysis() {
        // when
        analyzer.analyzeAsync(1L, "voices/user/test.m4a");

        // then: VoiceComposite 저장 호출 없음
        verifyNoInteractions(voiceCompositeAdaptor);
    }

    @Test
    @DisplayName("S3 클라이언트 미설정 시 분석 건너뜀")
    void analyzeAsync_withoutS3Client_skipsAnalysis() {
        GeminiVoiceAnalyzer analyzerWithoutS3 = new GeminiVoiceAnalyzer(
                Optional.of(mock(Client.class)),
                "gemini-2.5-flash",
                Optional.empty(),
                voiceAdaptor,
                voiceCompositeAdaptor,
                emotionMapper,
                "test-bucket",
                new ObjectMapper()
        );

        analyzerWithoutS3.analyzeAsync(1L, "voices/user/test.m4a");

        verifyNoInteractions(voiceCompositeAdaptor);
    }
}
```

- [ ] **Step 2: 테스트 실행 → 실패 확인**

```bash
./gradlew test --tests "com.caring.infra.ai.gemini.GeminiVoiceAnalyzerTest" 2>&1 | tail -20
```

Expected: FAIL (GeminiVoiceAnalyzer 클래스 없음)

- [ ] **Step 3: GeminiVoiceAnalyzer.java 구현**

```java
package com.caring.infra.ai.gemini;

import com.caring.domain.voice.adaptor.VoiceAdaptor;
import com.caring.domain.voice.adaptor.VoiceCompositeAdaptor;
import com.caring.domain.voice.entity.Voice;
import com.caring.domain.voice.entity.VoiceComposite;
import com.caring.infra.ai.gemini.dto.GeminiAnalysisResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.types.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
public class GeminiVoiceAnalyzer {

    private static final String PROMPT = """
            당신은 감정 분석 AI입니다. 사용자의 한국어 음성을 분석하여 JSON으로 응답하세요.

            규칙:
            - transcript: 정확한 한국어 전사
            - summary: 전체 발화의 2-3문장 요약
            - segments: 자연스러운 발화 단위로 분할 (최소 1, 최대 10구간)
              - timestamp: MM:SS 형식 (예: "00:15")
              - text: 해당 구간 전사 텍스트
              - emotions: 상위 5개 감정, intensity는 0.0-1.0
              - category: label에 해당하는 대분류 (neutral/happy/sad/angry/surprised)
              - prosody_notes: 톤/속도/억양 관찰 (한국어 1문장)
            - stability_score: 0(극도로 불안정) ~ 10(매우 안정적). 화자의 전반적 감정 안정성 기준.

            감정 label은 반드시 아래 목록에서만 선택:
            [neutral] calmness, contemplation, concentration, interest, realization, boredom, tiredness, confusion, doubt, nostalgia
            [happy] joy, ecstasy, contentment, satisfaction, amusement, excitement, pride, triumph, relief, admiration, adoration, love, romance, entrancement, aesthetic_appreciation, determination
            [sad] sadness, distress, disappointment, guilt, shame, embarrassment, empathic_pain, sympathy, loneliness
            [angry] anger, contempt, disgust, frustration, envy, craving
            [surprised] surprise_positive, surprise_negative, awe, horror, fear, anxiety, awkwardness
            """;

    private final Optional<Client> geminiClient;
    private final String modelName;
    private final Optional<S3Client> s3Client;
    private final VoiceAdaptor voiceAdaptor;
    private final VoiceCompositeAdaptor voiceCompositeAdaptor;
    private final GeminiEmotionMapper emotionMapper;
    private final String s3Bucket;
    private final ObjectMapper objectMapper;

    public GeminiVoiceAnalyzer(
            Optional<Client> geminiClient,
            @Value("${gemini.model:gemini-2.5-flash}") String modelName,
            Optional<S3Client> s3Client,
            VoiceAdaptor voiceAdaptor,
            VoiceCompositeAdaptor voiceCompositeAdaptor,
            GeminiEmotionMapper emotionMapper,
            @Value("${spring.cloud.aws.s3.bucket:}") String s3Bucket,
            ObjectMapper objectMapper) {
        this.geminiClient = geminiClient;
        this.modelName = modelName;
        this.s3Client = s3Client;
        this.voiceAdaptor = voiceAdaptor;
        this.voiceCompositeAdaptor = voiceCompositeAdaptor;
        this.emotionMapper = emotionMapper;
        this.s3Bucket = s3Bucket;
        this.objectMapper = objectMapper;
    }

    @Async
    public void analyzeAsync(Long voiceId, String voiceKey) {
        if (geminiClient.isEmpty() || s3Client.isEmpty()) {
            log.debug("Gemini or S3 client not configured, skipping analysis for voiceId={}", voiceId);
            return;
        }

        try {
            Voice voice = voiceAdaptor.queryById(voiceId);
            byte[] audioBytes = downloadFromS3(voiceKey);
            String analysisJson = analyzeWithGemini(audioBytes, voiceKey);
            GeminiAnalysisResult result = objectMapper.readValue(analysisJson, GeminiAnalysisResult.class);
            VoiceComposite composite = emotionMapper.toVoiceComposite(result, voice);
            voiceCompositeAdaptor.save(composite);
            log.info("Gemini analysis saved for voiceId={}, topEmotion={}", voiceId, composite.getTopEmotion());
        } catch (Exception e) {
            log.error("Gemini analysis failed for voiceId={}, voiceKey={}", voiceId, voiceKey, e);
        }
    }

    private byte[] downloadFromS3(String voiceKey) {
        ResponseBytes<GetObjectResponse> responseBytes = s3Client.get().getObjectAsBytes(
                GetObjectRequest.builder()
                        .bucket(s3Bucket)
                        .key(voiceKey)
                        .build()
        );
        return responseBytes.asByteArray();
    }

    private String analyzeWithGemini(byte[] audioBytes, String voiceKey) throws IOException {
        Client client = geminiClient.get();
        String mimeType = resolveMimeType(voiceKey);

        Path tempFile = Files.createTempFile("gemini_voice_", extractExtension(voiceKey));
        try {
            Files.write(tempFile, audioBytes);

            File uploaded = client.files().upload(
                    tempFile,
                    UploadFileConfig.builder().mimeType(mimeType).build()
            );

            Schema emotionSchema = Schema.builder()
                    .type(Type.Known.OBJECT)
                    .properties(Map.of(
                            "label",    Schema.builder().type(Type.Known.STRING).build(),
                            "category", Schema.builder().type(Type.Known.STRING).build(),
                            "intensity",Schema.builder().type(Type.Known.NUMBER).build()
                    ))
                    .required(List.of("label", "category", "intensity"))
                    .build();

            Schema segmentSchema = Schema.builder()
                    .type(Type.Known.OBJECT)
                    .properties(Map.of(
                            "timestamp",     Schema.builder().type(Type.Known.STRING).build(),
                            "text",          Schema.builder().type(Type.Known.STRING).build(),
                            "emotions",      Schema.builder().type(Type.Known.ARRAY).items(emotionSchema).build(),
                            "prosody_notes", Schema.builder().type(Type.Known.STRING).build()
                    ))
                    .required(List.of("timestamp", "text", "emotions", "prosody_notes"))
                    .build();

            Schema responseSchema = Schema.builder()
                    .type(Type.Known.OBJECT)
                    .properties(Map.of(
                            "transcript",      Schema.builder().type(Type.Known.STRING).build(),
                            "summary",         Schema.builder().type(Type.Known.STRING).build(),
                            "segments",        Schema.builder().type(Type.Known.ARRAY).items(segmentSchema).build(),
                            "stability_score", Schema.builder().type(Type.Known.NUMBER).build()
                    ))
                    .required(List.of("transcript", "summary", "segments", "stability_score"))
                    .build();

            float temperature = 0.2f;
            GenerateContentResponse response = client.models().generateContent(
                    modelName,
                    Content.builder()
                            .role("user")
                            .parts(List.of(
                                    Part.builder().text(PROMPT).build(),
                                    Part.builder().fileData(
                                            FileData.builder()
                                                    .fileUri(uploaded.uri().orElseThrow())
                                                    .mimeType(mimeType)
                                                    .build()
                                    ).build()
                            ))
                            .build(),
                    GenerateContentConfig.builder()
                            .responseMimeType("application/json")
                            .responseSchema(responseSchema)
                            .temperature(temperature)
                            .build()
            );

            String text = response.text();
            if (text == null || text.isBlank()) {
                throw new IllegalStateException("Empty response from Gemini");
            }
            return text;
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    private String resolveMimeType(String voiceKey) {
        String lower = voiceKey.toLowerCase();
        if (lower.endsWith(".wav")) return "audio/wav";
        if (lower.endsWith(".m4a")) return "audio/mp4";
        return "audio/mpeg";
    }

    private String extractExtension(String voiceKey) {
        int dot = voiceKey.lastIndexOf('.');
        return dot >= 0 ? voiceKey.substring(dot) : ".bin";
    }
}
```

> **참고:** SDK `com.google.genai:google-genai:1.52.0`의 정확한 타입 경로(`Type.Known`, `Part.builder()` 등)는 컴파일 오류 시 SDK javadoc/소스를 참조해 수정한다. 핵심 흐름(upload → generateContent → text())은 동일.

- [ ] **Step 4: 테스트 실행 → 통과 확인**

```bash
./gradlew test --tests "com.caring.infra.ai.gemini.GeminiVoiceAnalyzerTest"
```

Expected: BUILD SUCCESSFUL, 2 tests passed

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/caring/infra/ai/gemini/GeminiVoiceAnalyzer.java \
        src/test/java/com/caring/infra/ai/gemini/GeminiVoiceAnalyzerTest.java
git commit -m "feat: implement GeminiVoiceAnalyzer with @Async S3→Gemini→VoiceComposite flow"
```

---

## Task 9: UploadVoiceFileUseCase 교체

**Files:**
- Modify: `src/main/java/com/caring/api/voice/service/UploadVoiceFileUseCase.java`
- Modify: `src/test/java/com/caring/api/voice/service/UploadVoiceFileUseCaseTest.java`

- [ ] **Step 1: UploadVoiceFileUseCase.java 수정**

`HumeBatchScheduler` → `GeminiVoiceAnalyzer` 교체. voiceKey를 직접 넘겨 presigned URL 생성 없이 동작:

```java
package com.caring.api.voice.service;

import com.caring.common.annotation.UseCase;
import com.caring.common.consts.UserServiceQuestionStaticValues;
import com.caring.domain.question.entity.QuestionCategory;
import com.caring.domain.question.exception.QuestionHandler;
import com.caring.domain.user.adaptor.UserAdaptor;
import com.caring.domain.user.entity.User;
import com.caring.domain.voice.service.VoiceDomainService;
import com.caring.domain.voice.entity.Voice;
import com.caring.infra.ai.gemini.GeminiVoiceAnalyzer;

import java.util.List;

@UseCase
public class UploadVoiceFileUseCase {

    private final UserAdaptor userAdaptor;
    private final VoiceDomainService voiceDomainService;
    private final GeminiVoiceAnalyzer geminiVoiceAnalyzer;

    public UploadVoiceFileUseCase(UserAdaptor userAdaptor,
                                  VoiceDomainService voiceDomainService,
                                  GeminiVoiceAnalyzer geminiVoiceAnalyzer) {
        this.userAdaptor = userAdaptor;
        this.voiceDomainService = voiceDomainService;
        this.geminiVoiceAnalyzer = geminiVoiceAnalyzer;
    }

    public Long execute(String username, QuestionCategory questionCategory, int questionIndex,
                        String voiceKey, String recordedAt) {
        validateQuestion(questionCategory, questionIndex);
        User user = userAdaptor.queryUserByUsername(username);
        Voice voice = voiceDomainService.uploadVoiceFile(user, voiceKey);
        voiceDomainService.linkVoiceQuestion(voice, questionCategory, questionIndex);

        // fire-and-forget: 기존 Hume 방식과 동일하게 즉시 반환 후 백그라운드 분석
        geminiVoiceAnalyzer.analyzeAsync(voice.getId(), voiceKey);

        return voice.getId();
    }

    private void validateQuestion(QuestionCategory questionCategory, int questionIndex) {
        if (questionCategory == null) {
            throw QuestionHandler.NOT_FOUND;
        }
        List<String> questions = UserServiceQuestionStaticValues.QUESTION_MAP.get(questionCategory.name());
        if (questions == null || questionIndex < 0 || questionIndex >= questions.size()) {
            throw QuestionHandler.NOT_FOUND;
        }
    }
}
```

- [ ] **Step 2: UploadVoiceFileUseCaseTest.java 수정**

```java
package com.caring.api.voice.service;

import com.caring.domain.question.entity.QuestionCategory;
import com.caring.domain.user.adaptor.UserAdaptor;
import com.caring.domain.user.entity.User;
import com.caring.domain.voice.entity.Voice;
import com.caring.domain.voice.service.VoiceDomainService;
import com.caring.infra.ai.gemini.GeminiVoiceAnalyzer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UploadVoiceFileUseCaseTest {

    @Mock UserAdaptor userAdaptor;
    @Mock VoiceDomainService voiceDomainService;
    @Mock GeminiVoiceAnalyzer geminiVoiceAnalyzer;
    @Mock User user;
    @Mock Voice voice;

    @Test
    @DisplayName("업로드 성공 - Gemini 분석 voiceId와 voiceKey 전달")
    void execute_triggersGeminiWithVoiceIdAndKey() {
        String username = "testUser";
        String voiceKey = "voices/testUser/uuid.m4a";
        QuestionCategory category = QuestionCategory.EMOTION;

        given(userAdaptor.queryUserByUsername(username)).willReturn(user);
        given(voiceDomainService.uploadVoiceFile(user, voiceKey)).willReturn(voice);
        given(voice.getId()).willReturn(1L);

        UploadVoiceFileUseCase useCase = new UploadVoiceFileUseCase(
                userAdaptor, voiceDomainService, geminiVoiceAnalyzer);

        Long voiceId = useCase.execute(username, category, 0, voiceKey, "2026-04-04T00:00:00");

        assertThat(voiceId).isEqualTo(1L);
        verify(geminiVoiceAnalyzer).analyzeAsync(1L, voiceKey);
    }
}
```

- [ ] **Step 3: 테스트 실행 → 통과 확인**

```bash
./gradlew test --tests "com.caring.api.voice.service.UploadVoiceFileUseCaseTest"
```

Expected: BUILD SUCCESSFUL, 1 test passed

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/caring/api/voice/service/UploadVoiceFileUseCase.java \
        src/test/java/com/caring/api/voice/service/UploadVoiceFileUseCaseTest.java
git commit -m "feat: replace HumeBatchScheduler with GeminiVoiceAnalyzer in UploadVoiceFileUseCase"
```

---

## Task 10: Hume / SQS 코드 제거

**Files:** 하기 파일 및 디렉토리 전체 삭제

- [ ] **Step 1: Hume 관련 파일 삭제**

```bash
git rm -r src/main/java/com/caring/infra/ai/hume/
git rm -r src/main/java/com/caring/infra/ai/sqs/
git rm -r src/main/java/com/caring/infra/ai/lambda/
git rm src/main/java/com/caring/infra/ai/AiServerClient.java
git rm src/main/java/com/caring/api/voice/service/TriggerHumeAnalyzeUseCase.java
git rm src/main/java/com/caring/api/voice/service/PollHumeAnalyzeUseCase.java
git rm src/main/java/com/caring/api/voice/service/PollHumeAsyncProcessor.java
```

- [ ] **Step 2: Hume 관련 테스트 파일 삭제**

```bash
git rm -r src/test/java/com/caring/infra/ai/hume/
git rm src/test/java/com/caring/api/voice/service/TriggerHumeAnalyzeUseCaseTest.java
```

- [ ] **Step 3: 빌드 확인 (컴파일 오류 없어야 함)**

```bash
./gradlew build -x test 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL (삭제된 파일을 참조하는 import가 없어야 함)

만약 컴파일 오류 발생 시: `grep -r "hume\|HumeBatch\|DiaryBatch\|DiarySqs\|AiServerClient" src/main/java` 로 잔존 참조를 찾아 제거.

- [ ] **Step 4: 전체 테스트 통과 확인**

```bash
./gradlew test 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git commit -m "chore: remove Hume AI, SQS, Lambda integration code"
```

---

## Task 11: 통합 확인

- [ ] **Step 1: 전체 빌드**

```bash
./gradlew build 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 2: .env에 GEMINI_API_KEY 추가 후 로컬 실행**

`.env` 파일에 추가:
```
GEMINI_API_KEY=AIza...
```

```bash
docker compose up --build
```

- [ ] **Step 3: 음성 업로드 후 로그 확인**

음성 파일을 업로드하는 API를 호출하고 다음 로그가 출력되는지 확인:

```
Gemini analysis saved for voiceId=X, topEmotion=HAPPY
```

또는 미설정 환경이라면:
```
Gemini or S3 client not configured, skipping analysis for voiceId=X
```

- [ ] **Step 4: VoiceComposite DB 저장 확인**

```sql
SELECT * FROM voice_composite ORDER BY created_date DESC LIMIT 1;
```

Expected: 분석 결과가 저장되어 있음 (happy_bps + sad_bps + ... = 10000)

- [ ] **Step 5: 기존 API 형상 확인**

```bash
curl -H "Authorization: Bearer {token}" \
  "http://localhost:8080/v1/api/users/voices/analyzing/monthly?month=2026-05"
```

Expected: 200 OK, 기존과 동일한 응답 구조

- [ ] **Step 6: Final commit**

```bash
git add .
git commit -m "feat: complete Gemini migration — Hume AI replaced, VoiceComposite saved directly"
```
