package de.illunium.custom.leaderboard;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Pure immutable numbers, safe to sort on a worker; exclusions use the current OP list at display time. */
public record RankingSnapshot(List<Value> sorted) {
    public record Value(UUID player, String name, long value) { }
    public record Row(UUID player, String name, long value, int rank) { }

    public RankingSnapshot {
        sorted = sorted.stream().filter(v -> v.value() > 0).sorted(Comparator
                .comparingLong(Value::value).reversed()
                .thenComparing(Value::name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(v -> v.player().toString())).toList();
    }

    public List<Row> excluding(Set<UUID> excluded) {
        List<Row> rows = new ArrayList<>();
        long previous = -1;
        int rank = 0;
        for (Value value : sorted) {
            if (excluded.contains(value.player())) continue;
            if (previous != value.value()) rank = rows.size() + 1;
            rows.add(new Row(value.player(), value.name(), value.value(), rank));
            previous = value.value();
        }
        return List.copyOf(rows);
    }
}
