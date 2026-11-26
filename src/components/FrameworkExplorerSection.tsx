import React, { useState } from 'react';
import { Check, Copy, Lock, ShieldCheck } from 'lucide-react';

import diarioEtapasMd from '@/kairo/docs/DIARIO_ETAPAS.md?raw';
import styleDialectCode from '@/kairo/libraries/kairo-ir/src/main/java/dev/kairo/ir/KairoStyleDialect.java?raw';
import styleEngineCode from '@/kairo/libraries/kairo-style/src/main/java/dev/kairo/style/StyleEngine.java?raw';
import stateCode from '@/kairo/libraries/kairo-state/src/main/java/dev/kairo/state/State.java?raw';
import layoutEngineCode from '@/kairo/libraries/kairo-layout/src/main/java/dev/kairo/layout/LayoutEngine.java?raw';
import registryCode from '@/kairo/libraries/kairo-components-api/src/main/java/dev/kairo/components/api/ComponentRegistry.java?raw';
import formCode from '@/kairo/libraries/kairo-components-form/src/main/java/dev/kairo/components/form/FormController.java?raw';
import futureStateCode from '@/kairo/libraries/kairo-async/src/main/java/dev/kairo/async/FutureState.java?raw';
import androidRendererCode from '@/kairo/libraries/kairo-renderer-android/src/main/java/dev/kairo/renderer/android/AndroidRenderer.java?raw';
import kairoCliCode from '@/kairo/tools/kairo-cli/src/main/java/dev/kairo/cli/KairoCli.java?raw';
import archTestCode from '@/kairo/tests/architecture-tests/src/test/java/dev/kairo/arch/KairoArchitectureTest.java?raw';
import loginModelCode from '@/kairo/examples/login-slice/src/main/java/dev/kairo/examples/login/LoginScreenModel.java?raw';
import galleryAppCode from '@/kairo/examples/gallery/src/main/java/dev/kairo/gallery/GalleryApp.java?raw';

interface StageSpec {
  id: number;
  code: string;
  title: string;
  modules: string[];
  platformBoundary: 'neutral' | 'android-only';
  output: string;
  filePath: string;
  sourceCode: string;
}

