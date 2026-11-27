# Diário de Construção do Kairo v1 (Registo de Etapas)

Este documento regista tudo o que foi construído, decidido e verificado em cada uma das 12 etapas do framework Kairo.

---

## Etapa 01 (A) — Fundações, Contratos Neutros, CI & TestKit [CONCLUÍDA]

### O que foi criado e configurado
1. **Orquestração Gradle & Baseline Java 17**
   - `kairo/settings.gradle.kts`, `kairo/build.gradle.kts`, `kairo/gradle/libs.versions.toml`, `kairo/gradle.properties`, `kairo/gradlew`, `kairo/gradle/wrapper/gradle-wrapper.properties`.
2. **Pipeline de CI**
   - `kairo/.github/workflows/ci.yml`: Executa em Java 17 os testes ArchUnit (`:tests:architecture-tests:test`), suite completa com JaCoCo e guarda estático contra imports de plataforma nos módulos neutros.
3. **Módulo `kairo-annotations`**
   - `@UIAction`, `@UIExpose`, `@Id`, `@UIComponent`.
4. **Módulo `kairo-core`**
   - `KairoId`, `StableKey`, `ComponentIdentity`, `SourceLocation`, `Diagnostic`, `CapabilityRegistry`, `Result<T>`, `KairoVersion` + `KairoCoreTest`.
5. **Módulo `kairo-testkit`**
   - `FakeRenderer`, `TestClock`, `TestScheduler`, `EventTestDriver`, `Fixtures`, `TreeAssertions`.
6. **Testes de Arquitetura (`tests/architecture-tests`)**
   - `KairoArchitectureTest.java` (ArchUnit) garantindo zero imports de plataforma fora de `kairo-renderer-android` e `kairo-platform-android`.

---

## Etapa 02 (A.1) — Fatia Vertical: Login Ponta a Ponta [CONCLUÍDA]

### O que foi criado, corrigido e verificado
1. **Aplicação de Fatia Vertical (`examples/login-slice`)**
   - `login.xml`, `login.css`, `LoginScreenModel.java` (100% neutro) e `KairoLoginActivity.java` (host fino Android).
2. **Melhorias no Runtime e Renderer**
   - `KairoRuntime.java`: coalescência de recomputações (`isRecomputing` / `recomputeQueued`) e deduplicação de subscrições (`IdentityHashMap`).
   - `AndroidRenderer.java`: propagação de `UpdateBounds` para contentores `KairoLayoutHostGroup` aninhados (`pendingBounds`), `placeholder`, `type="password"` e cores hex.
3. **Prova Ponta a Ponta (`LoginVerticalSliceTest.java`)**
   - Valida o pipeline completo `XML → IR → State/Derived → Layout → Reconciler → RenderPatch → FakeRenderer` e mascaramento de passwords no `TreeInspector`.

---

## Etapa 03 (B) — Pipeline Declarativo (XML, IR, Validação & Dialeto de Estilos Kairo) [CONCLUÍDA]

### Decisão de arquitetura sobre estilos
- **Não usamos CSS da web.** O Kairo adota um **dialeto nativo próprio de estilos UI (`.kcss`)** com propriedades previsíveis para layout nativo (`width`, `height`, `padding`, `margin`, `gap`, `align`, `grow`, `shrink`, `wrap`, `overflow`, `background`, `radius`, `border-color`, `opacity`, tipografia) e tokens de tema (`Theme { ... }` / `token(...)`), rejeitando explicitamente construções web incompatíveis (`position: fixed`, `float`, `display: grid`, `z-index`, `calc()`, `!important`, `@media`).

### O que foi criado e atualizado
1. **Módulo `kairo-ir`**
   - `IRDocument.java`: documento normalizado com `root`, `styleImports`, `componentImports` e `Diagnostic.Bag`.
   - `BindingExpressionValidator.java`: gramática segura de expressões (`{{ expr }}` e `bind:...`) sem `eval` — bloqueia `;`, `=`, `new `, `Runtime` e expressões arbitrárias.
   - `KairoStyleDialect.java`: lista branca de propriedades nativas do Kairo e rejeição explícita de CSS web (`STYLE_WEB_CSS_UNSUPPORTED`, `STYLE_WEB_CALC_FORBIDDEN`, `STYLE_WEB_IMPORTANT_FORBIDDEN`).
   - `IRValidator.java`: valida IDs duplicados, `key` obrigatória em `<List>`, `name` obrigatório em `<slot>` e gramática segura de todas as expressões.
