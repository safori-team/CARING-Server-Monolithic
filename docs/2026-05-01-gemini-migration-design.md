# Hume AI → Gemini API 마이그레이션 설계서

**작성일:** 2026-05-01  
**배경:** Hume AI의 Batch API가 2026년 6월 이후 종료됨에 따라 음성 감정 분석 엔진을 Gemini 2.5 Flash로 교체  
**원칙:** 외부 API 형상 유지 (Controller, DTO, 엔드포인트 URL 변경 없음)

---

## 1. 히스토리

```
[1세대] Caring-Voice (Python)
  - 로컬 AI 서버 (Wav2Vec2 한국어 음성 모델 + Google NLP)
  - VA Fusion 알고리즘으로 valence/arousal/intensity 계산
  - 비용 및 성능 한계로 폐기

[2세대] Spring Boot + Hume AI (현재)
  - Hume Batch API (prosody + burst + language 3개 모델)
  - 비동기 배치 큐 → Hume이 콜백 → SQS → Lambda(chatbot) → cbt_logs 저장
  - VoiceComposite 저장: 실질적으로 미구현 (더미 데이터만 존재)
  - 2026년 6월 Hume API 종료 예정

[3세대] Spring Boot + Gemini 2.5 Flash (목표)
  - 단일 Gemini 호출로 STT + 감정 분석 통합
  - SQS/Lambda 제거, 모놀리식 서버에서 직접 VoiceComposite 저장
  - 동일 비동기 fire-and-forget 패턴 유지
```

---

## 2. 현재 아키텍처 (Hume)

```
POST /v1/api/users/voices (음성 업로드)
  ↓
UploadVoiceFileUseCase
  ├─ Voice 엔티티 저장
  ├─ VoiceQuestion 링크
  └─ HumeBatchScheduler.enqueue() ← fire-and-forget, 즉시 반환
       ↓ (60초마다 flush)
  Hume Batch API POST /batch/jobs
  (prosody + burst + language + STT)
       ↓ (비동기 콜백)
  POST /v1/api/hume/callback ← HumeCallbackController
       ↓
  HumeResultMapper (EmotionAnalysis + EmotionCategoryResult)
       ↓
  DiaryPayload → SQS → Lambda → cbt_logs 저장
       (VoiceComposite 저장은 미구현)
```

**유지되는 조회 API:**
- `GET /v1/api/users/voices/analyzing/monthly`
- `GET /v1/api/users/voices/analyzing/weekly`
- `GET /v1/api/users/voices`
- `GET /v1/api/users/voices/{voiceId}`

---

## 3. 신규 아키텍처 (Gemini)

```
POST /v1/api/users/voices (음성 업로드) ← 형상 유지
  ↓
UploadVoiceFileUseCase
  ├─ Voice 엔티티 저장
  ├─ VoiceQuestion 링크
  └─ GeminiVoiceAnalyzer.analyzeAsync(voiceId, voiceKey, ...) ← @Async, fire-and-forget
       즉시 voiceId 반환 (기존과 동일)

[백그라운드 @Async 스레드]
  GeminiVoiceAnalyzer.analyzeAsync()
  1. S3에서 음성 파일 다운로드 (InputStream)
  2. Gemini Files API 업로드 → fileUri 반환
  3. GenerateContent(gemini-2.5-flash, prompt + fileUri)
     - ResponseMIMEType: application/json
     - ResponseSchema: 감정 분석 스키마 강제
  4. 응답 파싱 (transcript + segments[emotions])
  5. GeminiEmotionMapper: 48 라벨 → 6 EmotionType bps 집계
  6. VA 계산 (Caring-Voice 앵커값 기반)
  7. VoiceCompositeAdaptor.save() ← 모놀리식 서버 DB 직접 저장
  8. VoiceContentAdaptor.save() ← STT 텍스트 저장 (있는 경우)
```

**제거 대상:**
- `POST /v1/api/hume/callback` 엔드포인트 (외부 콜백 불필요)
- SQS 연동 (DiarySqsProducer, SqsConfig)
- HumeBatchScheduler, HumeBatchClient
- DiaryBatchItem, DiaryPayload

---

## 4. Gemini 호출 상세

### 4.1 사용 모델 및 설정
```
모델: gemini-2.5-flash
Temperature: 0.2 (낮은 창의성, 일관된 분석)
ResponseMIMEType: application/json
ResponseSchema: 구조화 강제
```

### 4.2 프롬프트 (toy 프로젝트 `fullPrompt` 그대로 이식)
```
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
```

### 4.3 S3 → Gemini 파일 흐름
```
S3 Object (voiceKey)
  ↓ AmazonS3.getObject()
InputStream
  ↓ 임시 파일 또는 메모리 버퍼
Gemini Files.uploadFromBytes() or uploadFromPath()
  ↓
fileUri (generativelanguage.googleapis.com/...)
  ↓ GenerateContent에서 참조
```

