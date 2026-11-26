import React from 'react';
import { Check, Minus } from 'lucide-react';

const WHY_CARDS = [
  {
    number: '01',
    title: 'Java puro',
    body: 'Sem compiladores externos, sem anotação mágica nem runtime Kotlin obrigatório. Funciona no seu projeto Gradle com Java padrão.',
  },
  {
    number: '02',
    title: 'Widgets nativos reais',
    body: 'Sem motor gráfico paralelo nem WebView. O Kairo instancia LinearLayout, TextView, EditText e Button reais do Android, preservando acessibilidade e input nativo.',
  },
  {
    number: '03',
    title: 'Reativo e incremental',
    body: 'O estado vive numa estrutura imutável em Java. Quando algo muda, o algoritmo de diff calcula apenas as propriedades alteradas e aplica patches pontuais.',
  },
  {
    number: '04',
    title: 'Core independente de plataforma',
    body: 'O parser XML, o resolvedor CSS e a árvore lógica correm na JVM sem dependências de android.*. Pode testar ecrãs e patches em JUnit em milissegundos.',
  },
];

const AUDIENCE_FOR = [
  'Equipas com equipas ou bibliotecas Java que querem construir apps Android sem reescrever tudo em Kotlin.',
  'Aplicações empresariais, de campo, logística, retalho ou ferramentas internas onde previsibilidade e manutenção pesam mais que efeitos visuais exóticos.',
  'Programadores que valorizam separação nítida: XML para estrutura, CSS para estilo, Java para regras e estado.',
];

const AUDIENCE_NOT_FOR = [
  'Quem já trabalha confortavelmente em Kotlin e Jetpack Compose — não há razão para trocar.',
  'Jogos, motores gráficos customizados ou interfaces baseadas em desenho livre por canvas.',
  'Projetos que precisem hoje de suporte multiplataforma pronto para produção em iOS ou desktop.',
];

const ROADMAP_PHASES = [
  {
    stage: 'v0.1 · Agora',
    title: 'Early preview',
    meta: 'Android primeiro · Core funcional',
    items: [
      'Parser XML e árvore lógica em Java puro',
      'Subset CSS essencial (flex, padding, cores, tipografia)',
      'Algoritmo de diff e patches incrementais',
      'Widgets Android base: Column, Row, Text, Input, Button',
    ],
    active: true,
  },
  {
    stage: 'v1.0 · Próximo',
    title: 'Base de produção Android',
    meta: 'Componentes, listas e CLI',
    items: [
      'Biblioteca alargada de componentes e estados de foco/toque',
      'Seletores CSS compostos, variáveis e temas claro/escuro',
      'Listas virtualizadas (RecyclerView) com diff por chave',
      'Navegação declarativa entre ecrãs e CLI de scaffolding',
    ],
    active: false,
  },
  {
    stage: 'Em investigação',
    title: 'Além do Android',
    meta: 'Sem data prometida',
    items: [
      'Adaptador iOS (UIKit) sobre o mesmo Core em Java',
      'Protótipo Desktop (Swing / JavaFX) para pré-visualização rápida',
      'Hot reload de XML e CSS sem recompilar o APK',
    ],
    active: false,
  },
];

