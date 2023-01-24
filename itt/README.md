### How to run the Integration Tests

#### Download Zookeeper and Kafka docker images
The following instructions assume you have Docker installed and running.
The first time you run `start_zk_and_kafka`, the docker images for Zookeeper and Kafka will be downloaded.
You may want to download the images prior to running the tests:
```bash
docker pull wurstmeister/zookeeper:latest
docker pull wurstmeister/kafka:2.12-2.3.0
```
(You may be running locally installed ZK and Kafka binaries, in which case the `start_zk_and_kafka` and
`stop_zk_and_kafka` commands in the below instructions do not apply).

#### Running the tests
1. Open a terminal, start Zookeeper and Kafka
    ```bash
    start_zk_and_kafka &> /dev/null &
    ```
2. Launch a peer loading classes the in itt-apps (**tcp-req** may be any available port number)
    ```bash
    pal run \
    --dir localhost:2181 \
    --tcp-req 5656 \
    --log auto \
    --interceptable \
    --tcp-req-threads 3 \
    --classpath itt-apps/target/itt-apps-1.0.0-SNAPSHOT.jar
    ```
3. Open a second terminal and run mvn verify from the itt subdirectory
    ```bash
    cd itt; PAL_DIRECTORY=localhost:2181 mvn verify
    ```
4. Stop zookeeper and kafka
    ```bash
    stop_zk_and_kafka
    ```
5. Stop the running peer with ctrl^c