const STAGES: StageSpec[] = [
  {
    id: 1,
    code: 'Etapa 01 · A',
    title: 'Fundações, Contratos & ArchUnit',
    modules: ['kairo-annotations', 'kairo-core', 'kairo-testkit', 'architecture-tests'],
    platformBoundary: 'neutral',
    output: 'Esqueleto Gradle Java 17 com identidade lógica e bloqueio de imports de plataforma.',
    filePath: 'kairo/tests/architecture-tests/src/test/java/dev/kairo/arch/KairoArchitectureTest.java',
    sourceCode: archTestCode,
  },
  {
    id: 2,
    code: 'Etapa 02 · A.1',
    title: 'Fatia Vertical (Login Ponta a Ponta)',
    modules: ['examples/login-slice', 'kairo-runtime', 'kairo-renderer-android'],
    platformBoundary: 'neutral',
    output: 'Login ponta a ponta XML -> IR -> State -> RenderPatch -> Views/FakeRenderer com coalescência.',
    filePath: 'kairo/examples/login-slice/src/main/java/dev/kairo/examples/login/LoginScreenModel.java',
    sourceCode: loginModelCode,
  },
  {
    id: 3,
    code: 'Etapa 03 · B',
    title: 'Pipeline Declarativo (XML, IR & Dialeto Kairo)',
    modules: ['kairo-ir', 'kairo-xml', 'kairo-css-parser', 'kairo-components-api'],
    platformBoundary: 'neutral',
    output: 'Parser StAX seguro, IRDocument, SemanticIRValidator e KairoStyleDialect (sem CSS web).',
    filePath: 'kairo/libraries/kairo-ir/src/main/java/dev/kairo/ir/KairoStyleDialect.java',
    sourceCode: styleDialectCode,
  },
  {
    id: 4,
    code: 'Etapa 04 · C',
    title: 'Motor de Estilos (Dialeto .kcss & Temas)',
    modules: ['kairo-css-parser', 'kairo-style', 'kairo-runtime', 'kairo-inspector'],
    platformBoundary: 'neutral',
    output: 'Tuplo ID > classe/estado > tipo, estados encadeados, :hover por capacidade, Theme light/dark, tokens com ciclo seguro e StyleTrace.',
    filePath: 'kairo/libraries/kairo-style/src/main/java/dev/kairo/style/StyleEngine.java',
    sourceCode: styleEngineCode,
  },
  {
    id: 5,
    code: 'Etapa 05 · D',
    title: 'Runtime Reativo, Estado & Lifecycle',
    modules: ['kairo-state', 'kairo-events', 'kairo-lifecycle', 'kairo-runtime'],
    platformBoundary: 'neutral',
    output: 'Pilha de rastreamento State/Derived/ObservableList sem glitches, EventDispatcher Capture->Bubble, LifecycleScope hierárquico, diretiva if e MoveChild.',
    filePath: 'kairo/libraries/kairo-state/src/main/java/dev/kairo/state/State.java',
    sourceCode: stateCode,
  },
  {
    id: 6,
    code: 'Etapa 06 · E.0',
    title: 'Layout Engine Neutro no Core',
    modules: ['kairo-layout', 'kairo-runtime', 'kairo-inspector'],
    platformBoundary: 'neutral',
    output: 'MeasurePass, DistributePass (Auto, Fixed, Fill, Percent, wrap, grow/shrink, alinhamento) e OverflowPass 100% fora do Android.',
    filePath: 'kairo/libraries/kairo-layout/src/main/java/dev/kairo/layout/LayoutEngine.java',
    sourceCode: layoutEngineCode,
  },
  {
    id: 7,
    code: 'Etapa 07 · F.1',
    title: 'API de Componentes & Catálogo Base',
    modules: ['kairo-components-api', 'kairo-components-basic', 'kairo-runtime'],
    platformBoundary: 'neutral',
    output: 'Registry explícito sem reflection, SlotProjection, expansão recursiva no runtime, BasicUI DSL e 12 componentes base neutros.',
    filePath: 'kairo/libraries/kairo-components-api/src/main/java/dev/kairo/components/api/ComponentRegistry.java',
    sourceCode: registryCode,
  },
  {
    id: 8,
    code: 'Etapa 08 · F.2',
    title: 'Inputs, Forms, Listas & Navegação',
    modules: ['kairo-components-input', 'kairo-components-form', 'kairo-components-list', 'kairo-navigation'],
    platformBoundary: 'neutral',
    output: 'SelectOption, FormController (ValidateOn + redação de segredos), ListWindow virtualizada (10k+ itens) + KairoListDiff e Router com guards/queryParams/canGoBack.',
    filePath: 'kairo/libraries/kairo-components-form/src/main/java/dev/kairo/components/form/FormController.java',
    sourceCode: formCode,
  },
  {
    id: 9,
    code: 'Etapa 09 · G',
    title: 'Async Limitado & Recursos Locais',
    modules: ['kairo-async', 'kairo-resources'],
    platformBoundary: 'neutral',
    output: 'CancellationToken, KairoSchedulers (daemon/UI neutro), FutureState com guarda de geração e ResourceCache LRU com bloqueio rigoroso de path traversal.',
    filePath: 'kairo/libraries/kairo-async/src/main/java/dev/kairo/async/FutureState.java',
    sourceCode: futureStateCode,
  },
  {
    id: 10,
    code: 'Etapa 10 · E',
    title: 'Renderer Android, C++17 & Platform Adapter',
    modules: ['kairo-renderer-api', 'kairo-renderer-android', 'kairo-platform-android'],
    platformBoundary: 'android-only',
    output: 'Única fronteira Android + JNI C++17 SDF (libkairo_render): KairoRenderEngine, KairoLayoutHostGroup, IME/cursor, acessibilidade, AssetManager e MainLooper.',
    filePath: 'kairo/libraries/kairo-renderer-android/src/main/java/dev/kairo/renderer/android/AndroidRenderer.java',
    sourceCode: androidRendererCode,
  },
  {
    id: 11,
    code: 'Etapa 11 · H',
    title: 'CLI & Inspetor com Mascaramento',
    modules: ['kairo-inspector', 'tools/kairo-cli'],
    platformBoundary: 'neutral',
    output: 'CLI real (create/doctor/inspect/build/run/clean/test), ProjectScaffolder standalone e SecretRedactor mascarando passwords/tokens.',
    filePath: 'kairo/tools/kairo-cli/src/main/java/dev/kairo/cli/KairoCli.java',
    sourceCode: kairoCliCode,
  },
  {
    id: 12,
    code: 'Etapa 12 · I.1',
    title: 'Kairo Gallery & Release v1',
    modules: ['examples/gallery'],
    platformBoundary: 'neutral',
    output: 'App de aceitação exercitando as 12 categorias (10k itens, themes light/dark, async, forms, router) usando apenas APIs públicas da v1.',
    filePath: 'kairo/examples/gallery/src/main/java/dev/kairo/gallery/GalleryApp.java',
    sourceCode: galleryAppCode,
  },
];