export function WhyAndAudienceSection() {
  return (
    <>
      {/* Section 4: Porquê o Kairo (4 cartões) */}
      <section
        id="porque"
        className="py-20 lg:py-24 border-t border-zinc-200 dark:border-zinc-800/80"
      >
        <div className="max-w-[1200px] mx-auto px-4 sm:px-6 lg:px-8">
          <div className="max-w-2xl mb-12">
            <p className="text-xs font-medium tracking-wide text-[#4F46E5] dark:text-indigo-400 mb-3">
              04. Porquê o Kairo
            </p>
            <h2 className="text-2xl sm:text-3xl font-semibold tracking-tight text-zinc-900 dark:text-zinc-100 text-balance">
              Construído para equipas que já têm Java como alicerce.
            </h2>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            {WHY_CARDS.map((card) => (
              <article
                key={card.number}
                className="p-6 sm:p-7 rounded-xl border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-[#111115]"
              >
                <div className="text-xs font-mono text-zinc-500 dark:text-zinc-400 tabular-nums mb-3">
                  {card.number}
                </div>
                <h3 className="text-lg font-semibold text-zinc-900 dark:text-zinc-100">
                  {card.title}
                </h3>
                <p className="mt-2.5 text-sm sm:text-[15px] text-zinc-600 dark:text-zinc-400 leading-relaxed">
                  {card.body}
                </p>
              </article>
            ))}
          </div>
        </div>
      </section>

      {/* Section 5: Para quem é e para quem não é */}
      <section
        id="para-quem"
        className="py-20 lg:py-24 border-t border-zinc-200 dark:border-zinc-800/80"
      >
        <div className="max-w-[1200px] mx-auto px-4 sm:px-6 lg:px-8">
          <div className="max-w-2xl mb-12">
            <p className="text-xs font-medium tracking-wide text-[#4F46E5] dark:text-indigo-400 mb-3">
              05. Adequação honesta
            </p>
            <h2 className="text-2xl sm:text-3xl font-semibold tracking-tight text-zinc-900 dark:text-zinc-100 text-balance">
              Para quem é e para quem não é.
            </h2>
            <p className="mt-3 text-base text-zinc-600 dark:text-zinc-400">
              Nenhuma ferramenta serve todos os projetos. Preferimos deixar claro onde o Kairo faz
              sentido.
            </p>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
            <div className="p-6 sm:p-8 rounded-xl border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-[#111115]">
              <h3 className="text-lg font-semibold text-zinc-900 dark:text-zinc-100 pb-4 border-b border-zinc-200 dark:border-zinc-800">
                Faz sentido se
              </h3>
              <ul className="mt-5 space-y-4">
                {AUDIENCE_FOR.map((item, idx) => (
                  <li key={idx} className="flex items-start gap-3 text-sm sm:text-[15px] text-zinc-700 dark:text-zinc-300 leading-relaxed">
                    <Check className="w-4 h-4 mt-1 shrink-0 text-[#4F46E5] dark:text-indigo-400" />
                    <span>{item}</span>
                  </li>
                ))}
              </ul>
            </div>

            <div className="p-6 sm:p-8 rounded-xl border border-zinc-200 dark:border-zinc-800 bg-zinc-100/50 dark:bg-[#0D0D11]">
              <h3 className="text-lg font-semibold text-zinc-900 dark:text-zinc-100 pb-4 border-b border-zinc-200 dark:border-zinc-800">
                Não é para si se
              </h3>
              <ul className="mt-5 space-y-4">
                {AUDIENCE_NOT_FOR.map((item, idx) => (
                  <li key={idx} className="flex items-start gap-3 text-sm sm:text-[15px] text-zinc-600 dark:text-zinc-400 leading-relaxed">
                    <Minus className="w-4 h-4 mt-1 shrink-0 text-zinc-400 dark:text-zinc-500" />
                    <span>{item}</span>
                  </li>
                ))}
              </ul>
            </div>
          </div>
        </div>
      </section>

      {/* Section 6: Roadmap público */}
      <section
        id="roadmap"
        className="py-20 lg:py-24 border-t border-zinc-200 dark:border-zinc-800/80"
      >
        <div className="max-w-[1200px] mx-auto px-4 sm:px-6 lg:px-8">
          <div className="max-w-2xl mb-12">
            <p className="text-xs font-medium tracking-wide text-[#4F46E5] dark:text-indigo-400 mb-3">
              06. Roadmap público
            </p>
            <h2 className="text-2xl sm:text-3xl font-semibold tracking-tight text-zinc-900 dark:text-zinc-100 text-balance">
              O que já existe hoje e o que vem a seguir.
            </h2>
            <p className="mt-3 text-base text-zinc-600 dark:text-zinc-400">
              Android é o foco exclusivo até a v1.0 estar sólida. iOS e desktop são frentes de
              investigação, não promessas com data fechada.
            </p>
          </div>

          <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
            {ROADMAP_PHASES.map((phase) => (
              <div
                key={phase.stage}
                className={`p-6 rounded-xl border ${
                  phase.active
                    ? 'border-[#4F46E5]/60 bg-white dark:bg-[#111115]'
                    : 'border-zinc-200 dark:border-zinc-800 bg-white/60 dark:bg-[#111115]/60'
                }`}
              >
                <div className="flex items-center justify-between gap-2 text-xs font-mono mb-2">
                  <span
                    className={
                      phase.active
                        ? 'text-[#4F46E5] dark:text-indigo-400 font-medium'
                        : 'text-zinc-500 dark:text-zinc-400'
                    }
                  >
                    {phase.stage}
                  </span>
                  <span className="text-zinc-500 dark:text-zinc-500">{phase.meta}</span>
                </div>
                <h3 className="text-lg font-semibold text-zinc-900 dark:text-zinc-100 pb-4 border-b border-zinc-200 dark:border-zinc-800">
                  {phase.title}
                </h3>
                <ul className="mt-4 space-y-3 text-sm text-zinc-600 dark:text-zinc-400 leading-relaxed">
                  {phase.items.map((item, idx) => (
                    <li key={idx} className="flex items-start gap-2.5">
                      <span
                        className="mt-2 w-1 h-1 rounded-full bg-zinc-400 dark:bg-zinc-600 shrink-0"
                        aria-hidden="true"
                      />
                      <span>{item}</span>
                    </li>
                  ))}
                </ul>
              </div>
            ))}
          </div>
        </div>
      </section>
    </>
  );
}
