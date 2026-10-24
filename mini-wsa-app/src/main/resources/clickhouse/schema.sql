-- Enriched security events. One row per DLR.
--  * ReplacingMergeTree(received_at): rows with the same sorting key (re-deliveries, client retries) collapse
--    into one on merge; queries use FINAL when exact results are required.
--  * PARTITION BY day of event time: time-range filters prune whole partitions, and duplicates of an event
--    always land in the same partition, so they can merge.
--  * ORDER BY config_id first: single-configuration queries read only that configuration's rows;
--    event_id last makes the key unique per event (needed for deduplication).
CREATE TABLE IF NOT EXISTS dlr_events
(
    event_id      String,
    timestamp     DateTime64(3, 'UTC'),
    received_at   DateTime64(3, 'UTC'),
    config_id     UInt64,
    policy_id     LowCardinality(String),
    client_ip     String,
    hostname      LowCardinality(String),
    path          String,
    method        LowCardinality(String),
    status_code   UInt16,
    user_agent    Nullable(String),
    rule_id       LowCardinality(String),
    rule_name     LowCardinality(String),
    rule_message  Nullable(String),
    rule_severity Enum8('CRITICAL' = 1, 'HIGH' = 2, 'MEDIUM' = 3, 'LOW' = 4),
    rule_category Enum8('INJECTION' = 1, 'XSS' = 2, 'PROTOCOL_VIOLATION' = 3, 'DATA_LEAKAGE' = 4,
                        'BOT' = 5, 'DOS' = 6, 'RATE_LIMIT' = 7),
    action        Enum8('DENY' = 1, 'ALERT' = 2, 'MONITOR' = 3),
    geo_country   LowCardinality(Nullable(String)),
    geo_city      LowCardinality(Nullable(String)),
    request_size  Nullable(UInt64),
    response_size Nullable(UInt64),
    attack_type   LowCardinality(String),
    threat_score  UInt8
)
ENGINE = ReplacingMergeTree(received_at)
PARTITION BY toYYYYMMDD(timestamp)
ORDER BY (config_id, timestamp, event_id);
