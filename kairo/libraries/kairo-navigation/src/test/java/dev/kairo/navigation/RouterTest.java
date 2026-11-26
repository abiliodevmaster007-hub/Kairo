package dev.kairo.navigation;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

final class RouterTest {
    @Test
    void supportsQueryParamsGuardsWildcardStackResetAndEntryState() {
        Router router = new Router();
        router.registerRoute("/", "HomeScreen");
        router.registerRoute("/login", "LoginScreen");
        router.registerRoute("/users/:id", "UserDetailScreen");
        router.registerRoute("*", "NotFoundScreen");

        AtomicBoolean authenticated = new AtomicBoolean(false);
        router.addGuard(dest -> {
            if ("UserDetailScreen".equals(dest.screenName()) && !authenticated.get()) {
                return new Router.GuardDecision.Redirect("/login");
            }
            return new Router.GuardDecision.Allow();
        });

        assertThat(router.navigate("/")).isTrue();
        assertThat(router.canGoBack.get()).isFalse();

        // Redirected to /login when unauthenticated
        assertThat(router.navigate("/users/42?tab=security")).isTrue();
        assertThat(router.current().get().screenName()).isEqualTo("LoginScreen");
        assertThat(router.canGoBack.get()).isTrue();

        authenticated.set(true);
        assertThat(router.navigateAndClearStack("/users/42?tab=security&sort=asc")).isTrue();
        assertThat(router.current().get().screenName()).isEqualTo("UserDetailScreen");
        assertThat(router.current().get().params()).containsEntry("id", "42");
        assertThat(router.current().get().queryParams()).containsEntry("tab", "security").containsEntry("sort", "asc");
        assertThat(router.backStack()).hasSize(1);

        router.saveCurrentEntryState("scrollY", 320);
        assertThat(router.readCurrentEntryState("scrollY")).isEqualTo(320);

        assertThat(router.navigate("/unknown/route")).isTrue();
        assertThat(router.current().get().screenName()).isEqualTo("NotFoundScreen");
        assertThat(router.popToRoot()).isTrue();
        assertThat(router.current().get().screenName()).isEqualTo("UserDetailScreen");
    }
}