2. **Módulo `kairo-xml`**
   - `KairoXmlParser.java`: endurecido com `XMLConstants.ACCESS_EXTERNAL_DTD = ""` e `ACCESS_EXTERNAL_SCHEMA = ""` (além de `SUPPORT_DTD = false`), suporte a `<kairo>`, `<import component="..." as="..."/>`, `<style src="..."/>`, validação de estilos inline pelo `KairoStyleDialect` e `SourceLocation` exato (`ficheiro:linha:coluna`).
3. **Módulo `kairo-components-api`**
   - `SemanticIRValidator.java`: valida `IRDocument` contra o `ComponentRegistry` explícito (deteta tags desconhecidas, props obrigatórias em falta, props desconhecidas, eventos não suportados e violações de `ChildrenPolicy`: `NONE`, `SINGLE`, `TEMPLATE`).
4. **Módulo `kairo-css-parser`**
   - `build.gradle.kts` ligado a `:libraries:kairo-ir`.
   - `KairoCssParser.parseWithDiagnostics(...)`: valida regras `.kcss` contra `KairoStyleDialect` com linha exata do erro.
5. **Suites de Teste da Etapa 03**
   - `KairoXmlPipelineTest.java`, `SemanticIRValidatorTest.java` e `KairoStyleDialectTest.java`.

---

## Etapa 04 (C) — Motor de Estilos Kairo (`.kcss`), Cascata, Temas & Trace [CONCLUÍDA]

### O que foi construído e atualizado
1. **Módulo `kairo-css-parser` (Dialeto `.kcss`)**
   - `CssStylesheet.java`: AST estendida com lista de `@import`, tokens base (`Theme`), temas nomeados (`Theme light`, `Theme dark`) e `Rule` com `SourceLocation`.
   - `KairoCssParser.java`: suporta `@import "..."`, blocos `Theme` e `Theme <nome>`, seletores separados por vírgula e rejeita explicitamente combinações web (`>`, `+`, `~`, `[...]`, `::`, `*`) com `STYLE_WEB_SELECTOR_UNSUPPORTED` e `@media`/`@keyframes` com `STYLE_WEB_ATRULE_FORBIDDEN`.
   - `KairoStyleDialectTest.java`: garante aceitação do dialeto nativo e rejeição de CSS web em tempo de parse.
2. **Módulo `kairo-style` (Cascata, Especificidade e Temas — 100% Neutro)**
   - `SelectorSpecificity.java`: tuplo exato `(ids, classesAndStates, types)` comparável sem overflow decimal (`12` classes nunca vencem `1` ID).
   - `VisualState.java` e `ViewportCapabilities.java`: suporte aos estados `PRESSED`, `FOCUSED`, `DISABLED`, `SELECTED`, `CHECKED`, `ERROR` e `HOVER` protegido por capacidade (`hoverSupported = false` por defeito em Android/touch).
   - `TokenResolver.java`: resolução recursiva de `token(key)` e `token(key, fallback)` com proteção contra ciclos transitivos (`STYLE_TOKEN_CYCLE`) e aviso para tokens ausentes (`STYLE_TOKEN_MISSING`).
   - `ThemeRegistry.java`: composição em camadas (`Theme` base -> `Theme <ativo>` -> overrides programáticos) e troca dinâmica de tema em runtime.
   - `ComputedStyle.java`: `EdgeInsets` para `padding`/`margin` (1, 2 ou 4 valores; **sem margin collapsing da web**), `getDouble`, `getColor`, igualdade por valor (`equals`/`hashCode`) para reconciliação incremental e herança restrita exclusivamente à tipografia (`color`, `font-size`, `font-weight`, `font-family`, `text-align`, `line-height`).
   - `StyleTrace.java`, `StyleResolutionResult.java` e `StyleEngine.java`: suporta estados encadeados (`Button.primary:focused:pressed`), combinador descendente, estilos inline `style="..."` com prioridade máxima validados pelo `KairoStyleDialect`, e registo de auditoria (`CAPABILITY_SKIPPED`, regras vencedoras e origem).
