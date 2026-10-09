# Two Spring Boot services with Postgres and Docker

Two small Spring Boot apps that run together via `docker compose`:

- **auth-api** (Service A, port 8080): registration, login (JWT) and a protected
  `/api/process` endpoint. It calls Service B and stores a log record in Postgres.
- **data-api** (Service B, port 8081): `/api/transform` endpoint. It accepts requests
  only with a valid `X-Internal-Token` header.
- **postgres**: stores `users` and `processing_log`.

```
client ---► auth-api (8080) ---► data-api (8081)
               │
               └---► postgres (5432)
```

## Requirements

- Docker with Docker Compose
- JDK 25 (to build the jars)

## Run

From the repository root:

```bash
# 1. Build the jars (the Dockerfiles copy them from target/)
mvn -f auth-api/pom.xml clean package -DskipTests
mvn -f data-api/pom.xml clean package -DskipTests

# 2. Start everything
docker compose up -d --build

# 3. Check that all three containers are running
docker compose ps
```

If Maven is not installed, use the bundled wrapper instead
(`./mvnw` on Linux/macOS, `.\mvnw.cmd` on Windows) from inside each service folder:

```bash
cd auth-api && ./mvnw clean package -DskipTests && cd ..
cd data-api && ./mvnw clean package -DskipTests && cd ..
```

Stop and remove containers (add `-v` to also delete the database volume):

```bash
docker compose down
```

## Configuration

All settings are environment variables, set in `docker-compose.yml`
(defaults are for local development only; override them for anything real):

| Variable | Used by | Purpose |
|---|---|---|
| `POSTGRES_URL` | auth-api | JDBC URL, e.g. `jdbc:postgresql://postgres:5432/winwin_db` |
| `POSTGRES_USER` | postgres, auth-api | database user |
| `POSTGRES_PASSWORD` | postgres, auth-api | database password |
| `JWT_SECRET` | auth-api | JWT signing key (at least 32 characters) |
| `INTERNAL_TOKEN` | auth-api, data-api | shared secret for the `X-Internal-Token` header |
| `DATA_API_URL` | auth-api | data-api endpoint, `http://data-api:8081/api/transform` |

Both services share one Docker network, so auth-api reaches data-api by the name `data-api`.

## Test

Examples use `curl` (on Windows PowerShell use `curl.exe`, or `Invoke-RestMethod`).

**Register** (201):

```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"a@a.com","password":"pass"}'
```

**Login** (200, returns `{ "token": "..." }`):

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"a@a.com","password":"pass"}'
```

**Process** (use the token from login):

```bash
curl -X POST http://localhost:8080/api/process \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"text":"hello"}'
# {"result":"OLLEH"}
```

**data-api rejects calls without the internal token** (403):

```bash
curl -i -X POST http://localhost:8081/api/transform \
  -H "Content-Type: application/json" \
  -d '{"text":"hello"}'
```

**Check the stored log record:**

```bash
docker compose exec postgres psql -U postgres -d winwin_db \
  -c "SELECT input_text, output_text, created_at FROM processing_log;"
```

## API summary

| Endpoint | Auth | Success | Errors |
|---|---|---|---|
| `POST /api/auth/register` | none | 201 | 400 missing fields, 409 email already used |
| `POST /api/auth/login` | none | 200 `{token}` | 400 missing fields, 401 wrong credentials |
| `POST /api/process` | Bearer JWT | 200 `{result}` | 400 empty text, 401 no/invalid token, 502 data-api failure |
| `POST /api/transform` (data-api) | `X-Internal-Token` | 200 `{result}` | 400 empty text, 403 missing/invalid token |

The transform logic is: reverse the text and convert it to uppercase.

## Notes

- Passwords are hashed with BCrypt; passwords and tokens are never logged.
- JWTs are stateless (HS256), valid for 24 hours.
- The schema is created by Hibernate (`ddl-auto=update`): `users` (id UUID, email unique,
  password_hash) and `processing_log` (id UUID, user_id, input_text, output_text, created_at).
- When data-api rejects a request or is unreachable, auth-api logs the cause and returns 502.
