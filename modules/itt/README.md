# Running PAL's integration tests

PAL's integration tests depend on a running instance or cluster of __etcd__ and __kafka__. For convenience, we provide
docker images and launch scripts for both, so all you need is __Docker installed and running__. Follow the
instructions in the next section to download our docker images for the corresponding binaries.

If you have etcd and/or kafka installed on your machine or an accessible host, then skip to
[Running the tests](#Run the tests). If either `etcd` or `kafka` are not listening on localhost or their standard
ports (2379 and 29092 respectively), then adjust the corresponding variables in the `export_env` file.

__NOTE__: if you already added `$PAL_HOME/bin` to your PATH variable, you may omit `bin/` from the instructions following.

## Download Etcd and Kafka docker images
Pull and tag the provided images for etcd and kafka:
```bash
docker pull gitlab.cometera.org:5050/quasient/pal/etcd:latest && docker tag gitlab.cometera.org:5050/quasient/pal/etcd:latest etcd:latest
docker pull apache/kafka:3.8.0 && docker tag apache/kafka:3.8.0 kafka:latest
```

## Run the tests
1. Open a terminal and export the ENV variables required to run the tests
    ```bash
    source export_env
    ```
2. Start the etcd and kafka containers
    ```bash
    infra/bin/start_etcd_and_kafka_docker.sh
    ```
   NOTE: If you're using a local installation of etcd and kafka, then replace the above by `start_etcd.sh && start_kafka.sh` (scripts found under the bin/ folder).
3. Run the integration tests
    ```bash
    mvn -pl modules/itt verify
    ```
   NOTE: Tests that require a peer automatically launch and manage their own peer processes via test suites:
   - `RpcTestSuite`: Manages a peer for all RPC tests (binary and JSON) on ports 5656/7789
   - `InterceptTestSuite`: Manages a separate peer with `--interceptable` flag for intercept tests on ports 5657/7790
   - `ThinPeerTestSuite`: Manages a peer for ThinPeer connection tests on ports 5658/7791

   Other tests (CLI, directory) run independently without requiring a peer.

4. Stop the etcd and kafka containers.
    ```bash
    infra/bin/stop_etcd_and_kafka_docker.sh
    ```
## Adding new tests
New integration tests might or not require a peer to be running. If it does, then:
- Consider adding the new test to an existing TestSuite that already launches and manages a shared peer,
  listing the new class name under `@RunWith(Suite.class) @Suite.SuiteClasses({`.
- Or create and manage the required peer using JUnit's `@BeforeClass` and `@AfterClass` to launch and
  stop the peer. For an example, see [ThinPeerIT.java](src/test/java/com/quasient/pal/cxn/ThinPeerIT.java).
- Verify that the failsafe plugin configuration in [ITT's pom](pom.xml) is up to date, so that the new
  test runs either as standalone or within a test suite.