3. **Integração no Runtime e Inspetor**
   - `KairoRuntime.java`: gere `ThemeRegistry` (`setTheme(String)`), `ViewportCapabilities`, estilos inline, propaga `ERROR`/`CHECKED`/`SELECTED`/`DISABLED` e guarda `StyleResolutionResult` por nó.
   - `TreeInspector.java`: novo `dumpStyleTraceByLogicalId(...)` para inspeção da cascata e do tema ativo sem expor dados sensíveis.
4. **Suite de Testes da Etapa 04**
   - `StyleEngineTest.java`: valida tuplo de especificidade, desempate por ordem de declaração, estados encadeados, `:hover` condicionado por capacidade, `Theme light`/`dark`, tokens com fallback e deteção de ciclos, herança tipográfica, `EdgeInsets` e estilos inline.

---

## Etapa 05 (D) — Runtime Reativo, Estado, Eventos & Lifecycle [CONCLUÍDA]

### O que foi construído e refinado
1. **Módulo `kairo-state` (Reatividade sem Glitches & Sem Ciclos)**
   - `ReactiveSource.java`: contrato comum implementado por `State`, `Derived` e `ObservableList`.
   - `State.java`: pilha real de rastreamento aninhado via `ThreadLocal<Deque<Set<ReactiveSource>>>`, `State.untracked(...)` para leituras isoladas e `State.transaction(...)` com coalescência por identidade (`ListenerKey`) e deteção de ciclos infinitos de flush (`MAX_FLUSH_ROUNDS = 100`).
   - `Derived.java`: acompanha automaticamente outros `Derived`s e `ObservableList`s lidos durante o cálculo, evita glitches dentro de transações e deteta ciclos (`Cyclic Derived dependency detected`).
   - `ObservableList.java`: implementa `ReactiveSource`, notifica `Added`, `Removed`, `Updated`, `Moved`, `Cleared` e `ReplacedAll`, e respeita transações ativas.
   - `TwoWayLoopGuard.java`: guarda explícita `Origin { NONE, MODEL, VIEW }` contra feedback infinito em bindings bidirecionais.
2. **Módulo `kairo-lifecycle` (Escopos Hierárquicos & Teardown Seguro)**
   - `Disposable.java`: `empty()` e `composite(...)` resiliente a exceções.
   - `LifecycleScope.java`: suporta sub-escopos hierárquicos (`createChildScope()`), hooks `onMount`/`onUnmount`/`onDestroy`, limpeza em ordem inversa garantida mesmo quando algum recurso lança exceção.
3. **Módulo `kairo-events` (Despacho Capture -> Target -> Bubble)**
   - `UIEvent.java` & `CommonEvents.java`: fases `CAPTURE`, `TARGET`, `BUBBLE` mais eventos `PointerPressEvent`, `HoverEvent`, `KeyEvent` e `ScrollEvent`.
   - `EventDispatcher.java`: propagação fiel `CAPTURE -> TARGET -> BUBBLE` ao longo do caminho até à raiz com respeito por `stopPropagation()` e `preventDefault()`.
4. **Módulo `kairo-runtime` & Renderers (Reconciliação por Chaves, Diretivas & Estado Interativo)**
   - `ExpressionEvaluator.java`: resolução sem reflexão via `ExposedObject` / `Map` em caminhos pontuados (`user.role`), negação `!flag` e abertura recursiva de `State`/`Derived`.
   - `Reconciler.java`: algoritmo em 3 passagens com correspondência por `StableKey` e `logicalId`, emissão de `RenderPatch.MoveChild` sem destruir views nativas e remoção de propriedades obsoletas (`UpdateProp(..., null)`).
   - `KairoRuntime.java`: suporta diretivas condicionais (`if` / `when`), chaves estáveis (`stableKeyExpr`), estados interativos (`FOCUSED`, `PRESSED`, `HOVER`), ações embrulhadas em `State.transaction(...)`, subscrição automática de `ReactiveSource` e limpeza completa em `unmount()`/`destroy()`.
   - `FakeRenderer.java` & `AndroidRenderer.java`: suporte completo a `MoveChild` e remoção segura de propriedades nulas.
5. **Suites de Teste da Etapa 05**
   - `ReactiveStateTest.java`, `EventDispatcherTest.java`, `LifecycleScopeTest.java`, `ReconcilerAndExprTest.java` e `ReactiveRuntimeTest.java` (`tests/runtime-tests`).

