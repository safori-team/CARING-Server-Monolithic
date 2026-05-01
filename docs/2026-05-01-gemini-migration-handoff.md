# Gemini 마이그레이션 핸드오프 문서

> **작성일:** 2026-05-01  
> **대상:** 이 브랜치를 이어받는 다음 AI / 개발자  
> **브랜치:** `feat/gemini-migration`  
> **워크트리 경로:** `E:\CARING-Server-Monolithic\.worktrees\feat-gemini-migration`

---

## 1. 작업 배경

Hume AI가 2026년 6월부로 API 서비스를 종료함에 따라,  
CARING Server의 음성 감정 분석 파이프라인을 **Google Gemini API**로 전면 교체했다.

**핵심 제약:**
- 외부 API 형상(엔드포인트, 요청/응답 DTO) 변경 없음
- SQS 미구현 (추후 Phase에서 chatbot과 함께 이전 예정)
- S3 다운로드 → Gemini Files API 업로드 방식
- 기존 Hume의 fire-and-forget async 패턴 유지

---

## 2. 현재 상태: 완료된 작업

### 브랜치 커밋 이력 (main 기준)
```
e7b7fa7 feat: replace Hume AI with Gemini API for voice emotion analysis
eb4e18b feat: add Gemini emotion label to EmotionType mapping
948fefc feat: add Gemini response DTOs
a071df9 chore: add google-genai 1.52.0, remove aws-sqs dependency
```

### 빌드 / 테스트 상태
- `./gradlew test` → **BUILD SUCCESSFUL** (모든 테스트 통과)
- 컴파일 에러 없음 (경고 3개: 기존 `VoiceJobProcess` @SuperBuilder 이슈, main 브랜치부터 존재)

---

## 3. 변경 파일 전체 목록

### 삭제된 파일
| 경로 | 설명 |
|------|------|
| `infra/ai/hume/**` | Hume AI 전체 패키지 (callback, client, config, dto, mapper, scheduler) |
| `infra/ai/sqs/SqsConfig.java` | SQS 설정 |
| `infra/ai/sqs/DiarySqsProducer.java` | SQS 전송 |
| `infra/ai/sqs/SqsSendException.java` | SQS 예외 |
| `infra/ai/lambda/dto/DiaryPayload.java` | Lambda 페이로드 DTO |
| `infra/ai/AiServerClient.java` | AI 서버 클라이언트 |
| `api/voice/service/TriggerHumeAnalyzeUseCase.java` | Hume 수동 트리거 use case |
| `api/voice/service/PollHumeAnalyzeUseCase.java` | Hume polling use case |
| `api/voice/service/PollHumeAsyncProcessor.java` | Hume polling 비동기 처리 |
| `api/voice/controller/AdminVoiceController.java` | Hume 어드민 엔드포인트 |
| `test/.../TriggerHumeAnalyzeUseCaseTest.java` | Hume 트리거 테스트 |
| `test/.../HumeBatchSchedulerTest.java` | Hume 스케줄러 테스트 |

### 생성된 파일
| 경로 | 설명 |
|------|------|
| `infra/ai/gemini/dto/GeminiAnalysisResult.java` | Gemini 응답 최상위 DTO |
| `infra/ai/gemini/dto/GeminiSegment.java` | 발화 구간 DTO |
| `infra/ai/gemini/dto/GeminiEmotionScore.java` | 감정 점수 DTO |
| `infra/ai/gemini/GeminiEmotionMapping.java` | Gemini label → EmotionType 정적 매핑 |
| `infra/ai/gemini/GeminiEmotionMapper.java` | BPS 집계 + VA Fusion 컴포넌트 |
| `infra/ai/gemini/GeminiVoiceAnalyzer.java` | S3 다운로드 → Gemini 분석 → DB 저장 |
| `infra/ai/gemini/config/GeminiConfig.java` | Gemini Client 빈 설정 |
| `test/.../GeminiEmotionMapperTest.java` | EmotionMapper 단위 테스트 4종 |

### 수정된 파일
| 경로 | 변경 내용 |
|------|-----------|
| `build.gradle` | `spring-cloud-aws-starter-sqs` 제거, `google-genai:1.52.0` + `aws-sdk bom:2.26.22` 추가 |
| `resources/application.yml` | `hume.*`, `sqs.*`, `ai-service` 블록 제거, `gemini.api-key` / `gemini.model` 추가 |
| `CaringServerApplication.java` | Spring Cloud AWS autoconfigure exclude 제거 (의존성 삭제됨) |
| `common/config/S3Config.java` | `S3Client` 빈 추가 (기존 `S3Presigner`와 함께) |
| `domain/voice/adaptor/VoiceCompositeAdaptor.java` | `save(VoiceComposite)` 인터페이스 추가 |
| `domain/voice/adaptor/VoiceCompositeAdaptorImpl.java` | `save()` 구현 + 트랜잭션 메서드 레벨로 이동 |
| `api/voice/service/UploadVoiceFileUseCase.java` | `HumeBatchScheduler` → `GeminiVoiceAnalyzer.analyzeAsync()` 교체, `S3PresignService` 제거 |
| `test/.../UploadVoiceFileUseCaseTest.java` | Gemini 기반으로 테스트 전면 재작성 |

---

## 4. 아키텍처 요약

### 음성 분석 흐름

