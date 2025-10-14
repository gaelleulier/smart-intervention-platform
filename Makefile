SHELL := /bin/bash
ENV_FILE ?= .env
include $(ENV_FILE)
export $(shell sed 's/=.*//' $(ENV_FILE))

.PHONY: help env-up env-down env-ps backend-run frontend-run db-cli clean cdc-bootstrap prod-build prod-up prod-down prod-logs prod-ps

help:
	@echo "Targets: env-up | env-down | env-ps | backend-run | frontend-run | db-cli | clean | prod-build | prod-up | prod-down | prod-logs | prod-ps"

env-up:
	docker compose -f docker-compose.dev.yml --env-file $(ENV_FILE) up -d

env-down:
	docker compose -f docker-compose.dev.yml --env-file $(ENV_FILE) down -v

env-ps:
	docker compose -f docker-compose.dev.yml --env-file $(ENV_FILE) ps

backend-run:
	cd backend && SPRING_PROFILES_ACTIVE=dev ./mvnw -q -DskipTests spring-boot:run

frontend-run:
	cd frontend && npm install && npm run start

db-cli:
	docker exec -it ${PROJECT_NAME}-db-dev psql -U ${POSTGRES_USER} -d ${POSTGRES_DB}

clean:
	rm -rf backend/target frontend/node_modules

prod-build:
	docker compose -f docker-compose.prod.yml --env-file $(ENV_FILE) build

prod-up: prod-build
	docker compose -f docker-compose.prod.yml --env-file $(ENV_FILE) up -d

prod-down:
	docker compose -f docker-compose.prod.yml --env-file $(ENV_FILE) down

prod-logs:
	docker compose -f docker-compose.prod.yml --env-file $(ENV_FILE) logs -f --tail=200

prod-ps:
	docker compose -f docker-compose.prod.yml --env-file $(ENV_FILE) ps

cdc-bootstrap: env-up
	@echo "Waiting for Kafka services to be ready..."
	sleep 10
	@echo "Ensuring CDC topic exists..."
	docker compose -f docker-compose.dev.yml --env-file $(ENV_FILE) exec kafka \
		kafka-topics --bootstrap-server kafka:9092 --create \
		--topic sip.public.interventions --partitions 1 --replication-factor 1 >/dev/null 2>&1 || true
	@echo "Registering Debezium connectors..."
	set -a && source $(ENV_FILE) && set +a && ./scripts/register-connectors.sh
	@echo "Submitting Flink SQL job..."
	./scripts/submit-flink-job.sh
