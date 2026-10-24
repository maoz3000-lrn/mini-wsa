package io.github.maoz3000.miniwsa.generator;

import io.github.maoz3000.miniwsa.generator.AttackCatalog.Site;
import io.github.maoz3000.miniwsa.generator.GeneratedEvent.GeoLocation;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.SplittableRandom;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Mixes background noise with attack waves: bursts from a single IP hammering a single path with one attack
 * category, a few hundred milliseconds to a few seconds apart. Waves are what trigger the repeat-offender bonus.
 */
public final class TrafficGenerator {

    private static final long MIN_GAP_MILLIS = 200;
    private static final long MAX_GAP_MILLIS = 3_000;

    private TrafficGenerator() {
    }

    /** @return exactly {@code plan.count()} events, sorted by timestamp */
    public static List<GeneratedEvent> generate(TrafficPlan plan) {
        SplittableRandom random = new SplittableRandom(plan.seed());
        EventFactory factory = new EventFactory(random);
        int waveEvents = (int) Math.round(plan.count() * plan.waveRatio());
        return Stream.concat(
                        waves(plan, factory, random, waveEvents).stream(),
                        IntStream.range(0, plan.count() - waveEvents)
                                .mapToObj(i -> factory.background(randomInstant(plan.start(), plan.duration(), random))))
                .sorted(Comparator.comparing(GeneratedEvent::timestamp))
                .toList();
    }

    private static List<GeneratedEvent> waves(TrafficPlan plan, EventFactory factory, SplittableRandom random, int total) {
        List<GeneratedEvent> events = new ArrayList<>(total);
        while (events.size() < total) {
            int size = Math.min(random.nextInt(plan.waveSizeMin(), plan.waveSizeMax() + 1), total - events.size());
            events.addAll(wave(plan, factory, random, size));
        }
        return events;
    }

    private static List<GeneratedEvent> wave(TrafficPlan plan, EventFactory factory, SplittableRandom random, int size) {
        List<Long> offsets = Stream.iterate(0L, previous -> previous + random.nextLong(MIN_GAP_MILLIS, MAX_GAP_MILLIS + 1))
                .limit(size)
                .toList();
        Duration span = Duration.ofMillis(offsets.getLast());
        // keep the whole wave inside the plan's time range (span may exceed it for tiny ranges: then start at 0)
        Duration room = plan.duration().minus(span);
        Instant waveStart = room.isNegative() ? plan.start() : randomInstant(plan.start(), room, random);

        String category = AttackCatalog.WAVE_CATEGORIES.pick(random);
        Site site = factory.pick(AttackCatalog.SITES);
        String ip = factory.randomIp();
        String path = factory.pick(AttackCatalog.PROFILES.get(category).paths());
        String agent = factory.pick(AttackCatalog.TOOL_AGENTS);
        GeoLocation geo = factory.pick(AttackCatalog.GEO);

        return offsets.stream()
                .map(offset -> min(waveStart.plusMillis(offset), plan.end()))
                .map(t -> factory.waveEvent(t, site, ip, category, path, agent, geo))
                .toList();
    }

    private static Instant randomInstant(Instant start, Duration range, SplittableRandom random) {
        return start.plusMillis(random.nextLong(range.toMillis() + 1));
    }

    private static Instant min(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }
}
