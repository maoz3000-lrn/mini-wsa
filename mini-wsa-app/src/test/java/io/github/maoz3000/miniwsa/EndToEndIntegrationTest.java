package io.github.maoz3000.miniwsa;

import io.github.maoz3000.miniwsa.api.DlrEventResponse;
import io.github.maoz3000.miniwsa.api.SampleEventResponse;
import io.github.maoz3000.miniwsa.api.SamplesPage;
import io.github.maoz3000.miniwsa.api.StatsSummaryResponse;
import io.github.maoz3000.miniwsa.domain.Action;
import io.github.maoz3000.miniwsa.domain.DlrEvent;
import io.github.maoz3000.miniwsa.domain.RuleCategory;
import io.github.maoz3000.miniwsa.domain.RuleSeverity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static io.github.maoz3000.miniwsa.fixtures.DlrEventFixtures.validEvent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The whole pipeline through its public API: POST /v1/events/ingest, then the stats and samples endpoints,
 * with real Kafka, Redis and ClickHouse.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(ContainersConfig.class)
class EndToEndIntegrationTest {

    private static final Pattern NEXT_LINK = Pattern.compile("<([^>]+)>; rel=\"next\"");
    private static final Instant T0 = Instant.parse("2026-05-20T10:00:00Z");

    @Autowired
    private RestTestClient client;

    @Test
    void ingestedEventsAreEnrichedAndQueryable() {
        long configId = uniqueConfigId();
        List<DlrEvent> events = Stream.concat(
                        IntStream.range(0, 12).mapToObj(i -> event(configId, "inj-" + i, RuleCategory.INJECTION,
                                RuleSeverity.CRITICAL, Action.DENY, "/api/v1/login", T0.plusSeconds(i))),
                        IntStream.range(0, 5).mapToObj(i -> event(configId, "bot-" + i, RuleCategory.BOT,
                                RuleSeverity.LOW, Action.MONITOR, "/products", T0.plusSeconds(100 + i))))
                .toList();

        client.post().uri("/v1/events/ingest").contentType(MediaType.APPLICATION_JSON).body(events)
                .exchange().expectStatus().isCreated();

        await().atMost(Duration.ofSeconds(60)).until(() -> summary(configId).totalEvents() == 17);
        StatsSummaryResponse summary = summary(configId);
        assertThat(summary.byCategory().get(RuleCategory.INJECTION).count()).isEqualTo(12);
        assertThat(summary.byCategory().get(RuleCategory.BOT))
                .isEqualTo(new StatsSummaryResponse.CategorySummary(5, 10.0));
        assertThat(summary.byAction()).containsEntry(Action.DENY, 12L).containsEntry(Action.MONITOR, 5L)
                .containsEntry(Action.ALERT, 0L);
        assertThat(summary.topTargetedPaths()).containsExactly(
                new StatsSummaryResponse.TopPath("/api/v1/login", 12), new StatsSummaryResponse.TopPath("/products", 5));

        SamplesPage bots = samples("/v1/events/samples?configId=" + configId + "&category=BOT").page();
        assertThat(bots.total()).isEqualTo(5);
        assertThat(bots.events()).extracting(e -> e.event().eventId().substring(e.event().eventId().indexOf('-') + 1))
                .containsExactly("bot-4", "bot-3", "bot-2", "bot-1", "bot-0");
        assertThat(bots.events()).allSatisfy(e -> {
            assertThat(e.attackType()).isEqualTo("Bot Activity");
            assertThat(e.threatScore()).isEqualTo(10);
            assertThat(e.receivedAt()).isAfter(e.event().timestamp());
        });
    }

    @Test
    void followingNextLinksVisitsEveryEventOnceEvenWithTiedTimestamps() {
        long configId = uniqueConfigId();
        // 9 events, 5 of them sharing one millisecond, paged 4 at a time
        List<DlrEvent> events = IntStream.range(0, 9)
                .mapToObj(i -> event(configId, "page-" + i, RuleCategory.XSS, RuleSeverity.HIGH, Action.ALERT,
                        "/search", i < 5 ? T0 : T0.plusSeconds(i)))
                .toList();
        client.post().uri("/v1/events/ingest").contentType(MediaType.APPLICATION_JSON).body(events)
                .exchange().expectStatus().isCreated();
        await().atMost(Duration.ofSeconds(60)).until(() -> summary(configId).totalEvents() == 9);

        List<String> visited = new ArrayList<>();
        Optional<String> next = Optional.of("/v1/events/samples?configId=" + configId + "&limit=4");
        while (next.isPresent()) {
            Page page = samples(next.get());
            page.page().events().stream().map(SampleEventResponse::event).map(DlrEventResponse::eventId).forEach(visited::add);
            next = page.next();
        }

        assertThat(visited).hasSize(9).doesNotHaveDuplicates();
    }

    @Test
    void rejectsInvalidBatchesWithoutStoringAnything() {
        long configId = uniqueConfigId();
        DlrEvent valid = event(configId, "valid", RuleCategory.DOS, RuleSeverity.HIGH, Action.DENY, "/", T0);
        DlrEvent invalid = valid.toBuilder().eventId("invalid").clientIp("not-an-ip").build();

        client.post().uri("/v1/events/ingest").contentType(MediaType.APPLICATION_JSON).body(List.of(valid, invalid))
                .exchange().expectStatus().isBadRequest()
                .expectBody().jsonPath("$.errors[0].field").isEqualTo("clientIp");
        client.post().uri("/v1/events/ingest").contentType(MediaType.APPLICATION_JSON)
                .body(IntStream.range(0, 1001).mapToObj(i -> valid).toList())
                .exchange().expectStatus().isEqualTo(413);

        assertThat(summary(configId).totalEvents()).isZero();
    }

    private StatsSummaryResponse summary(long configId) {
        return client.get().uri("/v1/stats/summary?configId=" + configId).exchange()
                .expectStatus().isOk()
                .expectBody(StatsSummaryResponse.class).returnResult().getResponseBody();
    }

    private Page samples(String uri) {
        EntityExchangeResult<SamplesPage> result = client.get().uri(URI.create(uri).getRawPath() + "?"
                        + URI.create(uri).getRawQuery()).exchange()
                .expectStatus().isOk()
                .expectBody(SamplesPage.class).returnResult();
        Optional<String> next = Optional.ofNullable(result.getResponseHeaders().getFirst(HttpHeaders.LINK))
                .map(NEXT_LINK::matcher).filter(Matcher::matches).map(m -> URI.create(m.group(1)))
                .map(u -> u.getRawPath() + "?" + u.getRawQuery());
        return new Page(result.getResponseBody(), next);
    }

    private static DlrEvent event(long configId, String id, RuleCategory category, RuleSeverity severity,
                                  Action action, String path, Instant timestamp) {
        DlrEvent base = validEvent();
        return base.toBuilder()
                .eventId(configId + "-" + id).configId(configId).timestamp(timestamp).path(path).action(action)
                .clientIp("198.18.0." + ThreadLocalRandom.current().nextInt(1, 255))
                .rule(base.rule().toBuilder().category(category).severity(severity).build())
                .build();
    }

    private static long uniqueConfigId() {
        return ThreadLocalRandom.current().nextLong(1_000_000, 9_000_000);
    }

    private record Page(SamplesPage page, Optional<String> next) {
    }
}
