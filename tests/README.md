# CMT Tests

Two independent test suites live under `tests/`. This file holds the authoring conventions common to
both; the E2E suite documents its own setup and tooling in [tests/e2e/README.md](e2e/README.md).

| Path | What | Build | Runs against |
|------|------|-------|--------------|
| `tests/unit-test` | JUnit 6 unit / characterization tests | jar module in the root reactor, `unit-test` profile | the plugin sources directly |
| `tests/e2e` | migration end-to-end tests | standalone Maven project, **not** in the root reactor | the built CMT console + Testcontainers databases |
| `test/` (repo root) | legacy Eclipse test fragments | none | nothing |

The root `test/` directory is out of scope: it predates both suites, is not listed in any `<module>`,
is not compiled, not run in CI, and not maintained. Do not add to it and do not migrate it. Today the
exclusion holds only because nothing references it — treat it as dead code.

## Running

| | Command |
|---|---|
| Unit tests | `mvn -B -Punit-test test` (repo root) |
| E2E | see [tests/e2e/README.md](e2e/README.md) — needs Docker and `CMT_CONSOLE_HOME` |

Selecting a subset with `-Dtest=` needs `-Dsurefire.failIfNoSpecifiedTests=false`: the `unit-test`
profile also builds four plugin modules that carry no tests, and surefire fails a module where the
pattern matched nothing. A test inside a `@Nested` class is addressed as `Outer$Nested#method` —
`Outer#method` silently matches nothing.

```bash
mvn -B -Punit-test test -Dsurefire.failIfNoSpecifiedTests=false \
    -Dtest='CUBRIDDataTypeHelperTest$GetRemain#stringSpelling_returnsNull'   # quoted: keeps $GetRemain
```

## Writing a test case

### What belongs in `tests/unit-test`

Unit tests carry the detail. Pin each behaviour densely — the happy path, the edge cases, the error
paths, the ugly inputs — because here thoroughness is nearly free: the whole suite runs in seconds on
any machine with nothing installed. E2E answers one different question, and only that one: does a whole
migration still work once every piece is combined. It costs minutes, needs Docker, and runs as a CI
matrix. So a behaviour that a unit test can pin belongs in a unit test even when an E2E scenario happens
to cross the same code — the E2E run tells you *a* migration worked, the unit test tells you *which*
rule held, and names it when it breaks.

The bar for a unit test is not "in-memory", it is **hermetic and deterministic**: same result on any
machine, in any order, at any time. So a test must not depend on

- a live database, a socket, a spawned process — anything outside this JVM;
- the wall clock, the default timezone or locale, or `System` state it did not itself set and restore;
- files it did not create, or paths outside what the test owns;
- the order tests run in, or state another test left behind.

`@TempDir`, a checked-in resource under `src/test/resources`, an in-memory stream, a `mock()` — all
fine, every one of them is hermetic. The question to ask is only ever: could this fail on someone else's
machine for a reason that is not the production code?

One constraint here is a fact rather than a convention. The `unit-test` profile builds four plugins
— `common.log`, `common.configuration`, `cubridmigration.core`, `cubridmigration.command` (`pom.xml`) —
so a test importing `cubridmigration.ui`, `app`, `plugin` or `common.update` genuinely does not compile.
That is a classpath problem, and the normal answer is to add the module to the profile's `<modules>` and
write the test. E2E is not the fallback for code the unit suite cannot see yet.

Nothing in the suite needs a temporary file today, so there is no `@TempDir` to copy from. When you do
need one, this is the shape — JUnit creates the directory per test and deletes it afterwards, which is
why it stays hermetic (template, not copied from the suite):

```java
@Test
@DisplayName("the rule being verified")
void condition_expectation(@TempDir Path dir) throws Exception {
    // dir exists, is empty, is unique to this test, and is deleted afterwards
}
```

### Naming

