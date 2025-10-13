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
3. Launch a peer loading the classes in itt-apps
    ```bash
   bin/peer4itts.sh
    ```
4. In a new terminal, export the ENV variables and run the integration tests
    ```bash
    source export_env
    mvn -pl modules/itt verify
    ```
5. Stop the running peer (launched via peer4itts.sh) typing Ctrl-C
6. Stop the etcd and kafka containers.
    ```bash
    infra/bin/stop_etcd_and_kafka_docker.sh
    ```
