package dev.kairo.lifecycle;

import java.util.List;

@FunctionalInterface
public interface Disposable {
    void dispose();

    static Disposable empty() {
        return () -> {};
    }

    static Disposable composite(Disposable... disposables) {
        return composite(List.of(disposables));
    }

    static Disposable composite(List<? extends Disposable> disposables) {
        List<Disposable> copy = List.copyOf(disposables);
        return () -> {
            RuntimeException firstFailure = null;
            for (int i = copy.size() - 1; i >= 0; i--) {
                Disposable item = copy.get(i);
                if (item == null) continue;
                try {
                    item.dispose();
                } catch (RuntimeException ex) {
                    if (firstFailure == null) {
                        firstFailure = ex;
                    } else {
                        firstFailure.addSuppressed(ex);
                    }
                }
            }
            if (firstFailure != null) {
                throw firstFailure;
            }
        };
    }
}