export function FrameworkExplorerSection() {
  const [selectedId, setSelectedId] = useState<number>(1);
  const [viewerMode, setViewerMode] = useState<'code' | 'diary'>('code');
  const [copied, setCopied] = useState(false);

  const stage = STAGES.find((s) => s.id === selectedId) || STAGES[0];
  const activeContent = viewerMode === 'diary' ? diarioEtapasMd : stage.sourceCode;
  const activePath = viewerMode === 'diary' ? 'kairo/docs/DIARIO_ETAPAS.md' : stage.filePath;

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(activeContent);
      setCopied(true);
      setTimeout(() => setCopied(false), 1800);
    } catch {
      // Ignore clipboard failures
    }
  };

  return (
    <section className="py-16 lg:py-24">
      <div className="max-w-[1200px] mx-auto px-4 sm:px-6 lg:px-8">
        <div className="max-w-3xl mb-10">
          <p className="text-xs font-medium tracking-wide text-[#4F46E5] dark:text-indigo-400 mb-3">
            Monorepo /kairo · 12 Etapas · 25 Módulos
          </p>
          <h2 className="text-2xl sm:text-3xl font-semibold tracking-tight text-zinc-900 dark:text-zinc-100 text-balance">
            Construção modular em 12 etapas, com fronteira Android isolada.
          </h2>
          <p className="mt-3 text-base text-zinc-600 dark:text-zinc-400 leading-relaxed">
            Todos os ficheiros abaixo foram criados na pasta <code className="text-sm text-zinc-900 dark:text-zinc-200">/kairo</code> dentro do projeto.
            Nenhum módulo de componentes, estado, layout ou estilo importa <code className="text-sm text-zinc-900 dark:text-zinc-200">android.*</code> — apenas{' '}
            <code className="text-sm text-zinc-900 dark:text-zinc-200">kairo-renderer-android</code> e{' '}
            <code className="text-sm text-zinc-900 dark:text-zinc-200">kairo-platform-android</code> tocam no SDK Android.
          </p>
        </div>

        {/* Isolation Verification Strip */}
        <div className="mb-8 p-4 rounded-xl border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-[#111115] flex flex-wrap items-center justify-between gap-4 text-xs sm:text-sm">
          <div className="flex items-center gap-2.5 text-zinc-700 dark:text-zinc-300">
            <ShieldCheck className="w-4 h-4 text-emerald-500 shrink-0" />
            <span>
              <strong>23 módulos neutros</strong> sem imports de plataforma (verificados por ArchUnit)
            </span>
          </div>
          <div className="flex items-center gap-2 text-zinc-500 dark:text-zinc-400 font-mono text-xs">
            <Lock className="w-3.5 h-3.5 text-[#4F46E5] dark:text-indigo-400 shrink-0" />
            <span>Fronteira Android: kairo-renderer-android · kairo-platform-android</span>
          </div>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
          {/* 12 Stages List */}
          <div className="lg:col-span-5 space-y-2">
            {STAGES.map((item) => {
              const active = item.id === selectedId;
              return (
                <button
                  key={item.id}
                  type="button"
                  onClick={() => setSelectedId(item.id)}
                  className={`w-full text-left px-4 py-3 rounded-xl border transition-colors duration-150 cursor-pointer focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#4F46E5] ${
                    active
                      ? 'border-[#4F46E5] bg-[#4F46E5]/[0.07] dark:bg-[#4F46E5]/10'
                      : 'border-zinc-200 dark:border-zinc-800 bg-white dark:bg-[#111115] hover:border-zinc-300 dark:hover:border-zinc-700'
                  }`}
                >
                  <div className="flex items-center justify-between gap-2 text-xs font-mono mb-1">
                    <span className={active ? 'text-[#4F46E5] dark:text-indigo-400 font-medium' : 'text-zinc-500 dark:text-zinc-400'}>
                      {item.code}
                    </span>
                    <span className="text-zinc-500 dark:text-zinc-500">
                      {item.platformBoundary === 'android-only' ? 'Fronteira Android' : '100% Neutro'}
                    </span>
                  </div>
                  <div className="text-sm font-medium text-zinc-900 dark:text-zinc-100">
                    {item.title}
                  </div>
                </button>
              );
            })}
          </div>

          {/* Selected Stage Detail & Live File Viewer */}
          <div className="lg:col-span-7 rounded-xl border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-[#111115] overflow-hidden">
            <div className="p-5 border-b border-zinc-200 dark:border-zinc-800 space-y-2">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <span className="text-xs font-mono text-[#4F46E5] dark:text-indigo-400">
                  {stage.code}
                </span>
                <span className="text-xs font-mono text-zinc-500 dark:text-zinc-400">
                  {stage.modules.join(' · ')}
                </span>
              </div>
              <h3 className="text-lg font-semibold text-zinc-900 dark:text-zinc-100">
                {stage.title}
              </h3>
              <p className="text-sm text-zinc-600 dark:text-zinc-400 leading-relaxed">
                {stage.output}
              </p>
            </div>

            <div className="px-4 py-2.5 border-b border-zinc-200 dark:border-zinc-800 bg-zinc-50 dark:bg-[#0D0D11] flex flex-wrap items-center justify-between gap-3">
              <div className="flex items-center gap-2 min-w-0">
                <div className="inline-flex items-center gap-1 p-0.5 rounded-md border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-[#111115] shrink-0">
                  <button
                    type="button"
                    onClick={() => setViewerMode('code')}
                    className={`px-2 py-1 text-xs font-medium rounded cursor-pointer ${
                      viewerMode === 'code'
                        ? 'bg-[#4F46E5] text-white'
                        : 'text-zinc-600 dark:text-zinc-400 hover:text-zinc-900 dark:hover:text-zinc-100'
                    }`}
                  >
                    Código
                  </button>
                  <button
                    type="button"
                    onClick={() => setViewerMode('diary')}
                    className={`px-2 py-1 text-xs font-medium rounded cursor-pointer ${
                      viewerMode === 'diary'
                        ? 'bg-[#4F46E5] text-white'
                        : 'text-zinc-600 dark:text-zinc-400 hover:text-zinc-900 dark:hover:text-zinc-100'
                    }`}
                  >
                    Diário das Etapas
                  </button>
                </div>
                <span className="text-xs font-mono text-zinc-600 dark:text-zinc-400 truncate">
                  {activePath}
                </span>
              </div>
              <button
                type="button"
                onClick={handleCopy}
                className="inline-flex items-center gap-1.5 px-2.5 py-1 text-xs font-medium text-zinc-600 dark:text-zinc-400 hover:text-zinc-900 dark:hover:text-zinc-100 rounded-md hover:bg-zinc-200/60 dark:hover:bg-zinc-800/80 transition-colors whitespace-nowrap shrink-0 cursor-pointer"
              >
                {copied ? (
                  <>
                    <Check className="w-3.5 h-3.5 text-emerald-500" />
                    <span>Copiado</span>
                  </>
                ) : (
                  <>
                    <Copy className="w-3.5 h-3.5" />
                    <span>Copiar</span>
                  </>
                )}
              </button>
            </div>

            <div className="p-4 max-h-[620px] overflow-auto font-mono text-xs leading-5 text-zinc-800 dark:text-zinc-200 bg-white dark:bg-[#0D0D11]">
              <pre className="whitespace-pre-wrap break-words">
                <code>{activeContent}</code>
              </pre>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
