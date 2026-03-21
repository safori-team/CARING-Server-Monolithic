## Docker Run

This project can run as a Spring Cloud Config client, using the existing MSA `config-service` build source from:

- `/Users/hann/Project/caring-back/caring-back`

### Prerequisites

Before starting the stack, make sure these are available in your shell or `.env`:

```dotenv
GITHUB_USERNAME=your_github_username
GITHUB_PASSWORD=your_github_token_or_password
SPRING_CLOUD_CONFIG_NAME=caring-server
DOCKER_DB_URL=jdbc:mysql://mysql:3306/caring?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Seoul
DOCKER_DB_USERNAME=caring
DOCKER_DB_PASSWORD=caring
```

`SPRING_CLOUD_CONFIG_NAME` should match the config file name stored in your private config repository.
For example, if the private repo contains `caring-server-docker.yml`, keep it as `caring-server`.

### Start

```bash
docker compose up --build
```

### Stop

```bash
docker compose down
```

If you also want to remove the MySQL volume:

```bash
docker compose down -v
```

### Notes

- The app runs with `SPRING_PROFILES_ACTIVE=docker` in Docker.
- `config-service` is built from the MSA project source and exposed on `http://localhost:8888`.
- Local `.env` loading still exists as a fallback path for non-Docker runs.
- The Docker stack uses `DOCKER_DB_URL`, `DOCKER_DB_USERNAME`, and `DOCKER_DB_PASSWORD` so your local `.env` `DB_*` values do not leak into container networking by mistake.
- Inside Docker, Redis is fixed to `redis:6379`.
