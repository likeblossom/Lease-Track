#!/usr/bin/env bash
set -u

BASE_URL="${API_BASE_URL:-http://localhost:8080}"
BASE_URL="${BASE_URL%/}"
RUN_ID="$(date +%s)"
TMP_DIR="$(mktemp -d)"
FAILURES=0
PASSES=0

cleanup() {
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    printf 'Missing required command: %s\n' "$1" >&2
    exit 2
  fi
}

require_command curl
require_command jq

record_pass() {
  printf 'PASS %-72s %s\n' "$1" "$2"
  PASSES=$((PASSES + 1))
}

record_fail() {
  printf 'FAIL %-72s got %s expected %s\n' "$1" "$2" "$3"
  if [ -f "$4" ]; then
    sed -n '1,5p' "$4"
  fi
  FAILURES=$((FAILURES + 1))
}

status_matches() {
  printf '%s' "$1" | tr ',' '\n' | grep -qx "$2"
}

request() {
  local label="$1"
  local method="$2"
  local endpoint="$3"
  local expected="$4"
  local data="${5:-}"
  shift 5

  local body="$TMP_DIR/$(printf '%s' "$label" | tr ' /{}?' '______').body"
  local args=(-sS -o "$body" -w '%{http_code}' --max-time 20 -X "$method")
  if [ -n "$data" ]; then
    args+=(-H 'Content-Type: application/json' -d "$data")
  fi

  local code
  code="$(curl "${args[@]}" "$@" "$BASE_URL$endpoint" 2>/dev/null || printf '000')"

  if status_matches "$expected" "$code"; then
    record_pass "$method $endpoint" "$code"
  else
    record_fail "$method $endpoint" "$code" "$expected" "$body"
  fi

  return 0
}

request health GET /api/health 200 ""
request actuator GET /actuator/health 200 ""
request liveness GET /actuator/health/liveness 200 ""
request readiness GET /actuator/health/readiness 200 ""
request info GET /actuator/info 200 ""
request openapi GET /v3/api-docs 200 ""
request swagger GET /swagger-ui/index.html 200 ""
request notices_unauth GET /api/notices 401,403 ""
request invitation_unauth POST /api/auth/invitations 401,403 \
  '{"email":"nobody@example.com","role":"TENANT"}'

PASSWORD='Password123!'
USER_EMAIL="endpoint.admin.$RUN_ID@example.com"
USER_ROLE="ADMIN"
REGISTER_BODY="$(printf '{"email":"%s","password":"%s","displayName":"Endpoint Admin","role":"ADMIN"}' "$USER_EMAIL" "$PASSWORD")"
register_body="$TMP_DIR/register.body"
register_code="$(curl -sS -o "$register_body" -w '%{http_code}' --max-time 20 \
  -H 'Content-Type: application/json' \
  -d "$REGISTER_BODY" \
  "$BASE_URL/api/auth/register" 2>/dev/null || printf '000')"

if [ "$register_code" != "201" ]; then
  USER_EMAIL="endpoint.landlord.$RUN_ID@example.com"
  USER_ROLE="LANDLORD"
  REGISTER_BODY="$(printf '{"email":"%s","password":"%s","displayName":"Endpoint Landlord","role":"LANDLORD"}' "$USER_EMAIL" "$PASSWORD")"
  register_code="$(curl -sS -o "$register_body" -w '%{http_code}' --max-time 20 \
    -H 'Content-Type: application/json' \
    -d "$REGISTER_BODY" \
    "$BASE_URL/api/auth/register" 2>/dev/null || printf '000')"
fi

if [ "$register_code" = "201" ]; then
  record_pass "POST /api/auth/register ($USER_ROLE)" "$register_code"
else
  record_fail "POST /api/auth/register" "$register_code" "201" "$register_body"
fi

login_body="$TMP_DIR/login.body"
LOGIN_BODY="$(printf '{"email":"%s","password":"%s"}' "$USER_EMAIL" "$PASSWORD")"
login_code="$(curl -sS -o "$login_body" -w '%{http_code}' --max-time 20 \
  -H 'Content-Type: application/json' \
  -d "$LOGIN_BODY" \
  "$BASE_URL/api/auth/login" 2>/dev/null || printf '000')"
TOKEN="$(jq -r '.accessToken // empty' "$login_body" 2>/dev/null)"
if [ "$login_code" = "200" ] && [ -n "$TOKEN" ]; then
  record_pass "POST /api/auth/login" "$login_code"
else
  record_fail "POST /api/auth/login" "$login_code" "200 with accessToken" "$login_body"
fi

tenant_email="endpoint.tenant.$RUN_ID@example.com"
invite_body="$TMP_DIR/invite.body"
INVITE_BODY="$(printf '{"email":"%s","displayName":"Endpoint Tenant","role":"TENANT"}' "$tenant_email")"
invite_code="$(curl -sS -o "$invite_body" -w '%{http_code}' --max-time 20 \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d "$INVITE_BODY" \
  "$BASE_URL/api/auth/invitations" 2>/dev/null || printf '000')"
