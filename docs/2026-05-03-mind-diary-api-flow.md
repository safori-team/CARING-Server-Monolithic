# 마음일기 API 플로우 가이드

> 프론트엔드 연동용 문서입니다.
> Base URL: `https://{host}/v1/api`
> 모든 요청에 `Authorization: Bearer {accessToken}` 헤더 필요

---

## 목차

1. [일기 작성 플로우](#1-일기-작성-플로우)
2. [일기 목록 조회](#2-일기-목록-조회)
3. [일기 상세 조회](#3-일기-상세-조회)
4. [분석 상태 안내](#4-분석-상태-안내)
5. [주간 감정 리포트](#5-주간-감정-리포트)
6. [월간 감정 리포트](#6-월간-감정-리포트)
7. [감정 버블차트 (세부 감정)](#7-감정-버블차트-세부-감정)
8. [버블 클릭 → 일기 리스트](#8-버블-클릭--일기-리스트)
9. [질문 카테고리 / 감정 타입 레퍼런스](#9-질문-카테고리--감정-타입-레퍼런스)

---

## 1. 일기 작성 플로우

음성을 녹음하고 일기를 등록하는 3단계 플로우입니다.
S3에 직접 업로드하기 때문에 서버를 거치지 않아 빠릅니다.

```
[Step 1] Presigned URL 발급
     ↓
[Step 2] S3에 음성 파일 PUT 업로드
     ↓
[Step 3] 서버에 등록 (voiceKey 전달)
     ↓
[백그라운드] Gemini 감정 분석 자동 시작 (비동기, 수초~수십초 소요)
```

### Step 1 — Presigned URL 발급

```
GET /users/voices/presigned-url?extension={확장자}
```

| 파라미터 | 설명 | 예시 |
|---|---|---|
| extension | 음성 파일 확장자 | `m4a`, `wav`, `mp3` |

**응답**
```json
{
  "isSuccess": true,
  "result": {
    "presignedUrl": "https://s3.ap-northeast-2.amazonaws.com/bucket/voices/.../uuid.m4a?X-Amz-...",
    "voiceKey": "voices/raw/2024/01/15/uuid.m4a"
  }
}
```

> `presignedUrl` — 10분간 유효한 S3 PUT URL
> `voiceKey` — Step 3에서 그대로 전달해야 하는 키값 (저장해두세요)

---

### Step 2 — S3에 직접 PUT 업로드

```
PUT {presignedUrl}
Content-Type: audio/mp4   (m4a의 경우)
Body: 음성 파일 바이너리
```

Authorization 헤더 **불필요** (presigned URL에 인증 포함됨)
응답 코드 `200`이면 업로드 성공

---

### Step 3 — 서버에 일기 등록

```
POST /users/voices
```

| 쿼리 파라미터 | 타입 | 설명 |
|---|---|---|
| `questionCategory` | string | 질문 카테고리 (아래 레퍼런스 참고) |
| `questionIndex` | int | 해당 카테고리 내 질문 인덱스 (0-based) |
| `voiceKey` | string | Step 1에서 받은 voiceKey |

**응답**
```json
{
  "isSuccess": true,
  "result": 42
}
```

> `result` — 생성된 `voiceId`. 이후 상세 조회에 사용합니다.

**등록 직후 상태**: 감정 분석은 백그라운드에서 자동으로 시작되며, 완료까지 수초~수십초가 소요됩니다. 이 시간 동안 `analysisStatus`는 `PENDING`으로 내려옵니다. ([분석 상태 안내](#4-분석-상태-안내) 참고)

---

## 2. 일기 목록 조회

```
GET /users/voices
GET /users/voices?date=2024-01-15
```

| 쿼리 파라미터 | 필수 | 설명 |
|---|---|---|
| `date` | 선택 | 특정 날짜 필터 (`yyyy-MM-dd`). 생략 시 전체 목록 |

**응답**
```json
{
  "isSuccess": true,
  "result": {
    "voices": [
      {
        "voiceId": 42,
        "createdAt": "2024-01-15",
        "analysisStatus": "COMPLETED",
        "emotion": "HAPPY",
        "questionTitle": "오늘 기분을 한 단어로 표현하면 무엇인가요?",
        "content": "오늘 친구랑 카페 갔는데 너무 좋았어요..."
      },
      {
        "voiceId": 43,
        "createdAt": "2024-01-15",
        "analysisStatus": "PENDING",
        "emotion": null,
        "questionTitle": "오늘 스트레스 지수는 어느 정도였나요?",
        "content": null
      },
      {
        "voiceId": 44,
        "createdAt": "2024-01-14",
        "analysisStatus": "FAILED",
        "emotion": null,
        "questionTitle": "오늘 가장 힘들었던 순간이 있었나요?",
        "content": null
      }
    ]
  }
}
```

> 음성 재생 URL(`s3Url`)은 목록에서 제공되지 않습니다. 재생이 필요한 경우 상세 조회 API를 사용하세요.

---

## 3. 일기 상세 조회

```
GET /users/voices/{voiceId}
```

**응답**
```json
{
  "isSuccess": true,
  "result": {
    "voiceId": 42,
    "createdAt": "2024-01-15",
    "analysisStatus": "COMPLETED",
    "topEmotion": "HAPPY",
    "questionTitle": "오늘 기분을 한 단어로 표현하면 무엇인가요?",
    "content": "오늘 친구랑 카페 갔는데 너무 좋았어요. 날씨도 맑고 기분이 정말 좋았습니다.",
    "s3Url": "https://..."
  }
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `analysisStatus` | string | `PENDING` / `COMPLETED` / `FAILED` |
| `topEmotion` | string \| null | 대표 감정 (분석 완료 전 null) |
| `content` | string \| null | AI 전사 텍스트 (분석 완료 전 null) |
| `s3Url` | string \| null | 음성 재생 presigned URL (호출마다 갱신) |

---

## 4. 분석 상태 안내

각 일기에는 `analysisStatus` 필드가 포함됩니다. 이 값으로 UI를 분기하세요.

| `analysisStatus` | 의미 | `emotion` / `content` | 권장 UI |
|---|---|---|---|
| `PENDING` | 분석 진행 중 | `null` | 로딩 스피너 / "분석 중" 뱃지 |
| `COMPLETED` | 분석 완료 | 값 존재 | 정상 표시 |
| `FAILED` | 분석 실패 | `null` | "분석 실패" 안내 |

> 분석 완료까지 통상 **5~30초** 소요됩니다.
> 목록에서 `PENDING` 항목 탭 시 상세 페이지를 열고, 일정 시간(예: 5초) 후 재조회하는 방식을 권장합니다.
> `PENDING`과 `FAILED` 모두 `emotion`, `content`가 `null`이지만 `analysisStatus`로 구분 가능합니다.

---

## 5. 주간 감정 리포트

특정 월의 N번째 주에 해당하는 요일별 대표 감정과 AI 코멘트를 반환합니다.

```
GET /users/voices/analyzing/weekly?yearMonth=2024-01&week=2
```

| 파라미터 | 설명 |
|---|---|
| `yearMonth` | `yyyy-MM` 형식 |
| `week` | 해당 월의 몇 번째 주 (1-based) |

**응답**
```json
{
  "isSuccess": true,
  "result": {
    "weeklyEmotions": [
      { "date": "2024-01-08", "weekDay": "MON", "emotionType": "HAPPY" },
      { "date": "2024-01-09", "weekDay": "TUE", "emotionType": "NEUTRAL" },
      { "date": "2024-01-10", "weekDay": "WED", "emotionType": null },
      { "date": "2024-01-11", "weekDay": "THU", "emotionType": "SAD" },
      { "date": "2024-01-12", "weekDay": "FRI", "emotionType": "HAPPY" },
      { "date": "2024-01-13", "weekDay": "SAT", "emotionType": null },
      { "date": "2024-01-14", "weekDay": "SUN", "emotionType": null }
    ],
    "reportMessage": "이번 주 OO님은 주로 기쁨을 느끼셨네요. 특히 월요일과 금요일에..."
  }
}
```

> `emotionType: null` → 해당 날짜에 일기 없음
> `weekDay` 값: `MON` `TUE` `WED` `THU` `FRI` `SAT` `SUN`

### 하루 대표 감정 선정 방식

하루에 일기를 여러 번 작성한 경우, 그날 기록된 일기들의 대표 감정 중 **가장 많이 등장한 감정**을 그날의 대표 감정으로 선택합니다.

**동점(tie) 처리**: 같은 횟수로 등장한 감정이 여럿이면 임상적으로 유의미한 감정을 우선합니다.

```
우선순위: SAD > ANGRY > FEAR > SURPRISE > HAPPY > NEUTRAL
```

예) 슬픔 1회 · 기쁨 1회 동점 → `SAD` 선택 (돌봄이 필요한 상태를 놓치지 않기 위함)

### AI 코멘트 생성 방식

`reportMessage`는 GPT-4o-mini가 생성하는 주간 감정 요약입니다.

**생성 조건**
- 해당 주·월에 새로운 일기가 분석된 경우 자동으로 재생성됩니다.
- 분석된 일기가 추가되지 않은 경우(= 직전 호출 이후 변경 없음) 캐시된 코멘트를 즉시 반환합니다.
- 해당 주에 일기가 전혀 없으면 `"해당 주에는 감정분석 데이터가 없었습니다."`를 반환합니다.

**생성 내용**: 요일별 감정 흐름(초반·중반·후반)을 1~3문장 한국어로 요약합니다. 조언보다 관찰 사실에 집중하며, FEAR 감정은 코멘트 내에서 "anxiety(불안)"로 표현됩니다.

---

## 6. 월간 감정 리포트

해당 월 전체의 감정 분포 통계와 AI 코멘트를 반환합니다.

```
GET /users/voices/analyzing/monthly?yearMonth=2024-01
```

**응답**
```json
{
  "isSuccess": true,
  "result": {
    "monthlyEmotionCounts": {
      "NEUTRAL": 3,
      "HAPPY": 8,
      "SAD": 2,
      "ANGRY": 1,
      "FEAR": 0,
      "SURPRISE": 1
    },
    "topEmotion": "HAPPY",
    "totalCount": 15,
    "reportMessage": "1월의 OO님은 전반적으로 밝고 긍정적인 한 달을 보내셨어요..."
  }
}
```

> `totalCount` — 해당 월 일기 수 (0이면 `reportMessage`에 데이터 없음 안내가 옴)

---

## 7. 감정 버블차트 (세부 감정)

해당 월에 느낀 세부 감정 레이블과 빈도를 반환합니다.
버블 크기는 `diaryCount` 기준, 색상은 `category` 기준으로 구분하세요.

```
GET /users/voices/analyzing/bubble?yearMonth=2024-01
```

**응답**
```json
{
  "isSuccess": true,
  "result": {
    "yearMonth": "2024-01",
    "labels": [
      { "label": "joy",           "category": "happy",   "diaryCount": 7, "avgIntensityX1000": 820 },
      { "label": "calmness",      "category": "neutral",  "diaryCount": 5, "avgIntensityX1000": 610 },
      { "label": "anxiety",       "category": "fear",     "diaryCount": 4, "avgIntensityX1000": 530 },
      { "label": "sadness",       "category": "sad",      "diaryCount": 3, "avgIntensityX1000": 470 },
      { "label": "frustration",   "category": "angry",    "diaryCount": 2, "avgIntensityX1000": 390 },
      { "label": "awe",           "category": "surprise", "diaryCount": 1, "avgIntensityX1000": 280 }
    ]
  }
}
```

| 필드 | 설명 |
|---|---|
| `label` | 세부 감정명 (영문, 한국어 매핑은 아래 레퍼런스 참고) |
| `category` | 대분류 (`happy` / `sad` / `neutral` / `angry` / `fear` / `surprise`) |
| `diaryCount` | 이 감정이 등장한 일기 수 → **버블 크기** |
| `avgIntensityX1000` | 평균 감정 강도 × 1000 (부가적인 가중치 참고용) |

> 일기가 없는 달은 `labels: []`로 옵니다.

---

## 8. 버블 클릭 → 일기 리스트

버블을 클릭했을 때 해당 감정을 느꼈던 일기 목록을 보여줍니다.

```
GET /users/voices/analyzing/bubble/diaries?yearMonth=2024-01&label=joy
```

| 파라미터 | 설명 |
|---|---|
| `yearMonth` | `yyyy-MM` 형식 |
| `label` | 버블차트에서 받은 `label` 값 그대로 전달 |

**응답**
```json
{
  "isSuccess": true,
  "result": {
    "yearMonth": "2024-01",
    "label": "joy",
    "category": "happy",
    "diaries": [
      {
        "voiceId": 42,
        "createdAt": "2024-01-15",
        "analysisStatus": "COMPLETED",
        "emotion": "HAPPY",
        "questionTitle": "오늘 기분을 한 단어로 표현하면 무엇인가요?",
        "content": "친구랑 카페 갔는데 너무 좋았어요..."
      },
      {
        "voiceId": 38,
        "createdAt": "2024-01-10",
        "analysisStatus": "COMPLETED",
        "emotion": "HAPPY",
        "questionTitle": "오늘 웃었던 이유가 있다면 무엇인가요?",
        "content": "아이가 처음 걸음마를 뗐어요..."
      }
    ]
  }
}
```

> `diaries` 배열 항목을 탭하면 `/users/voices/{voiceId}` 상세 화면으로 이동하면 됩니다.
> 최신순 정렬로 내려옵니다.
> 음성 재생 URL은 상세 조회 API에서만 발급됩니다.

---

## 9. 질문 카테고리 / 감정 타입 레퍼런스

### 질문 카테고리 (`questionCategory`)

| 값 | 설명 | 질문 수 |
|---|---|---|
| `EMOTION` | 감정 | 14개 |
| `STRESS` | 스트레스 | 10개 |
| `PHYSICAL` | 신체 | 10개 |
| `SOCIAL` | 관계 | 10개 |
| `SELF_REFLECTION` | 자기성찰 | 15개 |

> `questionIndex`는 0-based입니다. 질문 목록 API는 별도 제공됩니다.

---

### 대표 감정 타입 (`EmotionType`)

| 값 | 한국어 | 색상 (제안) |
|---|---|---|
| `HAPPY` | 기쁨 | #FFD966 |
| `SAD` | 슬픔 | #6BAED6 |
| `NEUTRAL` | 평온 | #74C476 |
| `ANGRY` | 분노 | #FB6A4A |
| `FEAR` | 불안 | #9E9AC8 |
| `SURPRISE` | 놀람 | #FD8D3C |

---

### 세부 감정 레이블 한국어 매핑 (버블차트용)

| label | 한국어 | category |
|---|---|---|
| joy | 기쁨 | happy |
| ecstasy | 황홀 | happy |
| contentment | 만족 | happy |
| satisfaction | 충족감 | happy |
| amusement | 즐거움 | happy |
| excitement | 설렘 | happy |
| pride | 자부심 | happy |
| triumph | 승리감 | happy |
| relief | 안도 | happy |
| admiration | 감탄 | happy |
| adoration | 경애 | happy |
| love | 사랑 | happy |
| romance | 로맨스 | happy |
| entrancement | 매혹 | happy |
| aesthetic_appreciation | 심미적 감동 | happy |
| determination | 결의 | happy |
| sadness | 슬픔 | sad |
| distress | 고통 | sad |
| disappointment | 실망 | sad |
| guilt | 죄책감 | sad |
| shame | 수치심 | sad |
| embarrassment | 당황 | sad |
| empathic_pain | 공감적 고통 | sad |
| sympathy | 동정 | sad |
| loneliness | 외로움 | sad |
| calmness | 평온 | neutral |
| contemplation | 사색 | neutral |
| concentration | 집중 | neutral |
| interest | 관심 | neutral |
| realization | 깨달음 | neutral |
| boredom | 지루함 | neutral |
| tiredness | 피곤함 | neutral |
| confusion | 혼란 | neutral |
| doubt | 의심 | neutral |
| nostalgia | 향수 | neutral |
| anger | 분노 | angry |
| contempt | 경멸 | angry |
| disgust | 혐오 | angry |
| frustration | 좌절 | angry |
| envy | 부러움 | angry |
| craving | 갈망 | angry |
| fear | 공포 | fear |
| anxiety | 불안 | fear |
| horror | 두려움 | fear |
| surprise_positive | 긍정적 놀람 | surprise |
| surprise_negative | 부정적 놀람 | surprise |
| awe | 경이로움 | surprise |
| awkwardness | 어색함 | surprise |

---

## 화면별 추천 API 호출 순서

### 홈 / 캘린더 화면
```
GET /users/voices?date={선택된 날짜}
```

### 일기 작성 화면
```
1. GET /users/voices/presigned-url?extension=m4a
2. PUT {presignedUrl}  ← S3 직접 업로드
3. POST /users/voices?questionCategory=EMOTION&questionIndex=0&voiceKey=...
```

### 일기 상세 화면
```
GET /users/voices/{voiceId}
```
→ `analysisStatus`가 `PENDING`이면 분석 중 UI 표시 후 일정 시간 후 재호출

### 감정 분석 탭
```
월간 통계:   GET /users/voices/analyzing/monthly?yearMonth=2024-01
주간 달력:   GET /users/voices/analyzing/weekly?yearMonth=2024-01&week=2
버블차트:    GET /users/voices/analyzing/bubble?yearMonth=2024-01
```

### 버블 클릭 시
```
GET /users/voices/analyzing/bubble/diaries?yearMonth=2024-01&label=joy
```