| Carrier | Rule | Example |
|---------|------|---------|
| File / class | `<ClassUnderTest>Test.java`, same package as the production class | `informix/InformixDataTypeHelperTest.java` |
| Class `@DisplayName` | the production class's simple name | `@DisplayName("InformixDataTypeHelper")` |
| `@Nested` class | one per member under test, member name in PascalCase | `class GetJdbcDataTypeID` |
| Nested `@DisplayName` | the member name with empty parentheses | `@DisplayName("getJdbcDataTypeID()")` |
| Method, inside a `@Nested` | `<condition>_<expectation>`, two segments by default | `upperCaseTypeName_throwsIllegalArgumentException` |
| Method, in a class with no `@Nested` | `<memberUnderTest>_<expectation>` | `fillColumnMetadata_usesCharLengthForNchar` |
| Subject field | `private static final <Type> <ROLE>`; a second one takes a qualifier | `HELPER`, `HANDLER`, `LOADER`, `COLLECTION_HELPER` |

```java
@DisplayName("InformixDataTypeHelper")
class InformixDataTypeHelperTest {

    private static final InformixDataTypeHelper HELPER = InformixDataTypeHelper.getInstance(null);

    @Nested
    @DisplayName("getInstance()")
    class GetInstance {

        @Test
        @DisplayName("any version -> the same singleton")
        void anyVersion_returnsSameInstance() { ... }
    }
}
```

The commonest shape in the suite, filled in — a pure function, one call, one assertion
(`TiberoTypeFormatterTest`):

```java
@Test
@DisplayName("NUMBER(10) -> \"NUMBER(10,0)\"")
void numberPrecisionOnly_returnsNumberWithPrecision() {
    assertThat(TiberoTypeFormatter.format(createColumn("NUMBER", 10, null), HELPER))
            .isEqualTo("NUMBER(10,0)");
}
```

Groups take one of two forms. Where the cases divide by member — every data type helper — it is one
`@Nested` per member, and the group name is that member with parentheses. Where they do not divide
that way, group by behaviour instead and the group name carries no parentheses
(`TiberoTypeFormatterTest`, the `tibero/export/handler` classes, `@DisplayName("NUMBER format")`).
Split by member wherever the member split exists. Groups follow the production class's declaration
order, not importance order.

A class that pins only a slice of a large production class skips `@Nested` altogether and carries the
member name in the method's first segment instead — `TiberoSchemaFetcherTest`, `DBUtilsTest`,
`UpdateAutoIncColCurrentValueTaskTest`, `ScriptCommandHandlerTest`. The member name is carried either
by a `@Nested` or by the method name, never by both. Test classes are package-private.

Inside a group, order the tests broad to narrow: the parameterized happy path first, then the special
cases, then the null / empty / exception cases last.

Two segments keep the condition and the expectation readable at a glance; camelCase a compound
condition (`firstOctetAbove223_returnsFalse`) rather than splitting it, and add a third segment when
that genuinely reads better.

The expectation verb is third person singular: `returnsNull`, `throwsIllegalArgumentException`,
`keepsElementsVerbatim`, `rendersZeroPrecision`.

Trap: surefire's default includes are `Test*.java`, `*Test.java`, `*Tests.java`, `*TestCase.java`, so
the shared factories `testutil/TestColumnFactory` and `testutil/TestCatalogFactory` are loaded as
zero-test classes. Harmless, but do not name a new helper `Test*`.

### @DisplayName

**Required on every `@Test` and every `@ParameterizedTest`.** The three name carriers have distinct
jobs; none substitutes for another.

| Carrier | Job |
|---------|-----|
| method name | the code identifier: what you grep, what appears in stack traces, what `-Dtest=` takes |
| `@DisplayName` | the definition's human name: its title in the TC catalog and on the test-status page |
| `@ParameterizedTest(name = ...)` | the per-invocation label: shows the actual argument values |

`@ParameterizedTest(name = "...")` names each *invocation*. The *definition* — the test-template
container, which is what the TC catalog counts as one test case — takes its name from `@DisplayName`.
Without one, JUnit publishes the raw Java signature, e.g.
`variousTypes_returnsShownDataType(String, Integer, Integer, String)`.

Write the rule being verified, not the method name again:

```java
// no - restates the method name
@DisplayName("every accepted spelling -> standard shown data type")
void spelling_returnsStandardShownDataType(String spelling, String expected)

// yes - states the rule the data set covers
@DisplayName("every synonym collapses onto its standard CUBRID spelling")
void spelling_returnsStandardShownDataType(String spelling, String expected)
```

Style: no trailing period, no "should" / "verify that", ASCII `->` with spaces for input-to-output
mappings, do not repeat the enclosing `@Nested` name. Do not capitalise the first word for its own
sake; a name that opens on an identifier or a SQL type keeps that token's own case
(`@DisplayName("BINARY_FLOAT -> Types.FLOAT")`).