### 4.4 응답 스키마
```json
{
  "transcript": "string",
  "stability_score": "number",
  "segments": [
    {
      "timestamp": "string",
      "text": "string",
      "emotions": [
        { "label": "joy", "category": "happy", "intensity": 0.8 }
      ]
    }
  ]
}
```

---

## 5. 감정 매핑

### 5.1 Gemini 48 라벨 → 6 EmotionType

Hume의 `HumeEmotionMapping`과 동일한 구조로 구현.  
toy 프로젝트의 카테고리 필드(`category`)를 1차 매핑 기준으로 활용.

| Gemini category | EmotionType | 비고 |
|---|---|---|
| `happy` | HAPPY | joy, ecstasy, contentment 등 16개 |
| `sad` | SAD | sadness, distress, guilt 등 9개 |
| `angry` | ANGRY | anger, contempt, disgust 등 6개 |
| `surprised` | FEAR / SURPRISE | label로 세분화 |
| `neutral` | NEUTRAL | calmness, concentration 등 10개 |

**FEAR vs SURPRISE 세분화 (surprised category 내):**
- FEAR: `fear`, `anxiety`, `horror`
- SURPRISE: `surprise_positive`, `surprise_negative`, `awe`, `awkwardness`

### 5.2 BPS 집계 방식
```
각 segment의 emotions에서 label별 intensity를 EmotionType으로 그룹핑
→ EmotionType별 intensity 합산
→ 전체 합 대비 비율 × 10000 = bps
→ 반올림 오차 보정 (합계 = 10000 보증)
```

---

## 6. VoiceComposite 저장 계산

### 6.1 감정 BPS (Gemini → DB)
```
happyBps  = HAPPY  그룹 bps
sadBps    = SAD    그룹 bps
neutralBps= NEUTRAL그룹 bps
angryBps  = ANGRY  그룹 bps
fearBps   = FEAR   그룹 bps
surpriseBps= SURPRISE그룹 bps
(합계 = 10000)

topEmotion = bps 최대값의 EmotionType
topEmotionConfidenceBps = 해당 bps 값
```

### 6.2 Valence / Arousal / Intensity (Caring-Voice 앵커값 재사용)
```java
// 감정별 VA 앵커 (Caring-Voice va_fusion.py 출처)
Map<EmotionType, double[]> VA_ANCHOR = {
  HAPPY:   [+0.80, +0.60],
  SAD:     [-0.70, -0.40],
  NEUTRAL: [ 0.00,  0.00],
  ANGRY:   [-0.70, +0.80],
  FEAR:    [-0.60, +0.70],
  SURPRISE:[ 0.00, +0.85]
}

double vFinal = Σ( emotionBps[e] / 10000.0 × VA_ANCHOR[e][0] )
double aFinal = Σ( emotionBps[e] / 10000.0 × VA_ANCHOR[e][1] )
double intensity = Math.sqrt(vFinal*vFinal + aFinal*aFinal)

valenceX1000   = (int) Math.round(vFinal    × 1000)
arousalX1000   = (int) Math.round(aFinal    × 1000)
intensityX1000 = (int) Math.round(intensity × 1000)
```

### 6.3 Nullable 필드 처리
```
textScoreBps       → null  (텍스트 감성 분석 미사용)
textMagnitudeX1000 → null
alphaBps           → null  (적응형 가중치 미사용)
betaBps            → null
```

---

## 7. 변경 파일 목록

### 7.1 제거
| 파일 | 이유 |
|---|---|
| `infra/ai/hume/config/HumeConfig.java` | Hume 설정 불필요 |
| `infra/ai/hume/client/HumeBatchClient.java` | Hume 클라이언트 불필요 |
| `infra/ai/hume/callback/HumeCallbackController.java` | 콜백 엔드포인트 제거 |
| `infra/ai/hume/scheduler/HumeBatchScheduler.java` | 배치 스케줄러 불필요 |
| `infra/ai/hume/scheduler/DiaryBatchItem.java` | 배치 아이템 DTO 불필요 |
| `infra/ai/hume/mapper/HumeResultMapper.java` | Hume 전용 매퍼 불필요 |
| `infra/ai/hume/mapper/HumeEmotionMapping.java` | Gemini 매핑으로 대체 |
| `infra/ai/hume/dto/` (전체) | Hume 전용 DTO 불필요 |
| `infra/ai/sqs/SqsConfig.java` | SQS 제거 |
| `infra/ai/sqs/DiarySqsProducer.java` | SQS 제거 |
| `infra/ai/lambda/dto/DiaryPayload.java` | SQS 페이로드 불필요 |
| `api/voice/service/TriggerHumeAnalyzeUseCase.java` | Hume 전용 트리거 |
| `api/voice/service/PollHumeAnalyzeUseCase.java` | Polling 방식 제거 |

