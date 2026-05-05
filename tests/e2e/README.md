# CMT E2E Tests

End-to-end migration tests: source DB → `migration.sh` → target DB
round-trip verification.

Active sources: Oracle / CUBRID / Tibero. Targets: online CUBRID,
CMT unload (LoadDB) dump.

## Prerequisites

- Docker daemon running.
- CMT Console binary extracted; export `CMT_CONSOLE_HOME` to its path.

## Running

```bash
cd tests/e2e

# All scenarios
docker compose run --rm e2e-test

# Single scenario / single fact
docker compose run --rm e2e-test mvn test -Dtest=OracleToCubridTest
docker compose run --rm e2e-test mvn test -Dtest='OracleToCubridTest#serials_match_snapshot'
```

## Snapshot update

When CMT output legitimately changes (bug fix, naming convention shift,
new column type), regenerate the golden files:

```bash
docker compose run --rm e2e-test mvn test \
    -Dtest='OracleToCubridTest,CubridToCubridTest' \
    -Dsnapshot.update=true
```

Review the diff before committing.

## Tibero

Executing Tibero scenarios requires a bring your own assets setup.

- **JDBC jar** — must be at `tests/e2e/lib/tibero7-jdbc-17.jar`
  (Maven profile activates only when this exact path exists)
- **License file** — anywhere on the host; absolute path set via
  `e2e.tibero.license`
- **Docker image** — built or pulled into the local Docker daemon;
  name set via `e2e.tibero.image`

Plus four keys in `tests/e2e/e2e-test.properties` (template:
`e2e-test.properties.example`): `image` / `hostname` / `license` /
`faketime`.

When any asset or key is missing, `TiberoToCubridTest` and
`TiberoToUnloadTest` auto-skip via `@EnabledIf`. Other scenarios are
unaffected.

When running via `docker compose run`, the license file is mounted
into the container by path. Default mount source is host-side
`tests/e2e/tibero/license.xml`. To use a license at any other host
path, set `TIBERO_LICENSE` env var:

```bash
TIBERO_LICENSE=/absolute/path/to/license.xml docker compose run --rm e2e-test
```

Host-side `mvn test` (without compose) honours the `e2e.tibero.license`
value in `e2e-test.properties` directly — no env var needed.

## Naming

| Kind | Scenario id | Class |
|------|-------------|-------|
| flat | `oracle_to_cubrid` | `OracleToCubridTest` |
| `@Nested` variant | `oracle_to_unload__split_per_table` | `OracleToUnloadTest.SplitPerTable` |
| regression | `oracle_to_cubrid__bug_tools_1234` | `OracleToCubridBugTools1234Test` (in `regression/`) |

Bug regressions: Jira id `TOOLS-1234` → identifier `tools_1234`.