### Parameterized tests

`name` is not optional either: every `@ParameterizedTest` in the suite supplies one, and it opens with
`[{index}] ` so a failure report identifies the row. Annotation order is `@ParameterizedTest`,
`@DisplayName`, then the source annotation (`@CsvSource` / `@ValueSource` / `@MethodSource` /
`@NullAndEmptySource`), as in `InformixDataTypeHelperTest`:

```java
@ParameterizedTest(name = "[{index}] {0}(p={1}, s={2}) -> \"{3}\"")
@DisplayName("each type family renders its own precision and scale form")
@CsvSource(
        nullValues = "null",
        value = {
            // String types get their precision from the isGenericString() branch.
            "char,          10,     null,   char(10)",
            "varchar,       255,    null,   varchar(255)",

            // Exact numeric types get precision and scale.
            "decimal,       10,     2,      'decimal(10,2)'",
        })
```

- `nullValues = "null"` on any block that needs a real `null` argument. Without it an empty cell is
  `""` and the word `null` is the four-character string — the test then passes against the wrong input.
- Quote any cell containing a comma, or CSV parsing splits it: `'decimal(10,2)'`, not `decimal(10,2)`.
- Align the columns, and group the rows with `//` section comments naming the production branch each
  block exercises. Such comments explain the *data*; they never restate what the code does.

One input and one fixed expectation is `@ValueSource` instead — same commenting discipline
(`CUBRIDDataTypeHelperTest.IsValidDatatype`):

```java
@ParameterizedTest(name = "[{index}] \"{0}\" -> false")
@DisplayName("bad parentheses, out-of-range bounds and unknown types are rejected")
@ValueSource(
        strings = {
            // Unbalanced parentheses.
            "VARCHAR(2",
            "char(10))",
            // VARCHAR2 is an Oracle spelling, not a CUBRID synonym.
            "VARCHAR2(10)",
        })
void unsupportedDataTypeInstance_returnsFalse(String dataType) {
    assertThat(HELPER.isValidDatatype(dataType)).isFalse();
}
```

`@NullAndEmptySource` supplies `null` and `""` without spelling them as CSV cells, and stacks with
`@ValueSource` when blank belongs in the same case (same class):

```java
@ParameterizedTest(name = "[{index}] \"{0}\" -> false")
@DisplayName("null, empty and blank are all rejected")
@NullAndEmptySource
@ValueSource(strings = {"   "})
void nullEmptyOrBlank_returnsFalse(String dataType) {
    assertThat(HELPER.isValidDatatype(dataType)).isFalse();
}
```

### Assertions

Assert through AssertJ: `assertThat`, `assertThatThrownBy`, `assertThatCode` and their companions
(`tuple`, `entry`, `catchThrowable`, `assertThatExceptionOfType`). Only the assertion vocabulary is
AssertJ's; grouping stays JUnit's.

```java
assertThatThrownBy(() -> HELPER.getJdbcDataTypeID(catalog, "VARCHAR", 255, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Not supported Informix data type(VARCHAR)");
```

How deep an exception assertion goes depends on who throws:

| Thrower | Assert |
|---------|--------|
| CMT code, fixed message | the type **and** the exact `.hasMessage(...)`, quirks included — `"Not supported  Informix data type(decimal: p=10, s=2)"` really does carry two spaces |
| CMT code wrapping a cause | the type plus `.hasMessageContaining(...)` for the identifying fragments (source type name, column name) |
| The JDK (`NullPointerException`, `NumberFormatException`, `StringIndexOutOfBoundsException`) | the type only — the message is not ours to pin |

When one setup produces several independent facts and stopping at the first failure would hide the
others, group them so every one is reported (`TiberoPartitionMetadataLoaderTest`):

```java
assertAll(
        () -> assertThat(rangePartitionDesc).isEqualTo("DATE '2025-01-01'"),
        () -> assertThat(rangePreviewDDL).contains("DATE '2025-01-01'"),
        () -> assertThat(listPartitionDesc).isEqualTo("'EAST','WEST'"),
        () ->
                assertThat(listPreviewDDL)
                        .contains("PARTITION BY LIST(REGION)")
                        .contains("VALUES ('EAST','WEST')"));
```

