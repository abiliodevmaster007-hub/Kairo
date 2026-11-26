package dev.kairo.lifecycle;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

final class LifecycleScopeTest {
    @Test
    void childScopesCascadeUnmountAndDestroySafely() {
        LifecycleScope parent = new LifecycleScope();
        LifecycleScope child = parent.createChildScope();
        List<String> trace = new ArrayList<>();

        parent.ownForMount(() -> trace.add("parent:unmount"));
        child.ownForMount(() -> trace.add("child:unmount"));
        child.ownForLifetime(() -> trace.add("child:destroy"));
        parent.ownForLifetime(() -> trace.add("parent:destroy"));

        parent.onMount();
        assertThat(parent.isMounted()).isTrue();
        assertThat(child.isMounted()).isTrue();

        parent.onDestroy();
        assertThat(parent.isDestroyed()).isTrue();
        assertThat(child.isDestroyed()).isTrue();
        assertThat(trace).contains("child:unmount", "parent:unmount", "child:destroy", "parent:destroy");
    }

    @Test
    void teardownDisposesRemainingResourcesEvenWhenOneThrows() {
        LifecycleScope scope = new LifecycleScope();
        AtomicInteger disposedCount = new AtomicInteger();

        scope.ownForMount(disposedCount::incrementAndGet);
        scope.ownForMount(() -> {
            disposedCount.incrementAndGet();
            throw new IllegalStateException("cleanup failure");
        });
        scope.ownForMount(disposedCount::incrementAndGet);

        scope.onMount();
        assertThatThrownBy(scope::onUnmount).isInstanceOf(IllegalStateException.class);
        assertThat(disposedCount.get()).isEqualTo(3);
    }
}
