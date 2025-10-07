#!/usr/bin/env bash
set -euo pipefail

CONNECT_URL="${CONNECT_URL:-http://localhost:8083}"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONNECTOR_DIR="${ROOT_DIR}/infra/cdc/connectors"

if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required to register connectors. Please install it first." >&2
  exit 1
fi

for config in "${CONNECTOR_DIR}"/*.json; do
  if command -v envsubst >/dev/null 2>&1; then
    payload="$(envsubst <"${config}")"
  else
    payload="$(cat "${config}")"
  fi

  name="$(printf '%s' "${payload}" | jq -r '.name')"
  echo "Registering connector ${name} from ${config}"

  curl -sSf \
    -X DELETE \
    "${CONNECT_URL}/connectors/${name}" >/dev/null 2>&1 || true

  printf '%s' "${payload}" | curl -sSf \
    -X POST \
    -H "Content-Type: application/json" \
    --data-binary @- \
    "${CONNECT_URL}/connectors" >/dev/null
  echo "Connector ${name} registered."
done
