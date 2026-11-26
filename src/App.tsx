/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useEffect, useRef, useState } from 'react';
import { ArrowDown, CheckCircle2, Moon, Sun, X } from 'lucide-react';
import { Header } from './components/Header';
import { KairoMark } from './components/KairoMark';
import { CodeDemoSection } from './components/CodeDemoSection';
import { ArchitectureSection } from './components/ArchitectureSection';
import { WhyAndAudienceSection } from './components/WhyAndAudienceSection';
import { WaitlistSection, type WaitlistSubmission } from './components/WaitlistSection';
import { FrameworkExplorerSection } from './components/FrameworkExplorerSection';
import { GalleryPreviewSection } from './components/GalleryPreviewSection';

type AppView = 'overview' | 'framework' | 'gallery';

const STORAGE_KEY = 'kairo_waitlist_v01';
const THEME_KEY = 'kairo-theme';

export default function App() {
  const [theme, setTheme] = useState<'dark' | 'light'>(() => {
    try {
      return localStorage.getItem(THEME_KEY) === 'light' ? 'light' : 'dark';
    } catch {
      return 'dark';
    }
  });

  const [activeView, setActiveView] = useState<AppView>('overview');
  const [heroFormOpen, setHeroFormOpen] = useState(false);
  const [heroEmail, setHeroEmail] = useState('');
  const [heroProject, setHeroProject] = useState('');
  const [heroError, setHeroError] = useState('');

  const [submission, setSubmission] = useState<WaitlistSubmission | null>(() => {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      return raw ? (JSON.parse(raw) as WaitlistSubmission) : null;
    } catch {
      return null;
    }
  });

  const waitlistEmailRef = useRef<HTMLInputElement | null>(null);
  const heroEmailRef = useRef<HTMLInputElement | null>(null);

  useEffect(() => {
    const root = document.documentElement;
    if (theme === 'dark') {
      root.classList.add('dark');
    } else {
      root.classList.remove('dark');
    }
    try {
      localStorage.setItem(THEME_KEY, theme);
    } catch {
      // Ignore storage failures
    }
  }, [theme]);

  const toggleTheme = () => {
    setTheme((prev) => (prev === 'dark' ? 'light' : 'dark'));
  };

  const handleSaveSubmission = (data: WaitlistSubmission) => {
    setSubmission(data);
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(data));
    } catch {
      // Ignore storage failures
    }
  };

  const handleResetSubmission = () => {
    setSubmission(null);
    try {
      localStorage.removeItem(STORAGE_KEY);
    } catch {
      // Ignore storage failures
    }
  };

  const scrollToWaitlist = () => {
    setActiveView('overview');
    setTimeout(() => {
      const el = document.getElementById('lista-de-espera');
      if (el) {
        el.scrollIntoView({ behavior: 'smooth' });
        setTimeout(() => {
          waitlistEmailRef.current?.focus();
        }, 350);
      }
    }, 50);
  };

  const handleHeroWaitlistTrigger = () => {
    setHeroFormOpen(true);
    setTimeout(() => {
      heroEmailRef.current?.focus();
    }, 50);
  };

  const handleHeroSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const trimmed = heroEmail.trim();
    const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!emailPattern.test(trimmed)) {
      setHeroError('Introduza um endereço de email válido.');
      return;
    }
    setHeroError('');
    handleSaveSubmission({
      email: trimmed,
      project: heroProject.trim(),
      blocker: submission?.blocker ?? '',
      blockerDetails: submission?.blockerDetails ?? '',
      submittedAt: new Date().toISOString(),
    });
  };

  return (
    <div className="min-h-screen flex flex-col bg-zinc-50 text-zinc-900 dark:bg-[#09090B] dark:text-zinc-100 transition-colors duration-150">
      <Header onWaitlistClick={scrollToWaitlist} onNavClick={() => setActiveView('overview')} />

      <main className="flex-1">
        {/* Hero Section */}
        <section className="pt-12 pb-20 lg:pt-20 lg:pb-28">
          <div className="max-w-[1200px] mx-auto px-4 sm:px-6 lg:px-8">
            {/* Disambiguation + Honesty Seal + Theme Switch */}
            <div className="flex flex-wrap items-center justify-between gap-4 pb-8 border-b border-zinc-200 dark:border-zinc-800/80">
              <div className="flex flex-wrap items-center gap-x-2.5 gap-y-1 text-xs sm:text-sm text-zinc-600 dark:text-zinc-400">
                <span className="font-medium text-zinc-900 dark:text-zinc-200">
                  UI declarativa para Java
                </span>
                <span aria-hidden="true">·</span>
                <span>Early preview v0.1. Android primeiro; iOS em investigação.</span>
              </div>

              <div className="flex flex-wrap items-center gap-3">
                <div
                  role="tablist"
                  aria-label="Modo de visualização"
                  className="inline-flex items-center gap-1 p-1 rounded-lg border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-[#111115]"
                >
                  {(
                    [
                      { id: 'overview', label: 'Visão Geral' },
                      { id: 'framework', label: 'Framework (12 Etapas)' },
                      { id: 'gallery', label: 'Kairo Gallery' },
                    ] as const
                  ).map((tab) => (
                    <button
                      key={tab.id}
                      type="button"
                      role="tab"
                      aria-selected={activeView === tab.id}
                      onClick={() => setActiveView(tab.id)}
                      className={`px-3 py-1 text-xs font-medium rounded-md transition-colors whitespace-nowrap shrink-0 cursor-pointer ${
                        activeView === tab.id
                          ? 'bg-[#4F46E5] text-white'
                          : 'text-zinc-600 dark:text-zinc-400 hover:text-zinc-900 dark:hover:text-zinc-100'
                      }`}
                    >
                      {tab.label}
                    </button>
                  ))}
                </div>

                <button
                  type="button"
                  onClick={toggleTheme}
                  aria-label={theme === 'dark' ? 'Mudar para modo claro' : 'Mudar para modo escuro'}
                  className="inline-flex items-center gap-2 px-3 py-1.5 text-xs font-medium rounded-lg border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-[#111115] text-zinc-700 dark:text-zinc-300 hover:border-zinc-300 dark:hover:border-zinc-700 transition-colors whitespace-nowrap shrink-0 cursor-pointer focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#4F46E5]"
                >
                  {theme === 'dark' ? (
                    <>
                      <Sun className="w-3.5 h-3.5" />
                      <span>Modo claro</span>
                    </>
                  ) : (
                    <>
                      <Moon className="w-3.5 h-3.5" />
                      <span>Modo escuro</span>
                    </>
                  )}
                </button>
              </div>
            </div>

            {/* Hero Core Copy */}
            <div className="pt-12 lg:pt-16 max-w-4xl">
              <h1 className="text-[clamp(2.25rem,4.5vw,3.75rem)] font-semibold tracking-[-0.025em] leading-[1.08] text-zinc-900 dark:text-zinc-50 text-balance">
                UI declarativa e nativa para Android, em Java.
              </h1>

              <p className="mt-6 text-lg sm:text-xl text-zinc-600 dark:text-zinc-400 leading-relaxed max-w-2xl">
                XML para a estrutura, CSS para o estilo, Java para a lógica. As atualizações são
                incrementais.
              </p>

              {/* Hero Actions */}
              <div className="mt-9 flex flex-wrap items-center gap-4">
                <button
                  type="button"
                  onClick={handleHeroWaitlistTrigger}
                  className="inline-flex items-center justify-center px-5 py-3 text-sm font-medium text-white bg-[#4F46E5] hover:bg-[#4338CA] rounded-lg transition-colors duration-150 whitespace-nowrap shrink-0 cursor-pointer focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#4F46E5] focus-visible:ring-offset-2 focus-visible:ring-offset-zinc-50 dark:focus-visible:ring-offset-[#09090B]"
                >
                  Entrar na lista de espera
                </button>

                <a
                  href="#demo"
                  className="inline-flex items-center gap-2 px-5 py-3 text-sm font-medium text-zinc-800 dark:text-zinc-200 border border-zinc-300 dark:border-zinc-800 bg-white dark:bg-[#111115] hover:border-zinc-400 dark:hover:border-zinc-700 rounded-lg transition-colors duration-150 whitespace-nowrap shrink-0 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#4F46E5]"
                >
                  <span>Ver o código</span>
                  <ArrowDown className="w-4 h-4 text-zinc-500" />
                </a>
              </div>

              {/* Inline Hero Waitlist Capture Drawer */}
              {heroFormOpen && (
                <div className="mt-8 p-5 sm:p-6 rounded-xl border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-[#111115] max-w-2xl">
                  {submission ? (
                    <div className="flex items-start justify-between gap-4">
                      <div className="flex items-start gap-3">
                        <CheckCircle2 className="w-5 h-5 text-emerald-500 shrink-0 mt-0.5" />
                        <div>
                          <p className="text-sm font-medium text-zinc-900 dark:text-zinc-100">
                            Email registado: <span className="font-mono">{submission.email}</span>
                          </p>
                          <p className="mt-1 text-xs text-zinc-600 dark:text-zinc-400">
                            Se quiser partilhar o que o travou em Android com Java, pode completar a
                            pergunta rápida mais abaixo.
                          </p>
                        </div>
                      </div>
                      <button
                        type="button"
                        onClick={() => setHeroFormOpen(false)}
                        aria-label="Fechar caixa de inscrição"
                        className="text-zinc-400 hover:text-zinc-700 dark:hover:text-zinc-200 cursor-pointer p-1"
                      >
                        <X className="w-4 h-4" />
                      </button>
                    </div>
                  ) : (
                    <form onSubmit={handleHeroSubmit} noValidate className="space-y-4">
                      <div className="flex items-center justify-between gap-4">
                        <span className="text-sm font-medium text-zinc-900 dark:text-zinc-100">
                          Acesso antecipado à v0.1
                        </span>
                        <button
                          type="button"
                          onClick={() => setHeroFormOpen(false)}
                          aria-label="Fechar formulário"
                          className="text-zinc-400 hover:text-zinc-700 dark:hover:text-zinc-200 cursor-pointer p-1"
                        >
                          <X className="w-4 h-4" />
                        </button>
                      </div>

                      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                        <div>
                          <label htmlFor="hero-email" className="sr-only">
                            Email
                          </label>
                          <input
                            ref={heroEmailRef}
                            id="hero-email"
                            type="email"
                            required
                            value={heroEmail}
                            onChange={(e) => {
                              setHeroEmail(e.target.value);
                              if (heroError) setHeroError('');
                            }}
                            placeholder="O seu email"
                            className="w-full h-10 px-3.5 rounded-lg border border-zinc-300 dark:border-zinc-800 bg-zinc-50 dark:bg-[#09090B] text-sm text-zinc-900 dark:text-zinc-100 placeholder:text-zinc-400 dark:placeholder:text-zinc-600 focus:outline-none focus:border-[#4F46E5]"
                          />
                        </div>

                        <div>
                          <label htmlFor="hero-project" className="sr-only">
                            O que constrói em Java?
                          </label>
                          <input
                            id="hero-project"
                            type="text"
                            value={heroProject}
                            onChange={(e) => setHeroProject(e.target.value)}
                            placeholder="O que constrói em Java? (opcional)"
                            className="w-full h-10 px-3.5 rounded-lg border border-zinc-300 dark:border-zinc-800 bg-zinc-50 dark:bg-[#09090B] text-sm text-zinc-900 dark:text-zinc-100 placeholder:text-zinc-400 dark:placeholder:text-zinc-600 focus:outline-none focus:border-[#4F46E5]"
                          />
                        </div>
                      </div>

                      {heroError && (
                        <p role="alert" className="text-xs text-rose-600 dark:text-rose-400">
                          {heroError}
                        </p>
                      )}

                      <div className="flex items-center justify-between gap-4">
                        <button
                          type="submit"
                          className="px-4 py-2 text-xs sm:text-sm font-medium text-white bg-[#4F46E5] hover:bg-[#4338CA] rounded-lg transition-colors cursor-pointer"
                        >
                          Confirmar inscrição
                        </button>
                        <button
                          type="button"
                          onClick={() => {
                            setHeroFormOpen(false);
                            scrollToWaitlist();
                          }}
                          className="text-xs text-zinc-500 hover:text-zinc-900 dark:text-zinc-400 dark:hover:text-zinc-200 underline underline-offset-4 cursor-pointer"
                        >
                          Responder também à pergunta de validação
                        </button>
                      </div>
                    </form>
                  )}
                </div>
              )}
            </div>
          </div>
        </section>

        {activeView === 'overview' && (
          <>
            {/* Section 1: Demo lado a lado */}
            <CodeDemoSection />

            {/* Section 2 & 3: O problema + Como funciona */}
            <ArchitectureSection />

            {/* Section 4, 5 & 6: Porquê o Kairo + Para quem é / não é + Roadmap público */}
            <WhyAndAudienceSection />

            {/* Section 7: Lista de espera + pergunta curta */}
            <WaitlistSection
              submission={submission}
              onSubmitWaitlist={handleSaveSubmission}
              onResetWaitlist={handleResetSubmission}
              emailInputRef={waitlistEmailRef}
            />
          </>
        )}

        {activeView === 'framework' && <FrameworkExplorerSection />}
        {activeView === 'gallery' && <GalleryPreviewSection />}
      </main>

      {/* Section 8: Rodapé */}
      <footer className="border-t border-zinc-200 dark:border-zinc-800/80 py-12">
        <div className="max-w-[1200px] mx-auto px-4 sm:px-6 lg:px-8 flex flex-col md:flex-row items-start md:items-center justify-between gap-8">
          <div className="space-y-1.5">
            <div className="inline-flex items-center gap-2 text-base font-semibold text-zinc-900 dark:text-zinc-100">
              <KairoMark className="w-4 h-4" />
              <span>Kairo</span>
            </div>
            <p className="text-xs text-zinc-500 dark:text-zinc-400">
              UI declarativa para Java · Early preview v0.1
            </p>
          </div>

          <div className="flex flex-wrap items-center gap-x-6 gap-y-3 text-sm text-zinc-600 dark:text-zinc-400">
            <a
              href="https://github.com"
              target="_blank"
              rel="noreferrer"
              className="hover:text-zinc-900 dark:hover:text-zinc-100 transition-colors"
            >
              GitHub
            </a>
            <span className="text-zinc-500 dark:text-zinc-500">
              Documentação <span className="text-xs">(em breve)</span>
            </span>
            <a
              href="mailto:kairo-java@example.org"
              className="hover:text-zinc-900 dark:hover:text-zinc-100 transition-colors"
            >
              Contacto
            </a>
            <span className="text-zinc-500 dark:text-zinc-500">Licença Apache-2.0</span>
            <button
              type="button"
              onClick={toggleTheme}
              className="text-xs font-medium text-zinc-600 dark:text-zinc-400 hover:text-zinc-900 dark:hover:text-zinc-100 underline underline-offset-4 cursor-pointer"
            >
              {theme === 'dark' ? 'Modo claro' : 'Modo escuro'}
            </button>
          </div>
        </div>
      </footer>
    </div>
  );
}
