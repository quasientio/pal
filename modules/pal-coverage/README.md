# PAL Coverage Reports

This module generates aggregate JaCoCo coverage reports combining data from all PAL modules.

## Usage

Run from the **project root** (not this module directly):

```bash
# Option 1: Full build with all tests (generates reports automatically)
mvn clean install -DskipITs && mvn verify -DskipUTs

# Option 2: Regenerate reports only (using existing exec files from previous test runs)
mvn verify -pl modules/pal-coverage -am -DskipTests -DskipITs
```

**Note:** Option 1 requires two phases because integration tests spawn separate JVM processes
that need the shaded JAR installed in the local Maven repository before they can run. The
`-am` flag in option 2 builds dependencies, required because `itt` has `maven.install.skip=true`.

## Generated Reports

Reports are generated in `target/site/`:

| Directory | Description | Use When |
|-----------|-------------|----------|
| `jacoco-aggregate/` | Unit test coverage only | Evaluating unit test quality |
| `jacoco-aggregate-it/` | Integration test coverage only | Evaluating IT coverage of runtime code |
| `jacoco-aggregate-all/` | Combined unit + IT coverage | Overall coverage assessment |

Open `index.html` in any directory to view the HTML report, or use `jacoco.csv` for
programmatic analysis.

### Which Report to Use

- **Unit test report** (`jacoco-aggregate/`): Most accurate for PAL classes. Use this to
  identify gaps in unit test coverage. No bytecode mismatch warnings for PAL code.

- **IT report** (`jacoco-aggregate-it/`): Shows what code paths are exercised by integration
  tests. Useful for understanding end-to-end coverage. Only generated when ITs have run.

- **Combined report** (`jacoco-aggregate-all/`): Provides the "big picture" but may
  undercount unit test coverage due to bytecode differences between shaded/unshaded classes.
  Use for overall assessment, but prefer the unit test report for detailed analysis.

### Understanding Coverage Metrics

JaCoCo reports three coverage metrics:

| Metric | What It Measures | Target |
|--------|------------------|--------|
| **Instructions** | JVM bytecode instructions executed | 80% |
| **Lines** | Source code lines executed | 80% |
| **Branches** | Decision paths taken (if/else, switch) | 70% |

**Branch coverage is typically lower than line coverage** because:
- Each `if` statement requires tests for both true and false paths
- Guard clauses and null checks often only test the happy path
- Error handling paths (catch blocks) are frequently untested
- Short-circuit evaluation (`a && b`) has multiple branch combinations

### Reading the CSV Reports

Each report includes a `jacoco.csv` file for programmatic analysis:

```bash
# View coverage by module (unit tests)
awk -F',' 'NR>1 {split($1,p,"/"); m[p[2]]+=$4; c[p[2]]+=$5}
  END {for(x in m) printf "%s: %.1f%%\n", x, c[x]/(m[x]+c[x])*100}' \
  target/site/jacoco-aggregate/jacoco.csv

# Find lowest-coverage classes
awk -F',' 'NR>1 {t=$4+$5; if(t>0) printf "%.1f%% %s.%s\n", $5/t*100, $2, $3}' \
  target/site/jacoco-aggregate/jacoco.csv | sort -n | head -20
```

CSV columns: `GROUP, PACKAGE, CLASS, INSTRUCTION_MISSED, INSTRUCTION_COVERED,
BRANCH_MISSED, BRANCH_COVERED, LINE_MISSED, LINE_COVERED, COMPLEXITY_MISSED,
COMPLEXITY_COVERED, METHOD_MISSED, METHOD_COVERED`

## How It Works

1. **Unit test coverage**: Each module generates `target/jacoco.exec` during `mvn test`
2. **Integration test coverage**: The `itt` module's integration tests spawn peer and CLI
   processes with JaCoCo agents, generating `jacoco-peer-*.exec` and `jacoco-cli-*.exec` files
3. **Shaded JAR handling**: The `pal-cli` module uses maven-shade-plugin which modifies
   bytecode (package relocations, minimization). Integration tests run against the shaded
   JAR, so IT coverage reports must use the shaded classes to match bytecode checksums.
4. **Report generation**:
   - Unit test report: Uses `report-aggregate` with unshaded `target/classes`
   - IT report: Unpacks shaded JAR, uses `report` goal with those classes
   - Combined report: Uses shaded classes (IT coverage accurate, unit coverage partial)

## Expected Warnings

During report generation, you may see JaCoCo warnings like:

```
[WARN] Some classes do not match with execution data.
[WARN] Execution data for class io/quasient/pal/... does not match.
```

**These warnings are expected** and occur because:

1. **IT report**: May warn about third-party classes (grpc, etcd, etc.) where the bytecode
   in the shaded JAR differs from what was actually executed (due to JVM optimizations or
   different class loading).

2. **Combined report**: Will warn about PAL classes where unit test coverage (recorded
   against unshaded classes) doesn't match the shaded bytecode. This is unavoidable since:
   - Unit tests run against `target/classes` (unshaded)
   - IT tests run against the shaded JAR
   - Combined report must use one set of class files

The **unit test report** (`jacoco-aggregate/`) should have no warnings for PAL classes
since it uses unshaded classes that match the unit test exec files.

## Viewing Coverage for a Single Module

For per-module coverage (without aggregation):

```bash
# Generate coverage for a specific module
mvn test jacoco:report -pl modules/pal-runtime

# View report
open modules/pal-runtime/target/site/jacoco/index.html
```