---

## Etapa 06 (E.0) — Layout Engine Neutro no Core (`MeasurePass -> DistributePass -> OverflowPass`) [CONCLUÍDA]

### O que foi construído e refinado (100% fora do Android)
1. **Primitivas Geométricas e Contratos (`kairo-layout`)**
   - `LayoutBounds.java`: limites `(x, y, width, height)` com `right()`, `bottom()`, `contains(...)` e `intersects(...)`.
   - `LayoutConstraints.java`: `unbounded()`, `fixed()`, `atMost()`, `clampWidth/Height` e `shrinkBy(EdgeInsets)`.
   - `LayoutSize.java`: `Auto`, `Fixed(px)`, `Fill(weight)` e `Percent(fraction)` (`50%`, `100%`).
   - `LayoutAlignment.java` (`START`, `CENTER`, `END`, `STRETCH`) e `OverflowPolicy.java` (`VISIBLE`, `CLIP`, `SCROLL` + `OverflowInfo`).
   - `LayoutStyleReader.java`: resolve propriedades do estilo computado `.kcss` com *fallback* para atributos declarados em XML (`padding`, `margin`, `gap`, `align`, `grow`, `shrink`, `wrap`, `overflow`, `axis`).
2. **Motor em 3 Passagens Modulares**
   - `MeasurePass.java`: medição intrínseca recursiva com suporte a `min-*`/`max-*`, `wrap` multi-linha, expansão sem estrangulamento em contentores `Scroll`, margens aditivas (**zero margin collapsing**) e delegação de folhas apenas ao `LeafMeasurer` da plataforma.
   - `DistributePass.java`: distribuição por pesos `Fill`/`grow`, redução proporcional `shrink` respeitando `min-*`, linhas encadeadas em `wrap: wrap` e alinhamento em eixos `VERTICAL`, `HORIZONTAL` e `OVERLAY`.
   - `OverflowPass.java` & `LayoutEngine.java`: cálculo exato de `contentWidth`/`contentHeight` face à caixa distribuída e emissão de `OverflowInfo`.
3. **Integração no Runtime e Inspetor**
   - `KairoRuntime.java` & `TreeInspector.java`: expõem `lastLayoutResult()`/`overflowInfo(id)` e assinalam `overflow=<POLICY>` na árvore do inspetor.
4. **Suite de Testes da Etapa 06**
   - `LayoutEngineTest.java`: valida pesos `Fill(1)` vs `Fill(2)`, margens sem *collapsing*, `wrap: wrap`, `shrink` com `min-width`, `Scroll` com deteção exata de `overflowY` e alinhamento `OVERLAY`.

---

## Etapa 07 (F.1) — API de Componentes & Catálogo Base (12 Componentes Neutros) [CONCLUÍDA]

### O que foi construído e integrado
1. **Módulo `kairo-components-api`**
   - `ComponentDescriptor.java`: `Builder` fluente e contrato `AccessibilitySpec` (`role`, `focusable`, `liveRegion`).
   - `ComponentRegistry.java`: registo explícito sem reflection scanning (`Registrar` modular, `registerModule`, `registerCustom`, `contains`).
   - `SlotProjection.java`: extração de slots nomeados (`slot="header"` ou `<slot name="...">`) e projeção automática em templates personalizados com suporte a conteúdo de *fallback*.
   - `SemanticIRValidator.java`: suporta diretivas universais (`if`, `when`, `style`, `key`, `slot`, `accessibilityLabel`, `visible`) e valida slots declarados (`SEM_UNKNOWN_SLOT`).
2. **Módulo `kairo-components-basic` (12 Componentes Base 100% Neutros)**
   - `BasicComponents.java`: catálogos completos para `Screen`, `Column`, `Row`, `Box`, `Stack`, `Scroll`, `Text`, `Button`, `Image` (`src`/`alt` obrigatórios), `Card`, `Divider` e `Progress`.
   - `BasicUI.java`: DSL fluente em Java puro para compor árvores `NodeIR` sem XML e sem dependências Android.
3. **Expansão de Componentes Custom & Defaults no Runtime**
   - `KairoRuntime.java`: injeta `defaultProperties()` dos descritores e expande recursivamente componentes custom via `CustomComponentExpander` + `SlotProjection` (com proteção de profundidade máxima `16` e propagação de `#id` e `.classes`).