AssertJ's `assertSoftly` does the same job; either is fine, but keep one style per class.

An assertion must be able to fail. Pin the actual value instead of asserting `isNotNull()` /
`isZero()` / `isNotEmpty()` on a value the fixture already makes null, zero or empty:

```java
// no - the fixture's scale is 0 anyway, so the assertion cannot fail
assertThat(columnOf("set_of(numeric(15,0))").getScale()).isZero();
// yes - a scale the parser has to actually carry through
assertThat(columnOf("set_of(numeric(15,3))").getScale()).isEqualTo(3);
```

Before trusting a new test, break the production code on purpose and confirm it fails. The plugin
sources are tracked, so revert with `git checkout -- <file>` when you are done, and check
`git status` before you commit.

### Mocks

Choose by construction cost, not by object size.

- **Cheap** — a no-arg constructor and setters (`MigrationConfiguration`, `SourceEntryTableConfig`,
  `Table`): build a real one and set only the fields the scenario needs. The untouched fields keep
  their production defaults, which is what the caller would really have handed you.
- **Repeated or awkward** — a valid object graph, several constructor arguments: add a factory to
  `testutil`, as `TestColumnFactory` and `TestCatalogFactory` already do.
- **Impossible, or behaviour-driven** — a live JDBC `ResultSet`, `Connection`, `PreparedStatement`,
  the filesystem, a spawned process; and any collaborator whose *behaviour* is the thing under test:
  one that has to throw on the third call, a listener that has to be notified, an interface the code
  delegates to. Mock those. Every `mock()` in the suite happens to be a JDBC object today because the
  JDBC loaders are what has been covered so far, not because other seams are off limits.

Do not mock a value carrier you could construct. `MigrationCfgUtils.checkAll(config)` reads two getters
in its own body, then hands the same config to five private methods; twenty-one distinct getters are
read across the file. A mock stubbed for the two visible ones answers `false` for `sourceIsCSV()` and
silently sends the code down the other branch — the stub, not the test, decides what runs.

Stub with the static `mock()` / `when()` API: it keeps the stubs next to the data they stand for, and
it is what every existing test reads like. Use `doThrow` / `doAnswer` when the method is `void` or the
answer has to be computed, and `@Mock` with `MockitoExtension` in a class where every test needs the
same mock — just don't mix the two styles in one file. A JDBC read is stubbed as the chain the
production code walks —
`Connection` hands out the statement, the statement hands out the `ResultSet` — and the rows are the
consecutive-return form of `when()`: `next()` returns `true` once per row then `false`, and each getter
returns its column's value for row 1, row 2, and so on (`TiberoConstraintIndexMetadataLoaderTest`):

```java
Connection conn = mock(Connection.class);
PreparedStatement stmt = mock(PreparedStatement.class);
ResultSet rs = mock(ResultSet.class);
when(conn.prepareStatement(anyString())).thenReturn(stmt);
when(stmt.executeQuery()).thenReturn(rs);
when(rs.next()).thenReturn(true, true, false);
when(rs.getString("FK_NAME")).thenReturn("FK_EMP_DEPT", "FK_EMP_DEPT");
when(rs.getString("FK_COLUMN_NAME")).thenReturn("DEPT_ID", "DEPT_CODE");
when(rs.getString("PK_COLUMN_NAME")).thenReturn("ID", "CODE");

Schema schema = createSchema("HR");
Table table = createTable("EMP", "DEPT_ID", "DEPT_CODE");

LOADER.buildTableFKs(conn, schema, table, FACTORY);

assertThat(table.getFks()).hasSize(1);
```

Where every test in the class needs the same fresh mock, build it in `@BeforeEach` and keep the
stateless subject in a `static final` field (`TiberoJsonTypeHandlerTest`). When a loader walks several
statements and many rows, move that wiring into a `private static final class TestContext` holding the
stubs plus small `addX()` / `stubX()` helpers, so each test reads as the data it declares rather than as
Mockito setup (`TiberoPartitionMetadataLoaderTest.TestContext`).

To exercise an abstract class, subclass it rather than mocking it: a `private static class` at the
bottom of the file, answering the abstract methods as narrowly as the inherited behaviour needs. Only
the method that behaviour actually consults matters — for `BaseHelper` that is `isCollection()`, reached
from `parseDTInstance()`. A second variant flips it to select the other branch
(`DBDataTypeHelperTest.BaseHelper`, `DBDataTypeHelperTest.CollectionAwareHelper`):

