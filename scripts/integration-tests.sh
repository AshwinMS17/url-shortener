#!/usr/bin/env bash
#
# Runs the full build INCLUDING the Testcontainers-backed integration tests
# (DynamoDbUrlRepositoryTest), which spin up LocalStack in Docker.
#
# Plain `mvn verify` SKIPS those tests unless a Docker daemon is reachable.
# This repo targets a Colima daemon, which needs two hints that can't live in
# pom.xml because they are machine-specific:
#
#   DOCKER_HOST                          Colima's socket is not at /var/run/docker.sock
#   TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE  the in-VM socket path Ryuk bind-mounts
#
# The Docker Remote API version pin (-Dapi.version) lives in pom.xml's
# maven-surefire-plugin <argLine>; Colima's engine rejects docker-java's default.
#
# Usage:  ./scripts/integration-tests.sh [extra maven args]
#
set -euo pipefail

if ! command -v colima >/dev/null 2>&1; then
  echo "colima is not installed. See README 'Running the DynamoDB tests'." >&2
  exit 1
fi

if ! colima status >/dev/null 2>&1; then
  echo "Colima is not running - starting it (colima start)..."
  colima start
fi

sock="${HOME}/.colima/default/docker.sock"
if [ ! -S "${sock}" ]; then
  echo "Expected Colima docker socket at ${sock} but it is not there." >&2
  echo "Check 'colima status' for the real path." >&2
  exit 1
fi

export DOCKER_HOST="unix://${sock}"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE="/var/run/docker.sock"

echo "DOCKER_HOST=${DOCKER_HOST}"
exec mvn verify "$@"
