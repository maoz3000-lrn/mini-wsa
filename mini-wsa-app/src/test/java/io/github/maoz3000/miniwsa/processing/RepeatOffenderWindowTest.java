package io.github.maoz3000.miniwsa.processing;

import io.github.maoz3000.miniwsa.ContainersConfig;
import io.github.maoz3000.miniwsa.domain.DlrEvent;
import io.github.maoz3000.miniwsa.domain.error.TransientStorageException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

import static io.github.maoz3000.miniwsa.fixtures.DlrEventFixtures.EVENT_TIME;
import static io.github.maoz3000.miniwsa.fixtures.DlrEventFixtures.validEvent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class RepeatOffenderWindowTest {

    private static final RepeatOffenderProperties PROPERTIES =
            new RepeatOffenderProperties(Duration.ofMinutes(10), 6, 6, Duration.ofMinutes(5), "ro:");

    @Container
    private static final GenericContainer<?> REDIS = ContainersConfig.redisContainer();

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redis;

    private RepeatOffenderWindow window;

    @BeforeAll
    static void connect() {
        connectionFactory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)));
        connectionFactory.afterPropertiesSet();
        redis = new StringRedisTemplate(connectionFactory);
    }

    @AfterAll
    static void disconnect() {
        connectionFactory.destroy();
    }

    @BeforeEach
    void setUp() {
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();
        window = new RepeatOffenderWindow(redis, PROPERTIES);
    }

    @Test
    void countsEarlierEventsOfSameIpWithinBatch() {
        List<DlrEvent> burst = IntStream.range(0, 8).mapToObj(i -> event("e" + i, "1.1.1.1", EVENT_TIME.plusSeconds(i))).toList();

        assertThat(window.recordAndCountPrior(burst)).containsExactly(0L, 1L, 2L, 3L, 4L, 5L, 6L, 6L);
    }

    @Test
    void seesEventsRecordedByEarlierBatches() {
        window.recordAndCountPrior(IntStream.range(0, 6)
                .mapToObj(i -> event("e" + i, "1.1.1.1", EVENT_TIME.plusSeconds(i))).toList());

        assertThat(window.recordAndCountPrior(List.of(event("e6", "1.1.1.1", EVENT_TIME.plusSeconds(6)))))
                .containsExactly(6L);
    }

    @Test
    void keepsIpsIndependent() {
        assertThat(window.recordAndCountPrior(List.of(
                event("a1", "1.1.1.1", EVENT_TIME),
                event("b1", "2.2.2.2", EVENT_TIME.plusSeconds(1)),
                event("a2", "1.1.1.1", EVENT_TIME.plusSeconds(2)))))
                .containsExactly(0L, 0L, 1L);
    }

    @Test
    void ignoresEventsOlderThanWindow() {
        assertThat(window.recordAndCountPrior(List.of(
                event("old", "1.1.1.1", EVENT_TIME),
                event("edge", "1.1.1.1", EVENT_TIME.plus(Duration.ofMinutes(10))),
                event("late", "1.1.1.1", EVENT_TIME.plus(Duration.ofMinutes(10)).plusMillis(1)))))
                .containsExactly(0L, 1L, 1L);
    }

    @Test
    void neverCountsLaterEventsForAnEarlierOne() {
        window.recordAndCountPrior(List.of(event("later", "1.1.1.1", EVENT_TIME.plusSeconds(10))));

        assertThat(window.recordAndCountPrior(List.of(event("earlier", "1.1.1.1", EVENT_TIME))))
                .containsExactly(0L);
    }

    @Test
    void reprocessingSameEventIsIdempotent() {
        List<DlrEvent> batch = IntStream.range(0, 3).mapToObj(i -> event("e" + i, "1.1.1.1", EVENT_TIME.plusSeconds(i))).toList();

        window.recordAndCountPrior(batch);

        assertThat(window.recordAndCountPrior(batch)).containsExactly(0L, 1L, 2L);
    }

    @Test
    void keepsOnlyNewestEntriesAndExpiresKey() {
        window.recordAndCountPrior(IntStream.range(0, 20)
                .mapToObj(i -> event("e" + i, "1.1.1.1", EVENT_TIME.plusSeconds(i))).toList());

        assertThat(redis.opsForZSet().range("ro:1.1.1.1", 0, -1))
                .containsExactly("e14", "e15", "e16", "e17", "e18", "e19");
        assertThat(redis.getExpire("ro:1.1.1.1")).isBetween(14 * 60L, 15 * 60L);
    }

    @Test
    void translatesRedisOutageToTransientFailure() {
        LettuceConnectionFactory unreachable = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration("localhost", 1));
        unreachable.afterPropertiesSet();
        RepeatOffenderWindow broken = new RepeatOffenderWindow(new StringRedisTemplate(unreachable), PROPERTIES);

        assertThatThrownBy(() -> broken.recordAndCountPrior(List.of(validEvent())))
                .isInstanceOf(TransientStorageException.class);
        unreachable.destroy();
    }

    private static DlrEvent event(String eventId, String clientIp, Instant timestamp) {
        return validEvent().toBuilder().eventId(eventId).clientIp(clientIp).timestamp(timestamp).build();
    }
}
