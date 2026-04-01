# Running PAL's integration tests

## Dependencies
PAL's integration tests depend on a running instance or cluster of __etcd__ and __kafka__. For convenience, we provide
docker images and launch scripts for both, so all you need is __Docker installed and running__. Follow the
instructions in the next section to download our docker images for the corresponding binaries.

If you have etcd and/or kafka installed on your machine or an accessible host, then skip to
[Running the tests](#Running-the-tests). If either `etcd` or `kafka` are not listening on localhost or their standard
ports (2379 and 29092 respectively), then adjust the corresponding variables in the `export-env` file.

### Download Etcd and Kafka docker images
Pull and tag the provided images for etcd and kafka:
```bash
docker pull gitlab.cometera.org:5050/quasient/pal/etcd:latest && docker tag gitlab.cometera.org:5050/quasient/pal/etcd:latest etcd:latest
docker pull apache/kafka:3.8.0 && docker tag apache/kafka:3.8.0 kafka:latest
```

## Running the tests
__NOTE__: if you already added `$PAL_HOME/bin` and `$PAL_HOME/infra/bin` to your PATH variable, you may omit `bin/` from the instructions following.

1. Open a terminal and export the ENV variables required to run the tests
    ```bash
    source export-env
    ```
2. Clean up logs from previous tests
    ```bash
    mvn -Plogs clean -q
    ```
3. Ensure no stale peer is running
    ```bash
    pkill -9 -f "pal-1.0.0-SNAPSHOT.jar"
    ```
4. Restart the etcd and kafka containers
    ```bash
    infra/bin/restart-containers.sh
    ```
   NOTE: If you're using a local installation of etcd and kafka, then replace the above by `start-etcd.sh && start-kafka.sh`.
5. Run the integration tests
    ```bash
    timeout 45m mvn -pl modules/itt verify
    ```
   NOTE: Tests that require a peer automatically launch and manage their own peer processes via test suites:
   - `RpcTestSuite`: Manages a peer for all RPC tests, exposing both binary and JSON rpc interfaces.
   - `InterceptFowTestSuite`: Manages an interceptable peer, with callbacks received by ThinPeer and processed within 
      the actual tests.
   - `InterceptEndToEndTestSuite`: Manages two separate peers, interceptable (where itt-apps run) and interceptor, 
      where callback handlers run.
   - `LocalInterceptTestSuite`: Manages an interceptable peer, which handles the local intercept callbacks.

   Other tests (CLI, directory) run independently without requiring a peer.

## Test Suites and Running Individual Tests

### Understanding Test Suites

**Some test classes cannot be run directly** via `-Dit.test=ClassName` because they belong to a Test Suite that manages shared resources (peer lifecycle, setup/teardown, etc.).

| Suite | Purpose |
|-------|---------|
| `RpcTestSuite` | All RPC-related integration tests |
| `InterceptFowTestSuite` | Intercept tests with ThinPeer callbacks |
| `InterceptEndToEndTestSuite` | Two-peer intercept tests |
| `LocalInterceptTestSuite` | Local intercept callback tests |

See `modules/itt/pom.xml` failsafe plugin configuration for authoritative suite membership.

### Using itt-focus.sh

When you need to run or debug a specific test class or method within a suite, use the `itt-focus.sh` utility script (available in PATH after sourcing `export-env`).

**Commands:**

```bash
# Focus on a specific class within a suite
itt-focus.sh <suite>::<class>

# Focus on a specific method within a class
itt-focus.sh <suite>::<class>#<method>

# Reset one or all suites to original state
itt-focus.sh reset [<suite>]

# Show current focus state
itt-focus.sh status

# List all classes in a suite
itt-focus.sh list <suite>
```

**Examples:**

```bash
# Focus InterceptFowTestSuite on a single test class
itt-focus.sh InterceptFowTestSuite::BeforeInterceptCallbackIT

# Focus on a specific test method
itt-focus.sh InterceptFowTestSuite::BeforeInterceptCallbackIT#testSingleArgumentMutation

# See what classes are in RpcTestSuite
itt-focus.sh list RpcTestSuite

# Check if any focus is currently active
itt-focus.sh status

# Reset everything back to normal
itt-focus.sh reset
```

**How it works:**
- **Class focus:** Comments out all other classes in `@Suite.SuiteClasses({...})`
- **Method focus:** Additionally adds `@Ignore` to all other `@Test` methods
- **Reset:** Removes all `// ITT-FOCUS:` markers and restores original state

> ⚠️ **Always run `itt-focus.sh reset` after completing work with suite-based tests** to avoid accidentally leaving modified suite files.

### Common Workflows

```bash
# Run a standalone test (not part of a suite)
mvn -pl modules/itt verify -Dit.test=MyStandaloneIT

# Run a full test suite
mvn -pl modules/itt verify -Dit.test=RpcTestSuite

# Run a single class within a suite
itt-focus.sh RpcTestSuite::SpecificRpcIT
mvn -pl modules/itt verify -Dit.test=RpcTestSuite
itt-focus.sh reset

# Run a single method within a suite
itt-focus.sh InterceptFowTestSuite::CallbackIT#testSpecificBehavior
mvn -pl modules/itt verify -Dit.test=InterceptFowTestSuite
itt-focus.sh reset
```

## Debugging Failed Tests

### Log Locations

| Log Location | Contents |
|--------------|----------|
| `modules/itt/logs/tests.log` | Logging output from the test class |
| `logs/*peer.log` | Logging output from peers launched by the test |

### Isolate Before Debugging

When multiple failures exist (especially in suite-based tests), **isolate to a single test method before debugging**:

- **Noise reduction:** Multiple failing tests produce overwhelming, interleaved output
- **Cleanup interference:** Earlier tests may leave state that causes cascading failures
- **False positives:** A test may fail due to a prior test's side effects, not its own bug

**Debugging workflow for suite-based tests:**

1. **Discover:** Run class-focused to find all failing methods
2. **Isolate:** Focus on the first failing method with `itt-focus.sh <Suite>::<Class>#<method>`
3. **Clean:** Kill peers, clean logs, restart containers
4. **Debug:** Analyze logs for this ONE method
5. **Fix:** Apply minimal fix and re-run
6. **Reset:** Run `itt-focus.sh reset`
7. **Repeat:** Go to step 2 for the next failing method

### Environment Reset

Full cleanup before a test run:
```bash
pkill -9 -f "pal-1.0.0-SNAPSHOT.jar"
mvn clean -Plogs
infra/bin/restart-containers.sh
```

## Adding new tests
New integration tests might or not require a peer to be running. If it does, then:
- Consider adding the new test to an existing TestSuite that already launches and manages a shared peer,
  listing the new class name under `@RunWith(Suite.class) @Suite.SuiteClasses({`.
- Or create and manage the required peer using JUnit's `@BeforeClass` and `@AfterClass` to launch and
  stop the peer. For an example, see [ThinPeerIT.java](src/test/java/io/quasient/pal/cxn/ThinPeerIT.java).
- Verify that the failsafe plugin configuration in [ITT's pom](pom.xml) is up to date, so that the new
  test runs either as standalone or within a test suite.