### 7.2 신규 생성
| 파일 | 역할 |
|---|---|
| `infra/ai/gemini/config/GeminiConfig.java` | Gemini 클라이언트 빈 설정 |
| `infra/ai/gemini/GeminiVoiceAnalyzer.java` | S3 다운로드 → Gemini 업로드 → 분석 → 저장 (핵심) |
| `infra/ai/gemini/GeminiEmotionMapper.java` | 48 라벨 → 6 EmotionType bps 집계 |
| `infra/ai/gemini/GeminiEmotionMapping.java` | 라벨-카테고리 매핑 상수 |
| `infra/ai/gemini/dto/GeminiAnalysisResult.java` | Gemini 응답 역직렬화 DTO |
| `infra/ai/gemini/dto/GeminiSegment.java` | segment DTO |
| `infra/ai/gemini/dto/GeminiEmotionScore.java` | emotion DTO |
| `domain/voice/adaptor/VoiceCompositeAdaptor.java` | save 메서드 추가 (현재 조회만 존재) |

### 7.3 수정
| 파일 | 변경 내용 |
|---|---|
| `api/voice/service/UploadVoiceFileUseCase.java` | `HumeBatchScheduler` → `GeminiVoiceAnalyzer` 주입 교체 |
| `domain/voice/adaptor/VoiceCompositeAdaptor.java` | `save()` 메서드 추가 |
| `domain/voice/adaptor/VoiceCompositeAdaptorImpl.java` | `save()` 구현 추가 |
| `src/main/resources/application.yml` | `hume.*` 제거, `gemini.*` 추가 |
| `build.gradle` | Gemini SDK 의존성 추가, AWS SQS 의존성 제거 가능 |
| 관련 테스트 파일 | Hume 의존성 → Gemini 의존성 교체 |

---

## 8. 설정 변경

### 8.1 application.yml
```yaml
# 제거
hume:
  api-key: ...
  base-url: ...
  callback-url: ...
sqs:
  diary-to-chatbot-url: ...

# 추가
gemini:
  api-key: ${GEMINI_API_KEY:}
  model: ${GEMINI_MODEL:gemini-2.5-flash}
```

### 8.2 환경 변수
```env
# 제거
HUME_API_KEY
HUME_BASE_URL
HUME_CALLBACK_URL
DIARY_TO_CHATBOT_SQS_URL

# 추가
GEMINI_API_KEY=AIza...
GEMINI_MODEL=gemini-2.5-flash   # optional
```

### 8.3 build.gradle 의존성
```groovy
// 추가 (google-genai 1.52.0 — 2026-04-30 GA 릴리스, Maven Central 등록 확인)
implementation 'com.google.genai:google-genai:1.52.0'

// 제거
implementation 'io.awspring.cloud:spring-cloud-aws-sqs:3.3.0'
```

---

## 9. 데이터 흐름 비교

### Before (Hume)
```
업로드 → enqueue → [60초 대기] → Hume API → [분석 대기] → callback
→ SQS → Lambda → cbt_logs 저장
(VoiceComposite 저장 미구현)
```

### After (Gemini)
```
업로드 → @Async 시작 → [Gemini 분석 ~5-30초] → VoiceComposite 직접 저장
(SQS/Lambda 제거, 모놀리식 서버에서 완결)
```

---

## 10. 유지되는 API 형상

변경 없이 그대로 유지되는 인터페이스:

| 엔드포인트 | 메서드 | 설명 |
|---|---|---|
| `/v1/api/users/voices` | POST | 음성 업로드 (voiceId 반환) |
| `/v1/api/users/voices` | GET | 음성 목록 (topEmotion 포함) |
| `/v1/api/users/voices/{voiceId}` | GET | 음성 상세 |
| `/v1/api/users/voices/analyzing/monthly` | GET | 월간 감정 분석 |
| `/v1/api/users/voices/analyzing/weekly` | GET | 주간 감정 분석 |
| `/v1/api/users/top_emotion` | GET | 대표 감정 |

`/v1/api/hume/callback`만 제거됨 (외부 콜백용으로 공개 API 아님)

---

## 11. 구현 우선순위

```
Phase 1 (핵심)
  1. GeminiConfig — Gemini 클라이언트 빈
  2. GeminiVoiceAnalyzer — S3 다운로드 + Gemini 호출 + VoiceComposite 저장
  3. GeminiEmotionMapper / GeminiEmotionMapping — 감정 집계
  4. UploadVoiceFileUseCase — HumeBatchScheduler → GeminiVoiceAnalyzer 교체
  5. VoiceCompositeAdaptor — save() 메서드 추가

Phase 2 (정리)
  6. Hume 관련 코드 전체 제거
  7. SQS 관련 코드 제거
  8. application.yml / build.gradle 정리
  9. 테스트 코드 수정
```
