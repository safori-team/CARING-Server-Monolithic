## PR Title
OpenAI 기반 주간/월간 감정 리포트를 추가하고 voice 조회 응답을 확장

## Change Summary

### Background / Motivation
음성 감정 분석 API가 통계 수치만 반환하고 있어 사용자에게 해석 가능한 리포트를 제공하기 어려웠고, voice 목록/상세 조회 응답도 실제 화면 구성에 필요한 대표 감정, 질문 제목, 전사문 정보를 충분히 담지 못하고 있었습니다. 이번 변경은 주간/월간 감정 데이터를 기반으로 OpenAI 요약 리포트를 생성 및 캐시하고, voice 관련 조회 API와 테스트 경로를 보강해 프론트 연동과 기능 검증을 쉽게 만드는 데 목적이 있습니다.

### Key Changes
- `/v1/api/users/voices/analyzing/monthly` 엔드포인트를 추가하고 기존 월간 감정 집계 로직을 `MonthlyAnalysisCombinedResponse` 기반으로 재구성해 월별 감정 카운트, 대표 감정, 전체 건수, AI 리포트 메시지를 함께 반환하도록 변경했습니다.
- 주간 감정 분석 응답에 `reportMessage`를 추가하고, `GetWeeklyEmotionReportUseCase` 및 `OpenAiWeeklyReportClient`를 통해 최근 음성 데이터 기준으로 OpenAI 요약 결과를 생성한 뒤 캐시 재사용하도록 구성했습니다.
- `monthly_emotion_report`, `weekly_emotion_report` 엔티티와 저장소를 추가해 사용자/월 또는 사용자/월/주 단위로 리포트 메시지를 저장하고, 최신 `voice_composite` 기준으로 재생성 여부를 판단하도록 했습니다.
- voice 목록/상세 조회에서 `topEmotion`, 질문 제목, 전사문을 함께 내려주도록 `GetUserVoiceListUseCase`, `GetUserVoiceDetailUseCase`, 관련 repository/adaptor를 확장했습니다.
- 테스트를 위해 `/v1/api/users/voices/test-dummy` API를 추가해 voice, content, analyze, composite 더미 데이터를 일괄 생성할 수 있게 했습니다.
- `@UserCode`를 Spring Security principal 기반으로 단순화하고 기존 `MemberCodeArgumentResolver`를 제거했으며, sign-out API는 `refreshToken`을 `@RequestParam`으로 받도록 정리했습니다.
- 사용하지 않는 `/v1/api/nlp` 테스트 컨트롤러를 삭제했고, OpenAI 설정값과 에이전트/PR 작성용 문서를 저장소에 추가했습니다.
- `VoiceComposite` 테이블 매핑과 `Notification` 인덱스 컬럼명을 수정해 Hibernate DDL 업데이트 시 발생할 수 있는 매핑 오류를 정리했습니다.

### Notes / Cautions
- DDL 영향이 있습니다. 신규 테이블 `monthly_emotion_report`, `weekly_emotion_report`가 추가되고, `VoiceComposite`는 `voice_composite` 테이블을 사용하며, `Notification` 인덱스는 `created_date` 기준으로 생성됩니다.
- API 스펙 변경이 있습니다. 월간 감정 분석 경로가 `/frequency`에서 `/monthly`로 바뀌었고, 주간 감정 분석 및 voice 상세/목록 응답 필드가 확장되었습니다.
- OpenAI 연동에는 `OPENAI_API_KEY`와 선택적 `OPENAI_MODEL` 설정이 필요하며, 키가 없으면 리포트 생성 시 예외가 발생합니다.
- 현재 브랜치에는 docs/#5 변경이 함께 병합되어 있어 기능 변경과 문서/매핑 수정이 같이 포함됩니다.

### Impact Scope
- 감정 분석 화면은 주간/월간 통계와 함께 자연어 리포트를 바로 표시할 수 있습니다.
- voice 목록/상세 화면은 추가 API 호출 없이 대표 감정, 질문 문구, 전사문을 사용할 수 있습니다.
- 인증 파라미터 해석 방식과 일부 테스트/운영 보조 API가 정리되어 컨트롤러 계층 동작에 영향이 있습니다.
- 스키마 업데이트가 필요한 배포에서는 기존 DB 객체 상태를 사전에 확인해야 합니다.

