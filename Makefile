ENV_FILE ?= .env
include $(ENV_FILE)
export $(shell sed 's/=.*//' $(ENV_FILE))

.PHONY: help env-up env-down env-ps backend-run frontend-run db-cli clean

help:
	@echo "Targets: env-up | env-down | env-ps | backend-run | frontend-run | db-cli | clean"

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