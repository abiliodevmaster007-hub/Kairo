# Kairo v1 — Contratos dos Componentes & Limitações Oficiais

Este documento define o contrato público estável da v1 para que aplicações construídas com Kairo permaneçam **100% independentes do Android** e portáveis para o backend v2 (Skia/Desktop) sem reescrita.

---

## 1. Catálogo de Componentes Neutros (19 Widgets v1)

| Categoria | Componente | Filhos (`ChildrenPolicy`) | Props Principais (e Obrigatórias `*`) | Eventos Suportados |
| --- | --- | --- | --- | --- |
| **Estrutura** | `Screen` | `MULTIPLE` | `title`, `padding`, `gap` | — |
| **Estrutura** | `Column` | `MULTIPLE` | `gap`, `padding`, `align`, `wrap` | — |
| **Estrutura** | `Row` | `MULTIPLE` | `gap`, `padding`, `align`, `wrap` | — |
| **Estrutura** | `Box` | `MULTIPLE` | `padding`, `align` | `onClick` |
| **Estrutura** | `Stack` | `MULTIPLE` | `padding`, `align` | — |
| **Estrutura** | `Scroll` | `SINGLE` | `axis` (`vertical`), `padding`, `showsIndicator` | `onScroll` |
| **Texto / Ações** | `Text` | `NONE` | `value`, `text`, `maxLines`, `selectable`, `heading` | — |
| **Texto / Ações** | `Button` | `NONE` | `text`, `disabled`, `variant` | `onClick`, `onFocus` |
| **Input** | `Input` | `NONE` | `value`, `label`, `placeholder`, `type`, `maxLength`, `disabled`, `error` | `onInput`, `onFocus`, `onSubmit` |
| **Input** | `TextArea` | `NONE` | `value`, `label`, `placeholder`, `rows`, `maxLength`, `disabled`, `error` | `onInput`, `onFocus` |
| **Input** | `Checkbox` | `NONE` | `checked`, `label`, `disabled`, `error` | `onChange`, `onFocus` |
| **Input** | `Switch` | `NONE` | `checked`, `label`, `disabled` | `onChange`, `onFocus` |
| **Input** | `Select` | `NONE` | `value`, `options` (`value:label\|...`), `placeholder`, `disabled`, `error` | `onChange`, `onFocus` |
| **Media / Superfície** | `Image` | `NONE` | `src*`, `alt*`, `fit` (`contain` / `cover`) | — |
| **Media / Superfície** | `Card` | `MULTIPLE` | `padding`, `elevation`, `variant`, `disabled` | `onClick`, `onFocus` |
| **Media / Superfície** | `Divider` | `NONE` | `orientation` (`horizontal`), `thickness` (`1`) | — |
| **Feedback** | `Progress` | `NONE` | `value`, `max` (`1.0`), `indeterminate` (`true`) | — |
| **Formulários** | `Form` | `MULTIPLE` | `validateOn` (`submit`/`blur`/`change`), `disabled` | `onSubmit` |
| **Formulários** | `FormField` | `SINGLE` | `name*`, `label`, `helperText`, `sensitive` (`false`) | — |
| **Listas** | `List` | `TEMPLATE` | `items*`, `key` (`StableKey`), `estimatedItemHeight`, `overscan`, `emptyMessage` | `onScroll` |
| **Navegação** | `Routes` / `Route` | `MULTIPLE` / `NONE` | `initialPath` / `path*`, `screen*` | — |

### Props Universais
Todos os componentes aceitam: `id`, `class`, `style`, `key`, `slot`, `if`, `when`, `visible` e `accessibilityLabel`.

---

## 2. Dialeto de Estilos Kairo (`.kcss`)
O Kairo **não usa CSS da web**:
- **Seletores suportados:** tipo (`Button`), classe (`.primary`), ID (`#submit`), descendente simples (`Card Text`), lista por vírgula e pseudo-estados (`:pressed`, `:focused`, `:disabled`, `:selected`, `:checked`, `:error`, `:hover` condicionado por capacidade).
- **Especificidade exata:** tuplo `(ids, classesAndStates, types)` sem *overflow* decimal.
- **Rejeição explícita em tempo de parse:** `float`, `position: fixed`, `z-index`, `display: grid`, `calc()`, `!important`, `@media`, `@keyframes`, seletores `>`, `+`, `~`, `[attr]`, `::pseudo` e `*`.
- **Espaçamento nativo:** `margin` e `padding` são estritamente aditivos (**sem *margin collapsing* da web**). Herança restrita à tipografia (`color`, `font-size`, `font-weight`, `font-family`, `text-align`, `line-height`).

---

## 3. Contratos de Segurança e Fronteiras
1. **Isolamento de Plataforma:** Apenas `kairo-renderer-android` e `kairo-platform-android` importam `android.*` / `androidx.*` / JNI C++. Verificado por **ArchUnit**.
2. **Sem `eval` e sem Reflection:** Expressões usam `BindingExpressionValidator` + `ExposedObject`; componentes usam `ComponentRegistry` explícito.
3. **XML Seguro:** XXE, DTD externo e esquemas externos desativados por omissão no StAX.
4. **Recursos Locais:** `ResourcePathValidator` rejeita `..`, sequências `%2e`/`%5c`, prefixos Windows e esquemas remotos (`http://`, `https://`, `file://`).
5. **Mascaramento:** `TreeInspector` e `FormController.safeSnapshot()` substituem automaticamente `password`, `token`, `secret`, `pin`, `cvv` e campos `sensitive=true` por `••••••`.

---

## 4. Limitações Deliberadas da v1 (Adiados para v2)
- Sem renderizador Desktop/iOS nem *bindings* Skia/Skija/LWJGL em produção (apenas investigação futura).
- Sem imagens remotas via URL (usar `FutureState` + cache local ou `ResourceManager` local).
- Sem `RichText` ou `Dialog` nativos multi-plataforma na v1.
