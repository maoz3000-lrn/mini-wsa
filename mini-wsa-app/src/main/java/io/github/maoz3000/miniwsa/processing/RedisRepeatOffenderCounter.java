package io.github.maoz3000.miniwsa.processing;

import io.github.maoz3000.miniwsa.config.ConditionalOnRole;
import io.github.maoz3000.miniwsa.config.Role;
import io.github.maoz3000.miniwsa.domain.DlrEvent;
import io.github.maoz3000.miniwsa.domain.error.TransientStorageException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Per-client-IP sliding window of recent events in Redis (sorted set: member = eventId, score = event time).
 * Each event is added and counted by one atomic Lua script; all scripts of a batch go in one pipeline (1 RTT).
 * Re-processing an event is a no-op because the eventId is already a member.
 */
@Component
@ConditionalOnRole(Role.PROCESSING)
public class RedisRepeatOffenderCounter implements RepeatOffenderCounter {

    private static final String SCRIPT_LOCATION = "redis/repeat-offender-window.lua";

    private final StringRedisTemplate redis;
    private final RepeatOffenderProperties properties;
    private final byte[] script;

    public RedisRepeatOffenderCounter(StringRedisTemplate redis, RepeatOffenderProperties properties) {
        this.redis = redis;
        this.properties = properties;
        this.script = loadScript();
    }

    @Override
    public List<Long> recordAndCountPrior(List<DlrEvent> events) {
        try {
            return redis.executePipelined((RedisCallback<Object>) connection -> {
                events.forEach(event -> evaluate(connection, event));
                return null;
            }).stream().map(Long.class::cast).toList();
        } catch (DataAccessException e) {
            throw new TransientStorageException("Redis repeat-offender window unavailable", e);
        }
    }

    private void evaluate(RedisConnection connection, DlrEvent event) {
        connection.scriptingCommands().eval(script, ReturnType.INTEGER, 1,
                bytes(properties.keyPrefix() + event.clientIp()),
                bytes(String.valueOf(event.timestamp().toEpochMilli())),
                bytes(event.eventId()),
                bytes(String.valueOf(properties.window().toMillis())),
                bytes(String.valueOf(properties.maxEntries())),
                bytes(String.valueOf(properties.keyTtl().toMillis())));
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] loadScript() {
        try (var in = new ClassPathResource(SCRIPT_LOCATION).getInputStream()) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot load " + SCRIPT_LOCATION, e);
        }
    }
}
