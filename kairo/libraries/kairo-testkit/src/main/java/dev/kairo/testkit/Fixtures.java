package dev.kairo.testkit;

import dev.kairo.layout.LayoutConstraints;

/**
 * Reusable Stage A/A.1 fixtures and viewport presets (small phone, standard phone, tablet).
 */
public final class Fixtures {
    public static final LayoutConstraints SMALL_PHONE = LayoutConstraints.fixed(320, 568);
    public static final LayoutConstraints STANDARD_PHONE = LayoutConstraints.fixed(390, 844);
    public static final LayoutConstraints TABLET = LayoutConstraints.fixed(800, 1280);

    public static final String LOGIN_XML = """
            <Screen id="login" class="login">
              <Column gap="16" padding="24">
                <Text id="heading" value="Entrar"/>
                <Input id="email" bind:value="email" placeholder="Email"/>
                <Input id="password" bind:value="password" type="password"/>
                <Button id="submit" disabled="{{ !canSubmit }}" onClick="submit">Entrar</Button>
              </Column>
            </Screen>
            """;

    public static final String LOGIN_CSS = """
            Theme { color.primary: #4F46E5; color.surface: #09090B; }
            Screen.login { background: token(color.surface); }
            Button.primary { background: token(color.primary); color: #FFFFFF; }
            """;

    private Fixtures() {}
}
