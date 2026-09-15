#!/usr/bin/env bash
set -euo pipefail

backend_url="${AUKNOWLOG_BACKEND_URL:-http://127.0.0.1:8080}"
prometheus_url="${AUKNOWLOG_PROMETHEUS_URL:-http://127.0.0.1:9090}"
metrics_file="$(mktemp)"
query_file="$(mktemp)"
trap 'rm -f "$metrics_file" "$query_file"' EXIT

curl --fail --silent --show-error --max-time 5 \
  "$backend_url/actuator/prometheus" > "$metrics_file"

required_metrics=(
  "auknowlog_quiz_generation_duration_seconds"
  "auknowlog_quiz_generation_attempts"
  "auknowlog_quiz_questions_total"
  "auknowlog_quiz_semantic_checks_total"
)

for metric in "${required_metrics[@]}"; do
  if ! grep -q "^# HELP ${metric} " "$metrics_file"; then
    echo "ERROR: backend does not expose ${metric}" >&2
    exit 1
  fi
done

if grep -Eq '(topic|question|quiz_id|request_id|document_id)=' "$metrics_file"; then
  echo "ERROR: a high-cardinality or content label was exposed" >&2
  exit 1
fi

curl --fail --silent --show-error --max-time 5 \
  "$prometheus_url/-/ready" > /dev/null
curl --fail --silent --show-error --max-time 5 --get \
  --data-urlencode 'query=up{job="auknowlog-backend"}' \
  "$prometheus_url/api/v1/query" > "$query_file"

python3 - "$query_file" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as query_result:
    payload = json.load(query_result)

results = payload.get("data", {}).get("result", [])
if payload.get("status") != "success" or not any(item.get("value", [None, "0"])[1] == "1" for item in results):
    raise SystemExit("ERROR: Prometheus is not scraping auknowlog-backend successfully")
PY

metric_family_count="$(grep -c '^# HELP auknowlog_' "$metrics_file")"
if grep -q '^# HELP auknowlog_ai_' "$metrics_file"; then
  ai_metric_status="present"
else
  ai_metric_status="not emitted yet (created after the first AI request)"
fi

echo "OK: backend Prometheus endpoint is reachable"
echo "OK: ${metric_family_count} Auknowlog metric families are exposed"
echo "OK: Prometheus target auknowlog-backend is UP"
echo "INFO: AI request metrics are ${ai_metric_status}"
echo "INFO: no OpenAI API request was made by this verification"
