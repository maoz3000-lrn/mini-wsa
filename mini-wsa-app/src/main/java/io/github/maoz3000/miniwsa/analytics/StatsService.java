package io.github.maoz3000.miniwsa.analytics;

import io.github.maoz3000.miniwsa.api.StatsSummaryResponse;
import io.github.maoz3000.miniwsa.api.StatsSummaryResponse.CategorySummary;
import io.github.maoz3000.miniwsa.api.StatsSummaryResponse.TimeRange;
import io.github.maoz3000.miniwsa.api.StatsSummaryResponse.TopAttacker;
import io.github.maoz3000.miniwsa.api.StatsSummaryResponse.TopPath;
import io.github.maoz3000.miniwsa.config.ConditionalOnRole;
import io.github.maoz3000.miniwsa.config.Role;
import io.github.maoz3000.miniwsa.domain.Action;
import io.github.maoz3000.miniwsa.domain.RuleCategory;
import io.github.maoz3000.miniwsa.domain.query.AttackerStats;
import io.github.maoz3000.miniwsa.domain.query.CategoryStats;
import io.github.maoz3000.miniwsa.domain.query.EventFilter;
import io.github.maoz3000.miniwsa.domain.query.PathStats;
import io.github.maoz3000.miniwsa.domain.query.SummaryCounts;
import io.github.maoz3000.miniwsa.storage.EventAnalyticsReader;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Builds the summary from three independent reads executed in parallel on virtual threads.
 */
@Service
@ConditionalOnRole(Role.ANALYTICS)
public class StatsService {

    /** Size of the top attackers / top targeted paths lists (assignment spec). */
    public static final int TOP_N = 10;

    private final EventAnalyticsReader reader;

    public StatsService(EventAnalyticsReader reader) {
        this.reader = reader;
    }

    public StatsSummaryResponse summary(EventFilter filter) {
        return VirtualThreads.fanOut(executor -> {
            var counts = CompletableFuture.supplyAsync(() -> reader.summaryCounts(filter), executor);
            var attackers = CompletableFuture.supplyAsync(() -> reader.topAttackers(filter, TOP_N), executor);
            var paths = CompletableFuture.supplyAsync(() -> reader.topTargetedPaths(filter, TOP_N), executor);
            return toResponse(filter, counts.join(), attackers.join(), paths.join());
        });
    }

    private static StatsSummaryResponse toResponse(EventFilter filter, SummaryCounts counts,
                                                   List<AttackerStats> attackers, List<PathStats> paths) {
        return new StatsSummaryResponse(
                filter.configId(),
                new TimeRange(filter.from(), filter.to()),
                counts.total(),
                byCategory(counts.byCategory()),
                byAction(counts.byAction()),
                attackers.stream()
                        .map(a -> new TopAttacker(a.clientIp(), a.count(), Rounding.oneDecimal(a.avgThreatScore())))
                        .toList(),
                paths.stream().map(p -> new TopPath(p.path(), p.count())).toList());
    }

    private static Map<RuleCategory, CategorySummary> byCategory(List<CategoryStats> stats) {
        Map<RuleCategory, CategoryStats> found = stats.stream()
                .collect(Collectors.toMap(CategoryStats::category, Function.identity()));
        return Arrays.stream(RuleCategory.values()).collect(Collectors.toMap(Function.identity(),
                c -> found.containsKey(c)
                        ? new CategorySummary(found.get(c).count(), Rounding.oneDecimal(found.get(c).avgThreatScore()))
                        : new CategorySummary(0, 0.0),
                (a, b) -> a, () -> new EnumMap<>(RuleCategory.class)));
    }

    private static Map<Action, Long> byAction(Map<Action, Long> counts) {
        return Arrays.stream(Action.values()).collect(Collectors.toMap(Function.identity(),
                a -> counts.getOrDefault(a, 0L), (a, b) -> a, () -> new EnumMap<>(Action.class)));
    }
}
