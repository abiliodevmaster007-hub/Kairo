# Kairo v1 — Framework de UI Declarativa Java-first

Framework modular de UI declarativa em **Java 17** com pipeline completo `XML + .kcss + State -> Layout Neutro -> RenderPatch -> AndroidRenderer (Views/Canvas + JNI C++17)`.

## Regra Invariante de Arquitetura

Nenhum módulo neutro importa `android.*`, `androidx.*`, `javafx.*`, `javax.swing.*`, `org.jetbrains.skija.*`, `org.lwjgl.*` ou `org.reflections.*`.
Apenas `:libraries:kairo-renderer-android` e `:libraries:kairo-platform-android` comunicam com o SDK Android e código nativo C++ através dos contratos opacos de `:libraries:kairo-renderer-api`.

Esta fronteira é verificada automaticamente em `:tests:architecture-tests` com **ArchUnit** e no CI (`.github/workflows/ci.yml`).

## Módulos (25 subprojetos Gradle)

- **Fundações & Contratos:** `kairo-annotations`, `kairo-core`, `kairo-renderer-api`
- **Pipeline Declarativo:** `kairo-ir`, `kairo-xml`, `kairo-css-parser`, `kairo-style`
- **Runtime Reativo & Layout:** `kairo-state`, `kairo-events`, `kairo-lifecycle`, `kairo-layout`, `kairo-runtime`
- **Componentes 100% Independentes de Plataforma:** `kairo-components-api`, `kairo-components-basic`, `kairo-components-input`, `kairo-components-form`, `kairo-components-list`, `kairo-navigation`
- **Serviços & Capabilities:** `kairo-async`, `kairo-resources`
- **Fronteira Android + C++17 (Única camada com SDK Android / NDK):** `kairo-renderer-android` (`src/main/cpp/kairo_render_engine.cpp`), `kairo-platform-android`
- **Qualidade & Tooling:** `kairo-inspector`, `kairo-testkit`, `tools/kairo-cli`
- **Exemplos & Aceitação:** `examples/login-slice`, `examples/gallery` (`12` categorias ponta a ponta)

## CLI & Verificação Rápida

```bash
./gradlew :tests:architecture-tests:test   # Prova fronteira neutra via ArchUnit
./gradlew test                              # Corre todas as suites JVM e GalleryAcceptanceTest
./gradlew :tools:kairo-cli:run --args="doctor --dir ."
./gradlew :tools:kairo-cli:run --args="inspect examples/gallery"
```

## Documentação Complementar

- [Plano em 12 Etapas](docs/12_ETAPAS.md)
- [Diário Completo de Construção (Etapas 01–12)](docs/DIARIO_ETAPAS.md)
- [Contratos dos Componentes & Limitações v1](docs/COMPONENTES_CONTRATOS_v1.md)
