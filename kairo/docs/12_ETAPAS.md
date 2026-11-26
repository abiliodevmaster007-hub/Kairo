# Kairo v1 — Plano de Construção em 12 Etapas

| Etapa | Fase | Módulos / Entregas | Critério de Saída |
| --- | --- | --- | --- |
| **01** | **A — Fundações** | `kairo-annotations`, `kairo-core`, `kairo-testkit`, `architecture-tests` | Esqueleto Gradle Java 17 e ArchUnit sem imports de plataforma |
| **02** | **A.1 — Fatia Vertical** | `LoginVerticalSliceTest`, `FakeRenderer` | Fluxo ponta a ponta XML → IR → State → Patches → Renderer |
| **03** | **B — Pipeline Declarativo** | `kairo-ir`, `kairo-xml` | Parsing StAX seguro (XXE desativado) com diagnósticos ficheiro/linha |
| **04** | **C — Motor de Estilos** | `kairo-css-parser`, `kairo-style` | Seletores ID/classe/tipo, especificidade, tokens, estados e cascata |
| **05** | **D — Runtime Reativo** | `kairo-state`, `kairo-events`, `kairo-lifecycle`, `kairo-runtime` | `State`, `Derived`, `ObservableList`, transações e reconciliação |
| **06** | **E.0 — Layout Neutro** | `kairo-layout` | Measure, Layout (`Auto`/`Fixed`/`Fill`) e Overflow 100% no Core |
| **07** | **F.1 — API & Catálogo Base** | `kairo-components-api`, `kairo-components-basic` | Registry explícito, slots, `Screen`, `Column`, `Row`, `Box`, `Stack`, `Scroll`, `Text`, `Button`, `Card`, `Divider`, `Image`, `Progress` |
| **08** | **F.2 — Inputs, Forms, Listas & Router** | `kairo-components-input`, `kairo-components-form`, `kairo-components-list`, `kairo-navigation` | Two-way binding com loop guard, validadores, lista com `StableKey` e `Router` com back stack |
| **09** | **G — Async & Recursos** | `kairo-async`, `kairo-resources` | `FutureState`, schedulers, cancelamento por scope, cache local seguro |
| **10** | **E — Renderer Android** | `kairo-renderer-api`, `kairo-renderer-android`, `kairo-platform-android` | Única camada Android: `measureLeaf`, aplicação de bounds, IME, foco e ciclo de vida |
| **11** | **H — CLI & Inspetor** | `kairo-inspector`, `tools/kairo-cli` | `create`, `doctor`, `build`, `run`, `clean`, `test`, `inspect` com mascaramento de segredos |
| **12** | **I.1 — Kairo Gallery & Release** | `examples/gallery/` | 12 categorias de teste exercitando apenas APIs públicas da v1 |