```java
private static class BaseHelper extends DBDataTypeHelper {

    @Override
    public boolean isCollection(String dataType) {
        return false;
    }

    // ... the remaining abstract methods, answered as narrowly
}

/** Variant whose dialect knows collection types, to exercise the sub type parsing branch. */
private static class CollectionAwareHelper extends BaseHelper {

    @Override
    public boolean isCollection(String dataType) {
        return checkType("/set/multiset/sequence/", dataType);
    }
}
```

Prefer asserting returned state over asserting interactions: an interaction assertion pins *how* the
code works rather than *what* it produces, and breaks on refactors that change nothing observable.
Reach for `verify()` when the interaction **is** the behaviour — a resource being closed, a callback
being fired.

### Test data

Use the static factories in `tests/unit-test/src/test/java/com/cubrid/cubridmigration/testutil/`
rather than hand-building domain objects.

| Factory | Methods |
|---------|---------|
| `TestColumnFactory` | `createColumn(dataType)`, `createColumn(dataType, precision, scale)`, `createColumnWithDefault(dataType, defaultValue)`, `createCharColumn(dataType, precision, charUsed)` |
| `TestCatalogFactory` | `createCatalog(key, DataType...)`, `createCatalog(key, jdbcTypeId)`, `createDataType(typeName, jdbcTypeId)` |

`createColumn` names the column `TEST_COL`; assertions on messages that echo the column name depend on
that. Add a factory method when a shape starts being reused; keep one-offs local to the test class.

### Characterization tests and `// DEFECT:`

Several suites — the data type helpers in particular — are characterization tests. They pin *current*
behaviour, including behaviour that is wrong, so a refactor cannot change it silently. A row marked
`DEFECT` is not a broken test; it is a deliberately recorded bug. Wrong behaviour gets pinned and
annotated, never switched off: a `@Disabled` test verifies nothing. `@Tag` is fine when a group of
tests needs to be selectable from the command line.

Mark a pinned defect with a `// DEFECT:` comment giving a short explanation and a citation. Cite the
member, `<ProductionClass>.<member>()`, never `File.java:LINE` — line numbers rot:

```java
void upperCaseTypeName_throwsIllegalArgumentException() {
    // DEFECT: the raw data type is used as the map key with no case folding, so only an exact
    // key match resolves. The keys are the driver's TYPE_NAME values, stored verbatim by
    // AbstractJDBCSchemaFetcher.getSupportedSqlTypes()
    // - see InformixDataTypeHelper.getJdbcDataTypeID()
```

A comment here explains the data, a defect, or why a test double is shaped as it is; it never restates
what the code plainly does — the project prefers self-documenting code.

## E2E specifics

Prerequisites, the `test.sh` dispatcher, snapshot regeneration, the Tibero bring-your-own assets and
the scenario-id table are in [tests/e2e/README.md](e2e/README.md). Only the authoring contract is here.

E2E has two class shapes, and the cheap one comes first.

A **functional test** needs no database: it drives the console directly through
`@RegisterExtension final CmtTestContext ctx = new CmtTestContext()`, as `CliTest` does for
`migration.sh` dispatch and the first-run filesystem contracts. Its own javadoc says why it is shaped
that way: "No DB required, runs in seconds and gates the rest of the E2E suite." Whenever a fact can be
checked without starting a container, this is the shape to reach for.

A **migration scenario** extends `AbstractMigrationE2E`, which is `@TestInstance(PER_CLASS)`: it starts
source and target and runs **one** migration in a non-static `@BeforeAll`, then caches the outcome, and
each `@Test` asserts against that one result via `run()`. A failure in `@BeforeAll` fails the whole
class at once, so keep the setup in the framework and keep each test to the facts that fail together:
one snapshot comparison, or a couple of chained expectations about the same outcome as
`migration_succeeds()` does with `run().expectSuccess().expectNoFatalStderr()`. What does not belong in
a `@Test` is setup.

The unit-test naming rules do not carry over: E2E uses its own dialect, and it differs between the two
shapes. Method names are `snake_case`; match the neighbouring class in the same package, which reads
`row_counts_match_snapshot` for a migration scenario and
`should_listAllSubcommands_when_calledWithoutArgs` in `CliTest`. `@DisplayName` states the fact in
plain words and may use `→`.