4. **Suites de Teste da Etapa 07**
   - `BasicCatalogTest.java`, `SlotProjectionTest.java` e `CustomComponentExpansionTest.java` (`tests/runtime-tests`).

---

## Etapa 08 (F.2) — Inputs, Forms, Listas Virtualizadas & Navegação [CONCLUÍDA]

### O que foi construído e refinado (100% Neutro)
1. **Módulo `kairo-components-input` (5 Controlos Interativos)**
   - `SelectOption.java`: contrato de opção `{value, label, disabled}` com serialização e *parsing* determinísticos no formato `value:label|...` sem reflexão.
   - `InputComponents.java`: catálogos atualizados via `ComponentDescriptor.Builder` para `Input`, `TextArea`, `Checkbox`, `Switch` e `Select`, com acessibilidade focável (`accessibility(true, false)`), `maxLength`, estado visual `error`, `onFocus` e `onSubmit`.
2. **Módulo `kairo-components-form` (Validação, Políticas & Redação de Segredos)**
   - `FormController.java`: políticas `ValidateOn` (`SUBMIT`, `BLUR`, `CHANGE`), validadores combináveis (`required`, `minLength`, `maxLength`, `email`, `pattern`, `matches`, `compose`), `FieldState.hasError` (`Derived<Boolean>`), ciclo completo `setValue`/`blur`/`submit`/`reset`, `twoWayStringStates()`, e `safeSnapshot()` que mascara automaticamente campos `sensitive` (`"••••••"`).
   - Descritores declarativos para `Form` (`validateOn`, `disabled`, `onSubmit`) e `FormField` (`name` obrigatório, `label`, `helperText`, `sensitive`).
3. **Módulo `kairo-components-list` (Virtualização & Reconciliação por Chave)**
   - `ListWindow.java`: calculador neutro da janela visível (`WindowRange` com `startIndex`, `endIndex`, `topSpacerHeight`, `bottomSpacerHeight`, `totalContentHeight` e `slice(...)`) para listas com 10.000+ itens mantendo apenas os itens visíveis + *overscan* na árvore virtual.
   - `KairoListDiff.java`: operações incrementais `Insert`, `Remove`, `Move`, `Update` por chave estável e descritor `<List>` com `estimatedItemHeight`, `overscan`, `emptyMessage` e `onScroll`.
4. **Módulo `kairo-navigation` (Router, Guards & Back Stack)**
   - `Router.java`: rotas parametrizadas (`/users/:id`), rota *wildcard* (`*`), *parsing* automático de `queryParams`, `RouteGuard` (`Allow`, `Redirect` protegido a 8 saltos, `Block`), `navigate`, `replace`, `navigateAndClearStack`, `back`, `popToRoot`, `canGoBack` (`Derived<Boolean>`) e preservação/limpeza automática de estado por entrada de *back stack*.
5. **Suites de Teste da Etapa 08**
   - `InputCatalogTest.java`, `FormControllerTest.java`, `ListEngineTest.java` e `RouterTest.java`.

---

## Etapa 09 (G) — Async Limitado & Recursos Locais Seguros [CONCLUÍDA]

### O que foi construído e refinado (100% Neutro)
1. **Módulo `kairo-async` (Async Cooperativo Ligado ao `LifecycleScope`)**
   - `CancellationToken.java`: token *thread-safe* com `throwIfCancelled()`, *listeners* `onCancel(...)` e sub-tokens (`createChild()`), integrado ao `LifecycleScope`.
   - `KairoSchedulers.java`: *pools* daemon limitados (`io()`, `compute()`), `immediate()` e registo neutro do *dispatcher* de UI (`setUiScheduler(...)` / `ui()`) para hosts como Android ligarem sem acoplar `android.*`.
   - `AsyncResult.java`: *sealed interface* em Java 17 (`Idle`, `Loading`, `Success`, `Failure`, `Cancelled`) para *snapshots* exaustivos.
   - `FutureState.java`: transações sem glitches, flags `Derived` (`isIdle`, `isLoading`, `isSuccess`, `isError`, `isCancelled`, `isDone`), guarda de geração (`activeGeneration`) impedindo que respostas antigas sobrescrevam um `reload()`, `retry()`, cancelamento automático em `onUnmount()`/`onDestroy()` com re-registo em remounts e entrega imediata de *callbacks* após conclusão síncrona.
