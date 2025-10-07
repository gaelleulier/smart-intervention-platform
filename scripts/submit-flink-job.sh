#!/usr/bin/env bash
set -euo pipefail

COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.dev.yml}"
JOB_PATH="${JOB_PATH:-infra/cdc/flink/intervention_metrics_job.py}"

if [[ ! -f "${JOB_PATH}" ]]; then
  echo "Unable to find Flink job at ${JOB_PATH}" >&2
  exit 1
fi

echo "Submitting Flink job ${JOB_PATH}..."
docker compose -f "${COMPOSE_FILE}" exec -T flink-jobmanager \
  flink run -py "/opt/flink/usrlib/$(basename "${JOB_PATH}")"
