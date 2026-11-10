# Plan: Dockerize Development Environment

## Goal
Create a `docker-compose-dev.yml` with two profiles:
- **`infra`** — Spins up all dependency services (PostgreSQL, Keycloak, RabbitMQ, Elasticsearch) so you can run the app locally from IDE/terminal
- **`full`** — Also includes the app container (built from source) for a fully containerized experience

## Files to Create

### 1. `docker-compose-dev.yml`
Docker Compose file with all services and profiles.

**Services:**

| Service | Image | Ports | Profile |
|---------|-------|-------|---------|
| `postgres` | `postgres:15-alpine` | `5433:5432` | infra, full |
| `keycloak` | `quay.io/keycloak/keycloak:26.0` | `8180:8080` | infra, full |
| `rabbitmq` | `rabbitmq:3.13-management` | `5672:5672`, `15672:15672` | infra, full |
| `elasticsearch` | `elasticsearch:8.13.0` | `9200:9200` | infra, full |
| `app` | Built from `docker/dev.dockerfile` | `8080:8080`, `8444:8444`, `9000:9000` | full |

**Key details:**
- PostgreSQL: creates `aaa` schema + runs Flyway migrations via init script, user=`postgres`, db=`iudx_db`, port mapped to `5433` (matches existing config)
- Keycloak: dev mode (`start-dev`), imports realm `iudx`, admin user/pass from env
- RabbitMQ: creates vhosts `/prod`, `/internal`, `/external` via definitions file
- Elasticsearch: single-node, security disabled for dev
- Network: `dx-dev-net` (bridge)
- Named volumes for data persistence

### 2. `dev/config-dev.json`
Development config JSON pointing all services to Docker container hostnames. Two variants will be embedded:
- Docker hostnames (for `full` profile): `postgres`, `keycloak`, `rabbitmq`, `elasticsearch`
- Localhost hostnames (for `infra` profile): `127.0.0.1` with mapped ports

We'll create one config for **infra** mode (localhost) since that's the primary dev workflow. The `full` profile will volume-mount a Docker-specific config.

### 3. `dev/init-db.sh`
PostgreSQL init script that:
- Creates the `aaa` schema
- Sets `search_path` to `aaa, public` for the default user

### 4. `dev/rabbitmq-definitions.json`
RabbitMQ definitions file that pre-creates:
- Vhosts: `/prod`, `/internal`, `/external`
- Admin user with permissions on all vhosts

### 5. `dev/keycloak/realm-iudx.json`
Keycloak realm import file for the `iudx` realm with:
- Basic realm settings
- Admin client (`admin-cli`) configured with client credentials

### 6. `dev/.env`
Environment variables file with default credentials (dev only).

### 7. Update `.gitignore`
Add `dev/` sensitive files if needed, but since these are dev defaults, they can be committed.

## Architecture

```
docker-compose-dev.yml
dev/
├── .env                          # Default dev credentials
├── config-dev.json               # App config for infra profile (localhost)
├── config-docker.json            # App config for full profile (container names)
├── init-db.sh                    # PostgreSQL init script
├── rabbitmq-definitions.json     # RabbitMQ vhosts/users
└── keycloak/
    └── realm-iudx.json           # Keycloak realm import
```

## Usage

```bash
# Start infrastructure only (run app from IDE)
docker compose -f docker-compose-dev.yml --profile infra up -d

# Start everything including the app
docker compose -f docker-compose-dev.yml --profile full up -d

# Stop
docker compose -f docker-compose-dev.yml --profile infra down

# Reset (delete volumes)
docker compose -f docker-compose-dev.yml --profile infra down -v
```

## Implementation Order
1. Create `dev/` directory structure
2. Create `dev/init-db.sh` (PostgreSQL init)
3. Create `dev/rabbitmq-definitions.json` (RabbitMQ setup)
4. Create `dev/keycloak/realm-iudx.json` (Keycloak realm)
5. Create `dev/.env` (default credentials)
6. Create `dev/config-dev.json` (infra mode config)
7. Create `dev/config-docker.json` (full mode config)
8. Create `docker-compose-dev.yml` (main compose file)
9. Test with `docker compose --profile infra up`
