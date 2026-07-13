#!/usr/bin/env bash
#
# tests/e2e/test.sh — task runner for docker-compose-based E2E tests.
#
# Reads `e2e.tibero.license` from e2e-test.properties and exposes it to
# docker-compose as TIBERO_LICENSE so the same configuration works for
# both host-side `mvn test` and `docker compose run`.

set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROPS="$DIR/e2e-test.properties"

# Tibero license host path — auto-extracted from properties (compose mount
# source). Empty if properties or key absent; compose then falls back to its
# default `./tibero/license.xml`.
if [[ -f "$PROPS" ]]; then
    TIBERO_LICENSE="$(grep '^e2e\.tibero\.license=' "$PROPS" | cut -d= -f2- || true)"
else
    TIBERO_LICENSE=""
fi
export TIBERO_LICENSE

COMPOSE=(docker compose -f "$DIR/docker-compose.yml" run --rm e2e-test)

# Flags that must be present whenever we override compose's default
# command. The license sysprop maps the JVM-side property to the
# in-container mount target (/run/tibero-license.xml from compose volume).
MVN_FLAGS=(
    -Dmaven.test.failure.ignore=true
    -Dpicocli.ansi=false
    -Dorg.fusesource.jansi.Ansi.disable=true
    -De2e.tibero.license=/run/tibero-license.xml
)

show_help() {
    cat <<EOF
Usage: ./test.sh [<command>]

Commands:
  (none)         Run all E2E tests
  oracle         Run only Oracle migration tests
  cubrid         Run only CUBRID migration tests
  mysql          Run only MySQL migration tests
  mariadb        Run only MariaDB migration tests
  informix       Run only Informix migration tests
  mssql          Run only MSSQL migration tests
  tibero         Run only Tibero migration tests
  cli            Run only CLI tests
  snapshot       Regenerate snapshot golden files (-Dsnapshot.update=true)
  help           Show this help

Tibero license (auto-detected from e2e-test.properties):
  ${TIBERO_LICENSE:-<not set — Tibero scenarios will skip>}
EOF
}

case "${1:-}" in
    "" | all)
        "${COMPOSE[@]}"
        ;;
    oracle)
        "${COMPOSE[@]}" mvn test "${MVN_FLAGS[@]}" -Dtest='OracleTo*Test'
        ;;
    cubrid)
        "${COMPOSE[@]}" mvn test "${MVN_FLAGS[@]}" -Dtest='CubridTo*Test'
        ;;
    mysql)
        "${COMPOSE[@]}" mvn test "${MVN_FLAGS[@]}" -Dtest='MySqlTo*Test'
        ;;
    mariadb)
        "${COMPOSE[@]}" mvn test "${MVN_FLAGS[@]}" -Dtest='MariaDbTo*Test'
        ;;
    informix)
        "${COMPOSE[@]}" mvn test "${MVN_FLAGS[@]}" -Dtest='InformixTo*Test'
        ;;
    mssql)
        "${COMPOSE[@]}" mvn test "${MVN_FLAGS[@]}" -Dtest='MsSqlTo*Test'
        ;;
    tibero)
        "${COMPOSE[@]}" mvn test "${MVN_FLAGS[@]}" -Dtest='TiberoTo*Test'
        ;;
    cli)
        "${COMPOSE[@]}" mvn test "${MVN_FLAGS[@]}" -Dtest='CliTest'
        ;;
    snapshot)
        "${COMPOSE[@]}" mvn clean test "${MVN_FLAGS[@]}" -Dsnapshot.update=true
        ;;
    help | -h | --help)
        show_help
        ;;
    *)
        echo "Unknown command: $1" >&2
        echo "" >&2
        show_help
        exit 1
        ;;
esac
