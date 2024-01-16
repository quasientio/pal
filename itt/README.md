# Running PAL's integration tests

PAL's integration tests depend on a running instance or cluster of __etcd__ and __kafka__. For convenience, we provide
docker images and launch scripts for both, so all you need is __Docker installed and running__. Follow the
instructions in the next section to download our docker images for the corresponding binaries.

If you have etcd and/or kafka installed on your machine or an accessible host, then skip to
[Running the tests](#Run the tests). If either `etcd` or `kafka` are not listening on localhost or their standard
ports (2379 and 9092 respectively), then adjust the corresponding variables in the `export_env` file.

__NOTE__: if you already added `$PAL_HOME/bin` to your PATH variable, you may omit `bin/` from the instructions following.

## Download Etcd and Kafka docker images
Pull and tag the provided images for etcd and kafka from the gitlab.com registry:
```bash
docker pull registry.gitlab.com/cometera/pal/etcd:latest && docker tag registry.gitlab.com/cometera/pal/etcd etcd:latest
docker pull registry.gitlab.com/cometera/pal/kafka:latest && docker tag registry.gitlab.com/cometera/pal/kafka:latest kafka:latest
```
_These images are based on alpine and are relatively small. You may, of course, use your own or any other public
images, in which case you may need to modify the parameters or variables in the scripts under bin/._

## Run the tests
1. Open a terminal and export the ENV variables required to run the tests
    ```bash
    source export_env
    ```
2. Start the etcd and kafka containers
    ```bash
    bin/dstart_etcd_and_kafka
    ```
   If you're using a local installation of etcd and kafka you may want to adapt and use the `start_etcd` and
`start_kafka` scripts found under the bin/ folder.
3. Launch a peer loading the classes in itt-apps
    ```bash
   bin/peer4itts
   
   # Or edit and run the command yourself (note that --rpc may be any available port number)
   pal run \
   --dir $PAL_DIRECTORY \
   --name peer-for-itt \
   --rpc 5656 \
   --rpc-threads 3 \
   --log auto \
   --kafka-servers $KAFKA_SERVERS \
   --interceptable \
   --classpath $PAL_HOME/itt-apps/target/itt-apps-1.0.0-SNAPSHOT.jar
    ```
4. In a new terminal, export the ENV variables and run the integration tests
    ```bash
    source export_env
    cd itt; mvn verify
    ```
5. Stop the running peer (launched with `bin/peer4itts`) typing Ctrl-C
6. Stop the etcd and kafka containers.
    ```bash
    bin/dstop_etcd_and_kafka
    ```
