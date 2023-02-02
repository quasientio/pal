# Instructions to set up Kafka w/ Kraft before 1st run (https://kafka.apache.org/documentation/#quickstart)

# Generate a Cluster UUID
KAFKA_CLUSTER_ID="$($KAFKA_HOME/bin/kafka-storage.sh random-uuid)"

# Format Log Directories
$KAFKA_HOME/bin/kafka-storage.sh format -t $KAFKA_CLUSTER_ID -c $PAL_HOME/config/kafka/server.properties
