package dev.kairo.components.list;

import dev.kairo.core.StableKey;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

final class ListEngineTest {
    record Row(int id, String label) {}

    @Test
    void diffsTenThousandItemsWindowedViewportAndDetectsDuplicateKeys() {
        List<Row> rows = new ArrayList<>(10_000);
        for (int i = 0; i < 10_000; i++) {
            rows.add(new Row(i, "Linha " + i));
        }

        // Virtualized window at scroll Y = 2800 (item 50 * 56dp), viewport 560dp (10 items), overscan 3
        ListWindow.WindowRange window = ListWindow.compute(rows.size(), 56.0, 2800.0, 560.0, 3);
        assertThat(window.startIndex()).isEqualTo(47);
        assertThat(window.endIndex()).isEqualTo(62);
        assertThat(window.visibleCount()).isEqualTo(16);
        assertThat(window.topSpacerHeight()).isEqualTo(47 * 56.0);
        assertThat(window.totalContentHeight()).isEqualTo(10_000 * 56.0);
        assertThat(window.slice(rows)).hasSize(16);

        // Incremental Move/Insert/Remove diff
        KairoListDiff diff = new KairoListDiff();
        List<Row> a = List.of(new Row(1, "A"), new Row(2, "B"), new Row(3, "C"));
        List<Row> b = List.of(new Row(3, "C"), new Row(1, "A+"), new Row(4, "D"));
        List<KairoListDiff.Op<Row>> ops = diff.diff(a, b, r -> StableKey.of(r.id()));

        assertThat(ops).anyMatch(op -> op instanceof KairoListDiff.Op.Remove<?> rm && rm.key().equals(StableKey.of(2)));
        assertThat(ops).anyMatch(op -> op instanceof KairoListDiff.Op.Move<?> mv && mv.key().equals(StableKey.of(3)) && mv.toIndex() == 0);
        assertThat(ops).anyMatch(op -> op instanceof KairoListDiff.Op.Update<?> up && up.key().equals(StableKey.of(1)));
        assertThat(ops).anyMatch(op -> op instanceof KairoListDiff.Op.Insert<?> ins && ins.key().equals(StableKey.of(4)));

        // Duplicate keys report DuplicateKeyError
        List<Row> dup = List.of(new Row(9, "X"), new Row(9, "Y"));
        assertThat(diff.diff(List.of(), dup, r -> StableKey.of(r.id())))
                .hasOnlyElementsOfType(KairoListDiff.Op.DuplicateKeyError.class);
    }
}