## Changed Files
| Status | File |
|---|---|
| A | .agents/AGENTS.md |
| A | .claude/CLAUDE.md |
| M | DOCKER.md |
| A | agents/branch_compare.md |
| A | agents/pr_create.md |
| A | prs/docs-5.md |
| M | src/main/java/com/caring/api/auth/controller/SecurityAccessApiController.java |
| M | src/main/java/com/caring/api/emotion/controller/EmotionAnalysisApiController.java |
| D | src/main/java/com/caring/api/emotion/controller/NlpController.java |
| R | src/main/java/com/caring/api/emotion/dto/FrequencyAnalysisCombinedResponse.java -> src/main/java/com/caring/api/emotion/dto/MonthlyAnalysisCombinedResponse.java |
| M | src/main/java/com/caring/api/emotion/dto/WeeklyAnalysisCombinedResponse.java |
| A | src/main/java/com/caring/api/emotion/service/GetMonthlyEmotionReportUseCase.java |
| A | src/main/java/com/caring/api/emotion/service/GetWeeklyEmotionReportUseCase.java |
| M | src/main/java/com/caring/api/voice/controller/VoiceApiController.java |
| M | src/main/java/com/caring/api/voice/dto/VoiceDetailResponse.java |
| A | src/main/java/com/caring/api/voice/service/CreateVoiceDummyDataUseCase.java |
| R | src/main/java/com/caring/api/voice/service/GetEmotionFrequencyUseCase.java -> src/main/java/com/caring/api/voice/service/GetMonthlyEmotionAnalysisUseCase.java |
| M | src/main/java/com/caring/api/voice/service/GetUserVoiceDetailUseCase.java |
| M | src/main/java/com/caring/api/voice/service/GetUserVoiceListUseCase.java |
| M | src/main/java/com/caring/api/voice/service/GetWeeklyEmotionAnalysisUseCase.java |
| M | src/main/java/com/caring/common/annotation/UserCode.java |
| M | src/main/java/com/caring/common/config/WebMvcConfig.java |
| D | src/main/java/com/caring/common/resolver/MemberCodeArgumentResolver.java |
| M | src/main/java/com/caring/common/util/DateRangeUtil.java |
| M | src/main/java/com/caring/domain/notification/entity/Notification.java |
| M | src/main/java/com/caring/domain/voice/adaptor/VoiceCompositeAdaptor.java |
| M | src/main/java/com/caring/domain/voice/adaptor/VoiceCompositeAdaptorImpl.java |
| A | src/main/java/com/caring/domain/voice/entity/MonthlyEmotionReport.java |
| M | src/main/java/com/caring/domain/voice/entity/VoiceComposite.java |
| A | src/main/java/com/caring/domain/voice/entity/WeeklyEmotionReport.java |
| A | src/main/java/com/caring/domain/voice/repository/MonthlyEmotionReportRepository.java |
| A | src/main/java/com/caring/domain/voice/repository/VoiceAnalyzeRepository.java |
| M | src/main/java/com/caring/domain/voice/repository/VoiceCompositeRepository.java |
| A | src/main/java/com/caring/domain/voice/repository/VoiceContentRepository.java |
| M | src/main/java/com/caring/domain/voice/repository/VoiceQuestionRepository.java |
| A | src/main/java/com/caring/domain/voice/repository/WeeklyEmotionReportRepository.java |
| A | src/main/java/com/caring/infra/openai/OpenAiMonthlyReportClient.java |
| A | src/main/java/com/caring/infra/openai/OpenAiWeeklyReportClient.java |
| M | src/main/resources/application.yml |

## Commit List
- `7786623` chore : move md files #4
- `cd923da` refactor : remove nlp test api #4
- `decc1af` refactor : add function of making monthly report by open-ai #4
- `3b7c17b` feat : add function of using open-ai in getting weekly report #4
- `f7ee6a8` refactor : add fields when get voice list or detail data #4
- `cb8cde8` refactor : add top_emotion field when get voice list #4
- `ad1db8a` feat : create post dummy-voice api for testing #4
- `c9c3753` refactor : remove UserCode annotation resolver #4
- `a3a067b` Merge pull request #6 from safori-team/docs/#5
- `2197dc3` docs : write docs-5.md #5
- `40b7d3f` fix : error of entity mapping #5
- `a76e36e` docs : convert md files korean to english #5
- `4403e59` docs : writing AGENTS.md & CLAUDE.md #5
