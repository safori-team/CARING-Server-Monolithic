# branch_compare

## 트리거
- "브랜치 비교", "main과 차이", "머지하면 어떤 위험이 있어?", "변경점 분석"

## 목표
기준 브랜치와 대상 브랜치의 차이를 파악하고, 머지 시 발생할 수 있는 위험 요소를 정리한다.

## 입력 규칙
- base 기본값: `main`
- head 기본값: `HEAD`
- 사용자가 브랜치를 지정하면 해당 브랜치 사용

## 실행 순서

### 1단계: 커밋 차이 확인
```bash
git rev-list --left-right --count <base>...<head>
git log --oneline <base>...<head>
```

### 2단계: 파일 변경 확인
```bash
git diff --name-status <base>...<head>
git diff --stat <base>...<head>
```

### 3단계: 위험 분석
변경된 파일 중 아래 항목을 중점 확인:
- **설정 변경**: `application.yml`, `build.gradle`, `SecurityConfig` 등
- **엔티티 변경**: JPA 엔티티 필드 추가/삭제/변경 → DDL 영향
- **API 변경**: 컨트롤러 엔드포인트 추가/삭제/시그니처 변경 → 클라이언트 영향
- **의존성 변경**: `build.gradle` 라이브러리 추가/제거/버전 변경
- **보안 변경**: Security, 인증/인가 로직 변경

### 4단계: 충돌 가능성 확인
```bash
git merge-tree $(git merge-base <base> <head>) <base> <head>
```

## 결과 형식
1. **비교 기준**: `<base>...<head>`
2. **커밋 차이**: ahead/behind 숫자
3. **주요 변경 파일**: 상위 파일 목록 (Added/Modified/Deleted)
4. **위험 요소**: 최대 5개, 심각도 표시 (HIGH/MEDIUM/LOW)
5. **머지 충돌**: 충돌 예상 파일 목록 (없으면 "충돌 없음")
