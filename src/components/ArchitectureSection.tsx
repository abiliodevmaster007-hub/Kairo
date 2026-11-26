import React, { useState } from 'react';
import { ArrowRight, ExternalLink } from 'lucide-react';

interface PipelineStep {
  id: number;
  title: string;
  subtitle: string;
  description: string;
  artifactTitle: string;
  artifactCode: string;
}

const PIPELINE_STEPS: PipelineStep[] = [
  {
    id: 1,
    title: '01. XML + CSS',
    subtitle: 'Declaração legível',
    description:
      'A interface é descrita em XML limpo e estilizada com propriedades CSS padronizadas, sem mistura de código imperativo no layout.',
    artifactTitle: 'Entrada do ficheiro',
    artifactCode: `<Button id="submit" class="primary-btn">\n  {state.loading ? "A autenticar..." : "Entrar"}\n</Button>`,
  },
  {
    id: 2,
    title: '02. Árvore lógica',
    subtitle: 'Modelo em Java puro',
    description:
      'O core do Kairo compila o XML e resolve os estilos num grafo leve de nós lógicos em memória, totalmente independente do SDK Android.',
    artifactTitle: 'Nó resultante',
    artifactCode: `LogicalNode(\n  type = NodeType.BUTTON,\n  id = "submit",\n  props = { text: "A autenticar...", bg: "#4F46E5" }\n)`,
  },
  {
    id: 3,
    title: '03. Patches',
    subtitle: 'Diff mínimo de estado',
    description:
      'Quando LoginState muda em Java, o algoritmo de diff compara apenas os nós afetados e emite instruções atómicas de alteração.',
    artifactTitle: 'Delta calculado',
    artifactCode: `Patch.SetProp(\n  targetId = "submit",\n  property = "text",\n  value = "A autenticar..."\n)`,
  },
  {
    id: 4,
    title: '04. Widgets nativos',
    subtitle: 'Views reais do Android',
    description:
      'O adaptador Android aplica o patch diretamente sobre a instância de android.widget.Button já montada, sem reconstruir o ecrã inteiro.',
    artifactTitle: 'Execução na UI thread',
    artifactCode: `android.widget.Button btn = registry.get("submit");\nbtn.setText("A autenticar...");`,
  },
];

