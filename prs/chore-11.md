## PR 제목
GitHub Actions 기반 ECR 푸시 및 SSM Run Command 배포 워크플로를 추가

## 변경 요약

### 변경 배경/동기
기존에는 `main` 반영 이후 애플리케이션 이미지를 빌드하고 서버에 반영하는 표준 배포 파이프라인이 없었습니다. 이번 변경은 테스트, ECR 이미지 푸시, SSM 기반 원격 배포를 하나의 GitHub Actions 흐름으로 묶어 수동 배포 단계를 줄이기 위한 작업입니다.

### 주요 변경 사항
- `main` push 시 `./gradlew test`를 먼저 실행하는 GitHub Actions 워크플로를 추가했습니다.
- 테스트 통과 후 Docker 이미지를 빌드해 Amazon ECR에 `${github.sha}` 와 `latest` 태그로 푸시하도록 구성했습니다.
- ECR에 올라간 이미지 URI를 다음 job으로 넘겨 SSM Run Command로 원격 배포를 실행하도록 연결했습니다.
- 대상 서버에서는 `${APP_DIR}/.env`를 사용해 기존 컨테이너를 교체하고 `/actuator/health` 기준 헬스체크까지 수행합니다.
- 최종 워크플로 파일명을 `workflow.yml`로 정리해 배포 파일 경로를 단순화했습니다.

### 주의할 점
- GitHub Actions `Repository variables`에 `AWS_REGION`, `ECR_REPOSITORY`, `SSM_TARGET_KEY`, `SSM_TARGET_VALUE`, `APP_DIR`, `CONTAINER_NAME`가 등록되어 있어야 합니다.
- GitHub Actions `Secrets`에는 `AWS_ROLE_TO_ASSUME`가 필요하며, 해당 IAM Role에 ECR 푸시와 SSM 실행 권한이 있어야 합니다.
- 배포 대상 서버에는 `${APP_DIR}/.env`가 미리 있어야 하고 `docker`, `aws`, `curl`, SSM Agent가 정상이어야 합니다.
- 현재 워크플로는 사실상 단일 대상 서버를 전제로 첫 번째 SSM invocation 기준으로 상태를 추적합니다.

### 영향 범위
- `main` 머지 이후 백엔드 이미지 빌드와 서버 반영 흐름이 자동화됩니다.
- 배포 실패 시 GitHub Actions 로그에서 ECR 단계와 SSM 단계의 실패 지점을 구분해 확인할 수 있습니다.
- 애플리케이션 코드나 API 스펙 변경은 없고, 영향 범위는 CI/CD 구성에 한정됩니다.

## 변경 파일
| 상태 | 파일 |
|---|---|
| A | .github/workflows/workflow.yml |

## 커밋 목록
- `6b6297c` chore : edit name of cicd file(test-deploy.yml->workflow.yml) #11
- `55230a3` chore : create ci/cd (ECR + SSM) #11