2. **Módulo `kairo-resources` (Recursos Locais & Cache LRU Limitado)**
   - `ResourcePathValidator.java`: normalização canónica POSIX e bloqueio rigoroso de *path traversal* (`..`, `%2e`, `%2f`, `%5c`), bytes de controlo/nulos, URLs remotas e prefixos/unidades (`http://`, `https://`, `file://`, `content://`, `C:\`) + inferência automática de MIME (`.png`, `.svg`, `.kcss`, `.json`, `.ttf`, etc.).
   - `ResourceCache.java`: cache LRU *thread-safe* limitado por orçamento em bytes (padrão 16 MiB) e contagem máxima de entradas, com métricas de *hit/miss/eviction*.
   - `ResourceManager.java`: cópia defensiva em `LocalResource`, *loaders* `inMemoryBuilder()` e `Loader.classpath(...)`, `preload(...)`, `resolveText(...)`, `invalidate(...)` e `resolveResult(...)` devolvendo `Result<LocalResource>` com códigos diagnósticos explícitos (`RES_EMPTY_PATH`, `RES_FORBIDDEN_PATH`, `RES_NOT_FOUND`).
3. **Suites de Teste da Etapa 09**
   - `FutureStateTest.java` (`kairo-async`) e `ResourceManagerTest.java` (`kairo-resources`).

---

## Etapa 10 (E) — Renderer Android, Motor de Render (Views/Canvas + C++17 NDK) & Platform Adapter [CONCLUÍDA]

### O que foi construído e isolado (Única fronteira Android + C++ JNI)
1. **Módulo Neutro `kairo-renderer-api`**
   - `RenderEngineStats.java`: contrato neutro de métricas do motor de render (`backendName`, `nativeAccelerated`, `mountedNodes`, `lastPatchBatchSize`, `totalPatchesApplied`, `lastFrameDurationMicros`).
   - `Renderer.java`: estendido com `engineStats()` e `flushFrame()` sem expor qualquer tipo Android ou nativo.
2. **Motor de Render Nativo C++17 (`kairo-renderer-android/src/main/cpp/`)**
   - `CMakeLists.txt`, `kairo_render_engine.hpp` e `kairo_render_engine.cpp`: `KairoSceneGraph` em C++17 com estruturas planas *cache-friendly*, *hit-testing* hierárquico com *clipping*, rasterizador SDF analítico para caixas arredondadas e bordas com anti-aliasing 1px, *blending* Porter-Duff `SrcOver` em RGBA8888 e *bridge* JNI (`libkairo_render.so`) para `Bitmap` (`AndroidBitmap_lockPixels`) e `Surface` (`ANativeWindow_lock`).
   - `KairoNativeEngine.java` e `KairoNativeSurfaceView.java`: carregamento seguro via `System.loadLibrary("kairo_render")` com *fallback* transparente para `Canvas`/`ViewGroup` quando a `.so` nativa não estiver presente.
3. **Motor de Renderização Android Modular (`kairo-renderer-android`)**
   - `KairoColorParser.java` e `KairoSceneEncoder.java`: codificação pura em Java 17 de cores (`#RGB`, `#RRGGBB`, `#AARRGGBB`, nomeadas), bordas, raios, opacidade, estados visuais (`ERROR`, `FOCUSED`, `DISABLED`) e `clipChildren`.
   - `KairoLayoutHostGroup.java`: posicionamento e medição `EXACTLY` guiados estritamente pelos `LayoutBounds` do `kairo-layout`, incluindo `KairoScrollView` dedicado (`contentHost()`) com `ScrollEvent`.
   - `KairoStyleApplier.java`: superfícies arredondadas via `GradientDrawable`, bordas, opacidade, visibilidade, *padding* em folhas e tipografia (`font-size`, `font-weight`, `text-align`, `max-lines`).
   - `KairoImeBridge.java`: *two-way binding* para `Input`/`TextArea` com `TwoWayLoopGuard`, preservação de composição IME (`BaseInputConnection.getComposingSpanStart`), *cursor safety*, `maxLength`, `type`, `onFocus`, `onSubmit` e controlo do teclado virtual.
   - `KairoAccessibilityBridge.java`: `accessibilityLabel`/`alt`, `accessibilityHidden`, `focusable` e `announceForAccessibility` (`liveRegion`).
   - `KairoViewFactory.java`, `KairoRenderEngine.java` e `AndroidRenderer.java`: suporte completo aos 19 componentes da v1 (incluindo `Select`/`Spinner` com `SelectOption`, `Checkbox`, `Switch`, `Image` local via `ResourceManager`, `Divider` e `Progress`).
4. **Módulo `kairo-platform-android`**
   - `AndroidMainScheduler.java`: liga `KairoSchedulers.ui()` ao `Handler(Looper.getMainLooper())` sem vazar tipos Android para `kairo-async`.
   - `AndroidResourceLoader.java`: liga `AssetManager` ao `ResourceManager.Loader` sob validação rigorosa de `ResourcePathValidator`.
   - `AndroidPlatformHost.java`: `bootstrap(...)` completo registando `AndroidClipboardCapability`, `AndroidAssetsCapability`, `AndroidMainScheduler`, `AndroidRenderer` e hooks de ciclo de vida/back.
5. **Suites de Teste da Etapa 10**
   - `RendererContractTest.java` (`kairo-renderer-api`) e `KairoSceneEncoderTest.java` (`kairo-renderer-android` — 100% executável em JVM sem mocks Android).

---

## Etapa 11 (H) — Inspetor com Mascaramento & Kairo CLI [CONCLUÍDA]

### O que foi construído (100% Neutro)
1. **Módulo `kairo-inspector` (Inspeção Segura Sem Vazamento de Segredos)**
   - `SecretRedactor.java`: deteção automática por padrão (`password`, `passwd`, `pwd`, `secret`, `token`, `apikey`, `pin`, `cvv`, `credential`, `bearer`), `type="password"` e `sensitive="true"`, substituindo valores por `"••••••"`.
   - `TreeInspector.java`: `dumpTree(...)` (árvore + *bounds* + *overflow* + props mascaradas), `dumpStyleTraceByLogicalId(...)`, `dumpBindings(...)` (bindings reativos e two-way mascarados), `dumpSnapshot(...)` (incluindo `RenderEngineStats`) e `formatDiagnostics(...)`.
2. **Ferramenta `tools/kairo-cli` (CLI Completa sem IDE)**
   - `ProjectScaffolder.java`: gera um projeto Gradle Java 17/Android completo (`create <nome> [--dir] [--package]`) com `app.kcss`, `main.xml`, `MainScreenModel`, `MainActivity` e `MainScreenTest` + bloqueio contra *path traversal*.
   - `DoctorChecker.java`: verifica Java 17+, `gradlew`, `ANDROID_HOME`/`ANDROID_SDK_ROOT` e analisa fontes Java neutras contra imports proibidos.
   - `ProjectInspector.java`: valida todos os `.xml` e `.kcss` de um projeto via `KairoXmlParser` + `IRValidator` + `SemanticIRValidator` + `KairoCssParser` com ficheiro, linha e coluna exatos.
   - `GradleRunner.java` & `KairoCli.java`: comandos `create`, `doctor`, `inspect`, `build`, `run`, `clean`, `test` com códigos de saída testáveis.
3. **Suites de Teste da Etapa 11**
   - `TreeInspectorTest.java` (`kairo-inspector`) e `KairoCliTest.java` (`tools/kairo-cli` com `@TempDir`).

---

## Etapa 12 (I.1) — Kairo Gallery & Release v1 [CONCLUÍDA]

### O que foi construído e documentado
1. **Aplicação de Aceitação (`examples/gallery`)**
   - `GalleryApp.java`, `gallery.css` (`Theme` base + `Theme light` + `Theme dark`) e `screens/index.xml`: exercita **exclusivamente APIs públicas da v1** em todas as **12 categorias** (Layout, Texto/Ações, Input, Seleção, Media/Superfície, Feedback, Form, Listas 10k itens com `ListWindow` + `KairoListDiff`, Navegação com *guards* e *query params*, Async cooperativo com cancelamento, Temas/Estados/Trace e Stress).
   - `GalleryAcceptanceTest.java`: prova ponta a ponta em JVM pura via `FakeRenderer` e `TestScheduler`.
2. **Documentação Final da v1**
   - `docs/COMPONENTES_CONTRATOS_v1.md`: catálogo completo dos 19 componentes, regras `.kcss`, fronteira ArchUnit/segurança e limitações v1.
   - `README.md`: visão consolidada dos 25 módulos, instruções de verificação e arquitetura v1 pronta.

---

## Estabilização & Endurecimento v1 (Etapas S1–S5) [CONCLUÍDA]

### O que foi endurecido sem quebrar contratos públicos da v1
1. **Etapa S1 — CI na Raiz & Reprodutibilidade Gradle**
   - Criado `/.github/workflows/kairo-ci.yml` na raiz do repositório validando em paralelo o ecossistema Java 17 (`Gradle 8.10.2`, `:tests:architecture-tests:test`, `check jacocoTestReport`, e guarda estática alargada a todos os módulos neutros incluindo `kairo-inspector`, `kairo-testkit`, `tools/kairo-cli` e `examples/gallery`) e a aplicação Web (`npm ci`, `npm run lint`, `npm run build`).
   - Sincronizado `/kairo/.github/workflows/ci.yml` com Gradle `8.10.2` e a lista completa de módulos neutros.
2. **Etapa S2 — Contrato de Concorrência (`UiScheduler`) & Isolamento de Transações (`kairo-state` + `kairo-platform-android`)**
   - Novos contratos neutros em `dev.kairo.state`: `UiScheduler`, `ConcurrencyPolicy` (`DISPATCH_TO_UI`, `STRICT_THROW`, `IMMEDIATE`), `ReactiveErrorHandler` e `ReactiveRuntimeConfig`.
   - `State<T>`, `Derived<T>` e `ObservableList<T>` endurecidos com visibilidade `volatile`/sincronizada, redirecionamento automático ou rejeição estrita (`STRICT_THROW`) em mutações fora da UI thread e isolamento de exceções por *listener* (sem bloquear *Derived*s nem subscritores irmãos; ciclos reativos continuam rejeitados explicitamente).
   - `AndroidMainScheduler` passa a implementar `UiScheduler` + `FutureState.Scheduler`, registando-se automaticamente em `ReactiveRuntimeConfig.setUiScheduler(...)`.
   - Suite `StateConcurrencyAndIsolationTest.java` cobrindo *workers* concorrentes em `DISPATCH_TO_UI`, rejeição `STRICT_THROW`, isolamento de erros em *listeners* e limite de ciclos.
3. **Etapa S3 — Verificação Matemática do Motor de Layout (`kairo-layout`)**
   - Refinado `DistributePass.java` com redistribuição iterativa (*freeze loop*) quando irmãos `fill`/`grow`/`shrink` atingem `min-*`/`max-*` e cálculo exato do eixo cruzado líquido (descontando margens) em linhas com `wrap`.
   - Criada a suite matemática dedicada por categoria: `VerticalLayoutMathTest`, `HorizontalLayoutMathTest`, `ConstraintsLayoutMathTest`, `WrappingLayoutMathTest` e `OverflowLayoutMathTest`.
4. **Etapa S4 — Thread Safety no Parser XML (`kairo-xml`)**
   - `KairoXmlParser.java` atualizado para `ThreadLocal<XMLInputFactory>` mantendo DTD/entidades externas bloqueadas por thread e fecho garantido do `XMLStreamReader` em `finally`.
   - Criado `KairoXmlConcurrencyTest.java` validando parsing concorrente multi-thread de XML válidos, malformados e tentativas XXE.
5. **Etapa S5 — Instrumentação de Performance (`kairo-runtime` & `kairo-inspector`)**
   - Criado `RuntimeMetrics.java` medindo por fase (`treeConstruction`, `styleResolution`, `layout`, `reconciliation`, `patchApplication`, `total`) e cardinalidades (`nodesRebuilt`, `nodesCreated`, `nodesChanged`, `nodesMoved`, `nodesRemoved`, `layoutNodesProcessed`, `patchesEmitted`).
   - `KairoRuntime` instrumenta `mount()` e `recompute()`, expondo `lastMetrics()` e `metricsHistory()`.
   - `TreeInspector` expõe `dumpPerformanceReport(KairoRuntime)` e inclui o bloco `Runtime Performance:` em `dumpSnapshot(...)`, verificado em `RuntimeMetricsTest.java`.