export function ArchitectureSection() {
  const [selectedStep, setSelectedStep] = useState<number>(1);
  const activeStep = PIPELINE_STEPS.find((s) => s.id === selectedStep) || PIPELINE_STEPS[0];

  return (
    <>
      {/* Section 2: O Problema */}
      <section
        id="problema"
        className="py-20 lg:py-24 border-t border-zinc-200 dark:border-zinc-800/80"
      >
        <div className="max-w-[1200px] mx-auto px-4 sm:px-6 lg:px-8">
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-10 lg:gap-12 items-start">
            <div className="lg:col-span-5">
              <p className="text-xs font-medium tracking-wide text-[#4F46E5] dark:text-indigo-400 mb-3">
                02. O problema
              </p>
              <h2 className="text-2xl sm:text-3xl font-semibold tracking-tight text-zinc-900 dark:text-zinc-100 text-balance">
                As equipas Java ficaram sem uma opção declarativa no Android.
              </h2>
            </div>

            <div className="lg:col-span-7 space-y-5 text-base text-zinc-600 dark:text-zinc-400 leading-relaxed">
              <p>
                O Jetpack Compose trouxe um modelo declarativo moderno ao Android, mas exige Kotlin
                e um plugin de compilador específico — não tem suporte para código escrito em Java.
              </p>
              <p>
                Ao mesmo tempo, a Google colocou o toolkit clássico de Android Views em modo de
                manutenção. Para equipas com bases de código Java consolidadas, bibliotecas internas
                ou programadores que não querem migrar para Kotlin, a escolha ficou limitada a manter
                código imperativo verboso com <code className="text-sm text-zinc-900 dark:text-zinc-200">findViewById</code> e{' '}
                <code className="text-sm text-zinc-900 dark:text-zinc-200">ViewBinding</code>.
              </p>
              <p>
                O Kairo existe para preencher este vazio com pragmatismo: dar a equipas Java um fluxo
                declarativo previsível sobre os widgets nativos que o Android já executa bem.
              </p>

              <div className="pt-3 flex flex-wrap items-center gap-x-6 gap-y-2 text-xs text-zinc-500 dark:text-zinc-400 border-t border-zinc-200 dark:border-zinc-800/80">
                <span>Referências oficiais:</span>
                <a
                  href="https://developer.android.com/kotlin/first"
                  target="_blank"
                  rel="noreferrer"
                  className="inline-flex items-center gap-1 hover:text-zinc-900 dark:hover:text-zinc-200 underline underline-offset-4 transition-colors"
                >
                  <span>Android Kotlin-first</span>
                  <ExternalLink className="w-3 h-3" />
                </a>
                <a
                  href="https://developer.android.com/jetpack/compose/why-adopt"
                  target="_blank"
                  rel="noreferrer"
                  className="inline-flex items-center gap-1 hover:text-zinc-900 dark:hover:text-zinc-200 underline underline-offset-4 transition-colors"
                >
                  <span>Why adopt Compose</span>
                  <ExternalLink className="w-3 h-3" />
                </a>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* Section 3: Como funciona */}
      <section
        id="como-funciona"
        className="py-20 lg:py-24 border-t border-zinc-200 dark:border-zinc-800/80"
      >
        <div className="max-w-[1200px] mx-auto px-4 sm:px-6 lg:px-8">
          <div className="max-w-2xl mb-12">
            <p className="text-xs font-medium tracking-wide text-[#4F46E5] dark:text-indigo-400 mb-3">
              03. Como funciona
            </p>
            <h2 className="text-2xl sm:text-3xl font-semibold tracking-tight text-zinc-900 dark:text-zinc-100 text-balance">
              Quatro passos claros do ficheiro XML ao widget nativo.
            </h2>
            <p className="mt-3 text-base text-zinc-600 dark:text-zinc-400">
              Selecione qualquer etapa do pipeline para inspecionar os dados produzidos pelo motor.
            </p>
          </div>

          {/* 4-step interactive pipeline diagram */}
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            {PIPELINE_STEPS.map((step, idx) => {
              const isSelected = step.id === selectedStep;
              return (
                <div key={step.id} className="relative flex items-stretch">
                  <button
                    type="button"
                    onClick={() => setSelectedStep(step.id)}
                    aria-pressed={isSelected}
                    className={`w-full text-left p-5 rounded-xl border transition-colors duration-150 cursor-pointer focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#4F46E5] ${
                      isSelected
                        ? 'border-[#4F46E5] bg-[#4F46E5]/[0.06] dark:bg-[#4F46E5]/10'
                        : 'border-zinc-200 dark:border-zinc-800 bg-white dark:bg-[#111115] hover:border-zinc-300 dark:hover:border-zinc-700'
                    }`}
                  >
                    <div className="text-xs font-mono text-[#4F46E5] dark:text-indigo-400 mb-2">
                      {step.subtitle}
                    </div>
                    <div className="text-base font-semibold text-zinc-900 dark:text-zinc-100">
                      {step.title}
                    </div>
                    <p className="mt-2 text-sm text-zinc-600 dark:text-zinc-400 leading-relaxed">
                      {step.description}
                    </p>
                  </button>

                  {idx < PIPELINE_STEPS.length - 1 && (
                    <div
                      className="hidden lg:flex items-center justify-center -mr-3 ml-1 z-10 text-zinc-400 dark:text-zinc-600 pointer-events-none"
                      aria-hidden="true"
                    >
                      <ArrowRight className="w-4 h-4" />
                    </div>
                  )}
                </div>
              );
            })}
          </div>

          {/* Active Stage Output Inspector */}
          <div className="mt-6 rounded-xl border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-[#111115] p-5 sm:p-6">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 mb-3 pb-3 border-b border-zinc-200 dark:border-zinc-800">
              <div className="text-sm font-medium text-zinc-900 dark:text-zinc-100">
                {activeStep.title} — {activeStep.artifactTitle}
              </div>
              <div className="text-xs text-zinc-500 dark:text-zinc-400 font-mono">
                Etapa {activeStep.id} de 4
              </div>
            </div>
            <pre className="font-mono text-xs sm:text-[13px] leading-6 text-zinc-800 dark:text-zinc-200 overflow-x-auto">
              <code>{activeStep.artifactCode}</code>
            </pre>
          </div>
        </div>
      </section>
    </>
  );
}
