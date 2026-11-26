import React, { useState, useEffect } from 'react';
import { CheckCircle2 } from 'lucide-react';

export interface WaitlistSubmission {
  email: string;
  project: string;
  blocker: string;
  blockerDetails: string;
  submittedAt: string;
}

interface WaitlistSectionProps {
  submission: WaitlistSubmission | null;
  onSubmitWaitlist: (data: WaitlistSubmission) => void;
  onResetWaitlist: () => void;
  emailInputRef?: React.RefObject<HTMLInputElement | null>;
}

const BLOCKER_OPTIONS = [
  'Boilerplate das Views e findViewById',
  'Compose exige Kotlin',
  'XML de estilos difícil de manter',
  'Migração para Kotlin custosa',
];

export function WaitlistSection({
  submission,
  onSubmitWaitlist,
  onResetWaitlist,
  emailInputRef,
}: WaitlistSectionProps) {
  const [email, setEmail] = useState(submission?.email ?? '');
  const [project, setProject] = useState(submission?.project ?? '');
  const [selectedBlocker, setSelectedBlocker] = useState(submission?.blocker ?? '');
  const [blockerDetails, setBlockerDetails] = useState(submission?.blockerDetails ?? '');
  const [error, setError] = useState('');

  useEffect(() => {
    if (submission) {
      setEmail(submission.email);
      setProject(submission.project);
      setSelectedBlocker(submission.blocker);
      setBlockerDetails(submission.blockerDetails);
    }
  }, [submission]);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const trimmed = email.trim();
    const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!emailPattern.test(trimmed)) {
      setError('Introduza um endereço de email válido.');
      return;
    }
    setError('');
    onSubmitWaitlist({
      email: trimmed,
      project: project.trim(),
      blocker: selectedBlocker,
      blockerDetails: blockerDetails.trim(),
      submittedAt: new Date().toISOString(),
    });
  };

  return (
    <section
      id="lista-de-espera"
      className="py-20 lg:py-28 border-t border-zinc-200 dark:border-zinc-800/80"
    >
      <div className="max-w-[1200px] mx-auto px-4 sm:px-6 lg:px-8">
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-12 items-start">
          <div className="lg:col-span-5">
            <p className="text-xs font-medium tracking-wide text-[#4F46E5] dark:text-indigo-400 mb-3">
              07. Lista de espera
            </p>
            <h2 className="text-2xl sm:text-3xl font-semibold tracking-tight text-zinc-900 dark:text-zinc-100 text-balance">
              Acompanhe a evolução da v0.1 à v1.0.
            </h2>
            <p className="mt-3 text-base text-zinc-600 dark:text-zinc-400 leading-relaxed">
              Sem newsletters semanais nem trackers. Avisamos quando o repositório de preview e a
              documentação inicial estiverem disponíveis para teste.
            </p>
            <p className="mt-4 text-sm text-zinc-500 dark:text-zinc-500">
              Early preview v0.1. Android primeiro; iOS em investigação.
            </p>
          </div>

          <div className="lg:col-span-7">
            <div className="p-6 sm:p-8 rounded-xl border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-[#111115]">
              {submission ? (
                <div className="space-y-4">
                  <div className="flex items-start gap-3">
                    <CheckCircle2 className="w-5 h-5 text-emerald-500 shrink-0 mt-0.5" />
                    <div>
                      <h3 className="text-lg font-semibold text-zinc-900 dark:text-zinc-100">
                        Registo guardado na lista de espera.
                      </h3>
                      <p className="mt-1 text-sm text-zinc-600 dark:text-zinc-400">
                        Vamos contactar <span className="font-mono text-zinc-900 dark:text-zinc-200">{submission.email}</span> quando o preview v0.1 abrir para equipas Java.
                      </p>
                    </div>
                  </div>

                  {(submission.project || submission.blocker || submission.blockerDetails) && (
                    <div className="pt-4 border-t border-zinc-200 dark:border-zinc-800 text-sm text-zinc-600 dark:text-zinc-400 space-y-1.5">
                      {submission.project && (
                        <div>
                          <span className="text-zinc-500">O que constrói em Java: </span>
                          <span className="text-zinc-800 dark:text-zinc-200">{submission.project}</span>
                        </div>
                      )}
                      {(submission.blocker || submission.blockerDetails) && (
                        <div>
                          <span className="text-zinc-500">Maior dificuldade: </span>
                          <span className="text-zinc-800 dark:text-zinc-200">
                            {[submission.blocker, submission.blockerDetails].filter(Boolean).join(' — ')}
                          </span>
                        </div>
                      )}
                    </div>
                  )}

                  <div className="pt-2">
                    <button
                      type="button"
                      onClick={onResetWaitlist}
                      className="text-xs font-medium text-zinc-600 dark:text-zinc-400 hover:text-zinc-900 dark:hover:text-zinc-100 underline underline-offset-4 cursor-pointer"
                    >
                      Alterar resposta
                    </button>
                  </div>
                </div>
              ) : (
                <form onSubmit={handleSubmit} noValidate className="space-y-6">
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                    <div>
                      <label
                        htmlFor="waitlist-email"
                        className="block text-sm font-medium text-zinc-900 dark:text-zinc-200 mb-1.5"
                      >
                        Email <span className="text-[#4F46E5] dark:text-indigo-400">*</span>
                      </label>
                      <input
                        ref={emailInputRef}
                        id="waitlist-email"
                        type="email"
                        required
                        value={email}
                        onChange={(e) => {
                          setEmail(e.target.value);
                          if (error) setError('');
                        }}
                        placeholder="nome@empresa.pt"
                        className="w-full h-11 px-3.5 rounded-lg border border-zinc-300 dark:border-zinc-800 bg-zinc-50 dark:bg-[#09090B] text-sm text-zinc-900 dark:text-zinc-100 placeholder:text-zinc-400 dark:placeholder:text-zinc-600 focus:outline-none focus:border-[#4F46E5]"
                      />
                    </div>

                    <div>
                      <label
                        htmlFor="waitlist-project"
                        className="block text-sm font-medium text-zinc-900 dark:text-zinc-200 mb-1.5"
                      >
                        O que constrói em Java? <span className="text-zinc-400 font-normal">(opcional)</span>
                      </label>
                      <input
                        id="waitlist-project"
                        type="text"
                        value={project}
                        onChange={(e) => setProject(e.target.value)}
                        placeholder="App interna, cliente POS, SDK..."
                        className="w-full h-11 px-3.5 rounded-lg border border-zinc-300 dark:border-zinc-800 bg-zinc-50 dark:bg-[#09090B] text-sm text-zinc-900 dark:text-zinc-100 placeholder:text-zinc-400 dark:placeholder:text-zinc-600 focus:outline-none focus:border-[#4F46E5]"
                      />
                    </div>
                  </div>

                  {/* Validation question */}
                  <div className="pt-2 border-t border-zinc-200 dark:border-zinc-800/80">
                    <span className="block text-sm font-medium text-zinc-900 dark:text-zinc-200 mb-2.5">
                      Que dificuldade o travou em Android com Java?{' '}
                      <span className="text-zinc-400 font-normal">(opcional)</span>
                    </span>

                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 mb-3">
                      {BLOCKER_OPTIONS.map((option) => {
                        const isSelected = selectedBlocker === option;
                        return (
                          <button
                            key={option}
                            type="button"
                            onClick={() =>
                              setSelectedBlocker((prev) => (prev === option ? '' : option))
                            }
                            aria-pressed={isSelected}
                            className={`text-left px-3.5 py-2.5 text-xs sm:text-[13px] rounded-lg border transition-colors duration-150 cursor-pointer focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#4F46E5] ${
                              isSelected
                                ? 'border-[#4F46E5] bg-[#4F46E5]/10 text-zinc-900 dark:text-zinc-100 font-medium'
                                : 'border-zinc-200 dark:border-zinc-800 bg-zinc-50 dark:bg-[#09090B] text-zinc-700 dark:text-zinc-400 hover:border-zinc-300 dark:hover:border-zinc-700'
                            }`}
                          >
                            {option}
                          </button>
                        );
                      })}
                    </div>

                    <label htmlFor="waitlist-blocker-details" className="sr-only">
                      Detalhe sobre a dificuldade em Android com Java
                    </label>
                    <input
                      id="waitlist-blocker-details"
                      type="text"
                      value={blockerDetails}
                      onChange={(e) => setBlockerDetails(e.target.value)}
                      placeholder="Outro contexto ou detalhe específico..."
                      className="w-full h-10 px-3.5 rounded-lg border border-zinc-300 dark:border-zinc-800 bg-zinc-50 dark:bg-[#09090B] text-sm text-zinc-900 dark:text-zinc-100 placeholder:text-zinc-400 dark:placeholder:text-zinc-600 focus:outline-none focus:border-[#4F46E5]"
                    />
                  </div>

                  {error && (
                    <p role="alert" className="text-xs font-medium text-rose-600 dark:text-rose-400">
                      {error}
                    </p>
                  )}

                  <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
                    <button
                      type="submit"
                      className="px-5 py-2.5 text-sm font-medium text-white bg-[#4F46E5] hover:bg-[#4338CA] rounded-lg transition-colors duration-150 whitespace-nowrap shrink-0 cursor-pointer focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#4F46E5]"
                    >
                      Entrar na lista de espera
                    </button>
                    <span className="text-xs text-zinc-500 dark:text-zinc-400">
                      Sem spam · Resposta única por utilizador
                    </span>
                  </div>
                </form>
              )}
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
