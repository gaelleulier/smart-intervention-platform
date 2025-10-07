#!/usr/bin/env bash
set -euo pipefail

COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.dev.yml}"
JOB_TEMPLATE="${JOB_TEMPLATE:-infra/cdc/flink/analytics_job.sql}"
TMP_JOB_PATH="/tmp/analytics_job.sql"

if ! command -v envsubst >/dev/null 2>&1; then
  echo "envsubst (part of gettext) is required to render ${JOB_TEMPLATE}" >&2
  exit 1
fi

if [[ ! -f "${JOB_TEMPLATE}" ]]; then
  echo "Unable to find Flink SQL job template at ${JOB_TEMPLATE}" >&2
  exit 1
fi

if [[ ! -f .env ]]; then
  echo "Missing .env file at repository root" >&2
  exit 1
fi

# Export environment variables defined in .env to substitute credentials.
set -a
source .env
set +a

SQL_PAYLOAD=$(envsubst < "${JOB_TEMPLATE}")

# Copy rendered SQL into the jobmanager container.
echo "Uploading rendered SQL job to Flink jobmanager..."
printf '%s' "${SQL_PAYLOAD}" | docker compose -f "${COMPOSE_FILE}" --env-file .env exec -T flink-jobmanager bash -c "cat > ${TMP_JOB_PATH}"

echo "Submitting Flink SQL job..."
docker compose -f "${COMPOSE_FILE}" --env-file .env exec -T flink-jobmanager bash -c "./bin/sql-client.sh embedded -f ${TMP_JOB_PATH}"
