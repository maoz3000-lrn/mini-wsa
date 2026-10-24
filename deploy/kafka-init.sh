#!/bin/sh
# Creates and configures the Kafka topics; run by the one-shot kafka-init compose service on every `up`.
# Idempotent: creates missing topics, re-applies their configs and raises partition counts. Kafka cannot lower a
# partition count, so a lower configured value only produces a warning.
set -eu

BIN=/opt/kafka/bin
BOOTSTRAP="${KAFKA_BOOTSTRAP:-kafka:29092}"

# ensure_topic <name> <partitions> <retention.ms> <retention.bytes>
ensure_topic() {
  name=$1 partitions=$2 retention_ms=$3 retention_bytes=$4
  configs="cleanup.policy=delete,retention.ms=${retention_ms},retention.bytes=${retention_bytes}"

  "$BIN/kafka-topics.sh" --bootstrap-server "$BOOTSTRAP" --create --if-not-exists --topic "$name" \
    --partitions "$partitions" --replication-factor 1 \
    --config cleanup.policy=delete --config "retention.ms=${retention_ms}" --config "retention.bytes=${retention_bytes}"

  "$BIN/kafka-configs.sh" --bootstrap-server "$BOOTSTRAP" --alter --entity-type topics --entity-name "$name" \
    --add-config "$configs"

  current=$("$BIN/kafka-topics.sh" --bootstrap-server "$BOOTSTRAP" --describe --topic "$name" \
    | sed -n 's/.*PartitionCount: *\([0-9]*\).*/\1/p' | head -n 1)
  if [ "$current" -lt "$partitions" ]; then
    "$BIN/kafka-topics.sh" --bootstrap-server "$BOOTSTRAP" --alter --topic "$name" --partitions "$partitions"
  elif [ "$current" -gt "$partitions" ]; then
    echo "WARN: $name has $current partitions, configured $partitions; Kafka cannot decrease partitions" \
      "(recreate the topic, e.g. docker compose down -v, to apply)"
  fi

  "$BIN/kafka-topics.sh" --bootstrap-server "$BOOTSTRAP" --describe --topic "$name" | head -n 1
}

ensure_topic dlr-raw "$DLR_TOPIC_PARTITIONS" "$DLR_TOPIC_RETENTION_MS" "$DLR_TOPIC_RETENTION_BYTES"
ensure_topic dlr-raw.DLT "$DLT_TOPIC_PARTITIONS" "$DLT_TOPIC_RETENTION_MS" -1