INVITE_TOKEN="$(jq -r '.token // empty' "$invite_body" 2>/dev/null)"
if [ "$invite_code" = "201" ] && [ -n "$INVITE_TOKEN" ]; then
  record_pass "POST /api/auth/invitations" "$invite_code"
else
  record_fail "POST /api/auth/invitations" "$invite_code" "201 with token" "$invite_body"
fi

accept_body="$TMP_DIR/accept.body"
ACCEPT_BODY="$(printf '{"token":"%s","password":"Tenant123!","displayName":"Endpoint Tenant"}' "$INVITE_TOKEN")"
accept_code="$(curl -sS -o "$accept_body" -w '%{http_code}' --max-time 20 \
  -H 'Content-Type: application/json' \
  -d "$ACCEPT_BODY" \
  "$BASE_URL/api/auth/invitations/accept" 2>/dev/null || printf '000')"
if [ "$accept_code" = "201" ]; then
  record_pass "POST /api/auth/invitations/accept" "$accept_code"
else
  record_fail "POST /api/auth/invitations/accept" "$accept_code" "201" "$accept_body"
fi

request notices_auth GET /api/notices 200 "" -H "Authorization: Bearer $TOKEN"

notice_body="$TMP_DIR/notice.body"
NOTICE_BODY='{"recipientName":"Test Recipient","recipientContactInfo":"test@example.com","noticeType":"RENT_INCREASE","deliveryMethod":"REGISTERED_MAIL","deadlineAt":"2026-06-01T12:00:00Z","notes":"Endpoint smoke test"}'
notice_code="$(curl -sS -o "$notice_body" -w '%{http_code}' --max-time 20 \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d "$NOTICE_BODY" \
  "$BASE_URL/api/notices" 2>/dev/null || printf '000')"
NOTICE_ID="$(jq -r '.id // empty' "$notice_body" 2>/dev/null)"
ATTEMPT_ID="$(jq -r '.deliveryAttempts[0].id // empty' "$notice_body" 2>/dev/null)"
if [ "$notice_code" = "201" ] && [ -n "$NOTICE_ID" ] && [ -n "$ATTEMPT_ID" ]; then
  record_pass "POST /api/notices" "$notice_code"
else
  record_fail "POST /api/notices" "$notice_code" "201 with notice and attempt ids" "$notice_body"
fi

request notice_get GET "/api/notices/$NOTICE_ID" 200 "" -H "Authorization: Bearer $TOKEN"
request notice_list_filter GET '/api/notices?status=OPEN&noticeType=RENT_INCREASE&deliveryMethod=REGISTERED_MAIL&page=0&size=5' 200 "" -H "Authorization: Bearer $TOKEN"
request attempt_sent PATCH "/api/notices/$NOTICE_ID/attempts/$ATTEMPT_ID/status" 200 \
  '{"status":"SENT"}' -H "Authorization: Bearer $TOKEN"
request evidence POST "/api/notices/$NOTICE_ID/attempts/$ATTEMPT_ID/evidence" 200 \
  '{"carrierName":"Canada Post","trackingNumber":"1234567890123456","deliveryConfirmation":true,"carrierReceiptRef":"receipt-1"}' \
  -H "Authorization: Bearer $TOKEN"
request docs_list_empty GET "/api/notices/$NOTICE_ID/attempts/$ATTEMPT_ID/evidence/documents" 200 "" \
  -H "Authorization: Bearer $TOKEN"

upload_file="$TMP_DIR/carrier-receipt.txt"
printf 'endpoint smoke receipt\n' > "$upload_file"
upload_body="$TMP_DIR/upload.body"
upload_code="$(curl -sS -o "$upload_body" -w '%{http_code}' --max-time 20 \
  -H "Authorization: Bearer $TOKEN" \
  -F documentType=CARRIER_RECEIPT \
  -F file=@"$upload_file" \
  "$BASE_URL/api/notices/$NOTICE_ID/attempts/$ATTEMPT_ID/evidence/documents" 2>/dev/null || printf '000')"
if [ "$upload_code" = "200" ]; then
  record_pass "POST /api/notices/{noticeId}/attempts/{attemptId}/evidence/documents" "$upload_code"
else
  record_fail "POST /api/notices/{noticeId}/attempts/{attemptId}/evidence/documents" "$upload_code" "200" "$upload_body"
fi

request docs_list_after GET "/api/notices/$NOTICE_ID/attempts/$ATTEMPT_ID/evidence/documents" 200 "" \
  -H "Authorization: Bearer $TOKEN"
request audit_log GET "/api/notices/$NOTICE_ID/audit-log" 200 "" -H "Authorization: Bearer $TOKEN"
request evidence_package GET "/api/notices/$NOTICE_ID/evidence-package" 200 "" -H "Authorization: Bearer $TOKEN"
request evidence_package_pdf GET "/api/notices/$NOTICE_ID/evidence-package.pdf" 200 "" \
  -H "Authorization: Bearer $TOKEN" -H 'Accept: application/pdf'

printf '\nEndpoint smoke summary: %s passed, %s failed\n' "$PASSES" "$FAILURES"

if [ "$FAILURES" -gt 0 ]; then
  exit 1
fi
