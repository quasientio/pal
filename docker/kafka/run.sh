#!/bin/bash

usage() {
  echo "Usage: format|start|format_start --config/-c server.properties" 1>&2; exit 1;
}

while :
do
  case $1 in
    -c | --config)
      CONFIG_FILE="$2"
      shift 2
      ;;
    -h | --help)
      print_help
      exit 0
      ;;
    format)
      COMMAND="format"
      shift 1
      ;;
    start)
      COMMAND="start"
      shift 1
      ;; 
    format_start)
      COMMAND="format_start"
      shift 1
      ;;
    *)
      break
      ;;
  esac
done

if [ -z $COMMAND ] || [ -z $CONFIG_FILE ]; then
  usage
  exit 1
fi

case $COMMAND in
  format)
    KAFKA_CLUSTER_ID="$(bin/kafka-storage.sh random-uuid)"
    bin/kafka-storage.sh format -t $KAFKA_CLUSTER_ID -c $CONFIG_FILE
    exit 0
    ;;
  start)
    bin/kafka-server-start.sh $CONFIG_FILE
    exit 0
    ;;
  format_start)
    KAFKA_CLUSTER_ID="$(bin/kafka-storage.sh random-uuid)"
    bin/kafka-storage.sh format -t $KAFKA_CLUSTER_ID -c $CONFIG_FILE
    echo "kafka storage formatted with KAFKA_CLUSTER_ID=$KAFKA_CLUSTER_ID"
    bin/kafka-server-start.sh $CONFIG_FILE
    exit 0
    ;;
esac
