#!/usr/bin/env bash
set -u

API_BASE_URL="${API_BASE_URL:-http://localhost:8080}"
BACKEND_HEALTH_REQUIRED="${BACKEND_HEALTH_REQUIRED:-0}"

failures=0

pass() {
  printf 'PASS %s\n' "$1"
}

warn() {
  printf 'WARN %s\n' "$1"
}

fail() {
  printf 'FAIL %s\n' "$1"
  failures=$((failures + 1))
}

major_version() {
  printf '%s' "$1" | sed -E 's/^1\.([0-9]+).*/\1/; s/^([0-9]+).*/\1/'
}

check_command() {
  if command -v "$1" >/dev/null 2>&1; then
    pass "$1 is installed at $(command -v "$1")"
  else
    fail "$1 is not installed or not on PATH"
  fi
}

check_command java
check_command mvn
check_command docker
check_command curl
check_command jq

if command -v java >/dev/null 2>&1; then
  java_output="$(java -version 2>&1 | head -n 1)"
  java_version="$(printf '%s' "$java_output" | sed -E 's/.*version "([^"]+)".*/\1/')"
  java_major="$(major_version "$java_version")"
  if [ "$java_major" -ge 21 ] 2>/dev/null; then
    pass "java uses version $java_version"
  else
    fail "java uses version $java_version; Lease Track requires Java 21+"
  fi
fi

if command -v mvn >/dev/null 2>&1; then
  maven_java_line="$(mvn -version 2>/dev/null | awk '/Java version:/ {print; exit}')"
  maven_java_version="$(printf '%s' "$maven_java_line" | sed -E 's/.*Java version: ([^,]+),.*/\1/')"
  maven_java_major="$(major_version "$maven_java_version")"
  if [ "$maven_java_major" -ge 21 ] 2>/dev/null; then
    pass "mvn uses Java $maven_java_version"
  else
    fail "mvn uses Java ${maven_java_version:-unknown}; set JAVA_HOME to a Java 21+ JDK"
  fi
fi

if command -v docker >/dev/null 2>&1; then
  if docker info >/dev/null 2>&1; then
    docker_context="$(docker context show 2>/dev/null || printf 'unknown')"
    pass "Docker is reachable with context $docker_context"
  else
    fail "Docker CLI cannot reach the active Docker context"
  fi
fi

if command -v curl >/dev/null 2>&1; then
  health_url="${API_BASE_URL%/}/api/health"
  health_code="$(curl -sS -o /dev/null -w '%{http_code}' --max-time 5 "$health_url" 2>/dev/null || printf '000')"
  if [ "$health_code" = "200" ]; then
    pass "backend health is reachable at $health_url"
  elif [ "$BACKEND_HEALTH_REQUIRED" = "1" ]; then
    fail "backend health is not reachable at $health_url (HTTP $health_code)"
  else
    warn "backend health is not reachable at $health_url (HTTP $health_code); set BACKEND_HEALTH_REQUIRED=1 to fail on this"
  fi
fi

if [ "$failures" -gt 0 ]; then
  cat <<'EOF'

Java 21 example for this machine:
JAVA_HOME=/Users/amandayuen/Library/Java/JavaVirtualMachines/openjdk-21.0.2/Contents/Home \
PATH=/Users/amandayuen/Library/Java/JavaVirtualMachines/openjdk-21.0.2/Contents/Home/bin:$PATH \
mvn test
EOF
  exit 1
fi

printf '\nDeveloper environment checks passed.\n'
