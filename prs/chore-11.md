## PR 제목
GitHub Actions 기반 ECR 푸시 및 SSM Run Command 배포 워크플로를 추가

## 변경 요약

### 변경 배경/동기
기존 저장소에는 `main` 브랜치 반영 이후 애플리케이션 이미지를 자동으로 빌드하고 배포하는 CI/CD 파이프라인이 없었습니다. 이번 변경은 GitHub Actions를 기준으로 테스트 실행, Amazon ECR 이미지 푸시, AWS Systems Manager Run Command 기반 서버 배포를 하나의 워크플로로 연결해 수동 배포 단계를 줄이는 데 목적이 있습니다.

### 주요 변경 사항
- `.github/workflows/workflow.yml`을 추가해 `main` 브랜치 push 시 `./gradlew test`를 먼저 실행하도록 구성했습니다.
- 테스트 통과 후 Docker Buildx를 사용해 애플리케이션 이미지를 빌드하고 Amazon ECR에 `${github.sha}` 및 `latest` 태그로 푸시하도록 설정했습니다.
- 푸시한 이미지 URI를 후속 job으로 전달해, AWS SSM `AWS-RunShellScript` 문서로 대상 서버에 배포 명령을 실행하도록 구성했습니다.
- SSM 대상 서버에서는 지정한 `.env` 파일을 사용해 기존 컨테이너를 제거하고 새 이미지를 기동한 뒤, `/actuator/health` 기준 헬스체크를 수행하도록 했습니다.
- 초기 파일명 `test-deploy.yml`을 최종적으로 `workflow.yml`로 정리해 단일 워크플로 파일로 유지했습니다.

### 주의할 점
- GitHub Actions `Repository variables`에 `AWS_REGION`, `ECR_REPOSITORY`, `SSM_TARGET_KEY`, `SSM_TARGET_VALUE`, `APP_DIR`, `CONTAINER_NAME`가 등록되어 있어야 합니다.
- GitHub Actions `Secrets`에 `AWS_ROLE_TO_ASSUME`가 등록되어 있어야 하며, 해당 IAM Role에는 ECR 푸시 및 SSM Run Command 실행 권한이 필요합니다.
- SSM 대상 서버에는 `${APP_DIR}/.env` 파일이 미리 존재해야 하고, `docker`, `aws`, `curl`, SSM Agent가 정상 동작해야 합니다.
- 현재 워크플로는 `SSM_TARGET_KEY/SSM_TARGET_VALUE`로 매칭된 대상이 사실상 단일 서버라는 전제를 두고 첫 번째 invocation 기준으로 상태를 추적합니다.

### 영향 범위
- `main` 브랜치 머지 이후 백엔드 배포 흐름이 자동화됩니다.
- 배포 실패 시 GitHub Actions 로그에서 ECR 빌드/푸시 실패와 SSM 원격 실행 실패를 구분해 확인할 수 있습니다.
- 애플리케이션 코드나 API 스펙 변경은 포함되지 않으며, 영향 범위는 CI/CD 파이프라인 구성에 한정됩니다.

## 변경 파일
| 상태 | 파일 |
|---|---|
| A | .github/workflows/workflow.yml |

## 커밋 목록
- `6b6297c` chore : edit name of cicd file(test-deploy.yml->workflow.yml) #11
- `55230a3` chore : create ci/cd (ECR + SSM) #11
