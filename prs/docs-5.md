## PR 제목
문서용 에이전트 가이드를 추가하고 Docker 구동 시 발생한 JPA 매핑 오류를 수정

## 변경 요약

### 변경 배경/동기
이번 브랜치는 두 가지 목적을 함께 담고 있습니다. 첫째, 저장소에서 Claude/Codex가 브랜치 비교와 PR 작성을 일관되게 수행할 수 있도록 문서 기반 가이드를 추가했습니다. 둘째, Docker 환경에서 애플리케이션 기동 시 Hibernate의 스키마 업데이트 과정에서 발생하던 엔티티 매핑 오류를 수정해 테이블과 인덱스 생성이 정상적으로 이뤄지도록 정리했습니다.

### 주요 변경 사항
- `CLAUDE.md`, `AGENTS.md`, `agents/` 하위 문서를 추가해 브랜치 비교와 PR 생성 작업 흐름을 저장소 수준에서 명시했습니다.
- 추가한 에이전트 문서를 영어 기준으로 정리해 문서 언어를 통일했습니다.
- `VoiceComposite`가 `voice_content` 테이블을 공유하던 매핑을 분리하고, 전용 테이블 `voice_composite` 및 별도 unique constraint 이름을 사용하도록 수정했습니다.
- `Notification`의 생성일 인덱스가 실제 감사 컬럼과 일치하도록 `created_at` 대신 `created_date`를 사용하도록 수정했습니다.
- 현재 애플리케이션 기준으로 필요하지 않은 토큰 항목을 `DOCKER.md`에서 제거해 실행 가이드를 단순화했습니다.

### 주의할 점
- 엔티티 매핑 변경으로 인해 DDL 영향이 있습니다. `VoiceComposite`는 이제 `voice_content`가 아닌 `voice_composite` 테이블을 사용하며, `Notification`의 인덱스 대상 컬럼도 `created_date`로 바뀌었습니다.
- 기존 환경에 실패한 스키마 변경 흔적이나 반쯤 생성된 객체가 남아 있다면, 재기동 전에 DB 상태를 한 번 확인할 필요가 있습니다.
- API 엔드포인트 스펙 변경은 포함되지 않습니다.

### 영향 범위
- 저장소 작업 가이드가 명확해져 브랜치 비교 및 PR 작성 흐름이 일관되게 유지됩니다.
- Docker 실행 시 Hibernate schema update 단계에서 발생하던 테이블 생성 실패를 줄여 기동 안정성이 개선됩니다.
- 영향 범위는 문서 체계와 일부 persistence 스키마 생성 로직에 한정되며, 비즈니스 로직 전반의 동작 변경은 없습니다.

## 변경 파일
| 상태 | 파일 |
|---|---|
| A | AGENTS.md |
| A | CLAUDE.md |
| M | DOCKER.md |
| A | agents/branch_compare.md |
| A | agents/pr_create.md |
| M | src/main/java/com/caring/domain/notification/entity/Notification.java |
| M | src/main/java/com/caring/domain/voice/entity/VoiceComposite.java |

## 커밋 목록
- `40b7d3f` fix : error of entity mapping #5
- `a76e36e` docs : convert md files korean to english #5
- `4403e59` docs : writing AGENTS.md & CLAUDE.md #5