```
[클라이언트]
    │
    ▼
POST /v1/api/voices (VoiceApiController)
    │
    ▼
UploadVoiceFileUseCase.execute()
    ├── voiceDomainService.uploadVoiceFile()  → DB에 Voice 레코드 저장
    ├── voiceDomainService.linkVoiceQuestion()
    └── geminiVoiceAnalyzer.analyzeAsync(voiceId, voiceKey)  ← @Async, 즉시 반환
            │
            ▼  (별도 스레드)
    [GeminiVoiceAnalyzer]
    ├── S3Client.getObjectAsBytes(voiceKey)       ← S3에서 오디오 다운로드
    ├── client.files.upload(tempFile, mimeType)   ← Gemini Files API 업로드
    ├── client.models.generateContent(...)         ← Gemini 2.5 Flash 분석 요청
    │       응답: transcript, summary, segments[], stability_score
    ├── objectMapper.readValue() → GeminiAnalysisResult
    ├── GeminiEmotionMapper.toVoiceComposite()    ← 감정 BPS + VA 계산
    └── voiceCompositeAdaptor.save()              ← DB 저장
```

### 감정 매핑 구조

Gemini는 48개 label, 5개 category(neutral/happy/sad/angry/surprised)를 반환.  
`surprised` category는 label로 FEAR/SURPRISE로 분기:

```
category "surprised" + label "fear"/"anxiety"/"horror"    → EmotionType.FEAR
category "surprised" + label "surprise_*"/"awe"/"awkwardness" → EmotionType.SURPRISE
category "neutral"  → EmotionType.NEUTRAL
category "happy"    → EmotionType.HAPPY
category "sad"      → EmotionType.SAD
category "angry"    → EmotionType.ANGRY
```

### VoiceComposite BPS 계산

- 전체 segment의 emotion intensity를 EmotionType별로 합산
- 총합 대비 비율로 BPS(basis points, 합계 10000) 환산
- 반올림 오차는 최대 BPS 항목에 보정

### VA Fusion (Caring-Voice va_fusion.py 기반)

```
VA Anchor:
  HAPPY    → [ +0.80, +0.60 ]
  SAD      → [ -0.70, -0.40 ]
  NEUTRAL  → [  0.00,  0.00 ]
  ANGRY    → [ -0.70, +0.80 ]
  FEAR     → [ -0.60, +0.70 ]
  SURPRISE → [  0.00, +0.85 ]

valence  = Σ(bps_i / 10000 × V_anchor_i)  → ×1000 → int
arousal  = Σ(bps_i / 10000 × A_anchor_i)  → ×1000 → int
intensity = √(valence² + arousal²)          → ×1000 → int
```

---

## 5. 주요 설정 / 환경변수

### `.env` 추가 필요
```
GEMINI_API_KEY=<your-google-ai-studio-key>
# 선택사항 (기본값: gemini-2.5-flash)
GEMINI_MODEL=gemini-2.5-flash
```

### `application.yml` 현재 구조
```yaml
gemini:
  api-key: ${GEMINI_API_KEY:}
  model: ${GEMINI_MODEL:gemini-2.5-flash}
```

### 빈 조건부 생성
- `GeminiConfig`: `GEMINI_API_KEY`가 비어있으면 `Client` 빈 미생성
- `S3Config`: `AWS_ACCESS_KEY`가 비어있으면 `S3Client` 빈 미생성
- `GeminiVoiceAnalyzer.analyzeAsync()`: 두 빈 중 하나라도 없으면 분석 건너뜀(로그만)
- → **로컬 환경에서 키 없이도 서버 정상 기동됨**

---

## 6. SDK 주의사항 (`google-genai:1.52.0`)

```java
// ❌ 틀림 — 메서드가 아님
client.files().upload(...)
client.models().generateContent(...)

// ✅ 맞음 — public 필드 접근
client.files.upload(tempFile.toFile(), UploadFileConfig.builder().mimeType(mimeType).build())
client.models.generateContent(modelName, content, config)

// upload의 첫 번째 인수는 java.io.File (Path 아님)
// Path → Path.toFile() 변환 필요
```

---

## 7. 남은 작업

### 즉시 가능
- [ ] **PR 생성**: `feat/gemini-migration` → `main`
  - `docs/2026-05-01-gemini-migration-design.md` 설계서 첨부
  - API 형상 무변경 명시
- [ ] **통합 테스트**: 실제 `GEMINI_API_KEY` 설정 후 음성 업로드 E2E 확인
  - DB `voice_composite` 테이블에 데이터 저장되는지 확인
  - `top_emotion`, `valence_x1000`, `arousal_x1000` 값 정상 여부 확인

### 추후 Phase (별도 브랜치)
- [ ] **Lambda chatbot 이전**: `Caring_Lambda`의 chatbot 기능을 모놀리식으로 이전
  - 현재 Lambda에서: 텍스트 분석 + AI 챗봇 응답 생성
  - SQS 파이프라인 이 서버로 흡수 예정
- [ ] `VoiceJobProcess` @SuperBuilder 경고 해소 (기존 코드 문제, 별도 이슈)

---

## 8. 관련 문서

| 문서 | 경로 |
|------|------|
| 설계서 (Hume→Gemini 설계 상세) | `docs/2026-05-01-gemini-migration-design.md` |
| 구현 플랜 (11 task) | `docs/superpowers/plans/2026-05-01-gemini-migration.md` |
| Gemini SDK GitHub | https://github.com/googleapis/java-genai |

---

## 9. 빠른 시작 명령어

```bash
# 워크트리로 이동
cd E:\CARING-Server-Monolithic\.worktrees\feat-gemini-migration

# 빌드 확인
./gradlew build -x test

# 테스트 실행
./gradlew test

# 현재 브랜치 확인
git log --oneline -5
```