A scenario class extends `AbstractMigrationE2E`, implements the two abstract methods `source()` and
`target()` from a `Sources.<engine>E2eSeed()` and a `Targets.cubridOnline()` / `Targets.unload(prefix)`
builder, and carries `@MigrationE2E(name, options)`. `name` follows
`<source>_to_<target>[__<discriminator>]` and resolves to `src/test/resources/snapshots/<name>/` and
`src/test/resources/queries/<name>.sql`. `options` must list every CMT option whose value affects
verification — relying on a CMT default couples the test to a default that can shift between releases.

```java
@MigrationE2E(name = "oracle_to_cubrid", options = {"add_schema=true"})
@DisplayName("Oracle e2e dataset → CUBRID online migration")
class OracleToCubridTest extends AbstractMigrationE2E {

    @Override protected Source source() { return Sources.oracleE2eSeed(); }
    @Override protected Target target() { return Targets.cubridOnline().addSchema(true).build(); }

    @Test
    @DisplayName("All target tables match snapshot")
    void tables_match_snapshot() {
        run().catalog().matchesSnapshot("tables");
    }
}
```

Option strings use human values (`add_schema=true`); the target builders emit `yes`/`no` on the wire,
so the annotation is documentation and is not read at runtime — it, the builder chain, and
`Targets.unload(prefix)` against `file_prefix=` have to be kept in step by hand. Option combinations go
in a `@Nested` class with its own `@MigrationE2E` and its own `extends AbstractMigrationE2E`; the outer
class is then a bare namespace that extends nothing, as the `*ToUnloadTest` classes do.

Verify through `run()` rather than opening your own connection: it hands back the one cached migration,
and its comparisons are what the snapshot files are written from.

| Call | Compares against | Note |
|------|------------------|------|
| `.expectSuccess()`, `.expectNoFatalStderr()`, `.expectImportMatchesExport()` | — | exit code, stderr, migration report |
| `.catalog().matchesSnapshot(name)` | `snapshots/<scenario>/<name>.txt` | `name` must be one of the fixed keys in `CatalogQueries` — `tables`, `views`, `columns`, `pk`, `fk`, `unique`, `indexes`, `sequences`, `synonyms`, `grants`, `functions`, `procedures`. Anything else throws `Unknown catalog query` |
| `.rowCounts().matchesSnapshot(name)` | `snapshots/<scenario>/<name>.txt` | free name; the convention is `row_counts` |
| `.queries(Path.of("src/test/resources/queries/<scenario>.sql")).matchesSnapshot(name)` | `snapshots/<scenario>/<name>.txt` | free name; the convention is `representative_rows` |
| `.dumpfile().matchesSnapshot()` | `snapshots/<scenario>/dumpfile/` | no argument, and a directory tree rather than one file |

A new scenario therefore needs: a seed under `src/test/resources/db/<engine>/`, a `Sources` factory
method if the engine is new, the class, a `queries/<scenario>.sql` if it spot-checks rows, the golden
files (first run with `-Dsnapshot.update=true`, then **read what it wrote** — update mode makes every
snapshot assertion pass), a `test.sh` command, and a `.github/workflows/ci.yml` matrix entry. Each
matrix job runs `mvn test -Dtest='<glob>'`, so a class that no glob selects is never run by CI even
though it compiles and passes locally. Tibero is permanently in that state: `ci.yml` has no `tibero`
entry, the Tibero classes are additionally gated by
`@EnabledIf("com.cmt.e2e.framework.db.containers.TiberoEnvironment#isAvailable")`, and their assets are
not distributable. A Tibero test will not be verified by CI: run it locally and say so in the PR.

## Why these rules exist

The TC catalog and the test-status page publish these names verbatim. A teammate browsing the catalog
sees the `@DisplayName` text and the `@Nested` chain — not the code, not the method name. A display
name that merely restates the method name therefore wastes the only human-readable field there is, and
a missing one publishes a raw Java signature to a page people read instead of the source. That page
requirement is the context a previous cleanup was missing: it deleted display names because they
duplicated the method name, which is locally sensible and globally wrong. Rewrite such a name to state
the rule being verified; do not delete it.
