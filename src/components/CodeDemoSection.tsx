import React, { useState } from 'react';
import { Check, Copy, RotateCcw } from 'lucide-react';

type FileTab = 'xml' | 'css' | 'java';

const CODE_SNIPPETS: Record<
  FileTab,
  { filename: string; language: string; lines: string[] }
> = {
  xml: {
    filename: 'login.xml',
    language: 'XML',
    lines: [
      '<Column id="root" class="screen">',
      '  <Text class="title">Entrar na conta</Text>',
      '  <Text class="subtitle">Acesso direto ao painel em Java.</Text>',
      '',
      '  <Input id="email"',
      '         class="field"',
      '         hint="Email"',
      '         value="{state.email}"',
      '         onChange="onEmailChanged" />',
      '',
      '  <Input id="password"',
      '         class="field"',
      '         type="password"',
      '         hint="Palavra-passe"',
      '         value="{state.password}"',
      '         onChange="onPasswordChanged" />',
      '',
      '  <Text id="feedback" class="feedback">{state.message}</Text>',
      '',
      '  <Button id="submit"',
      '          class="primary-btn"',
      '          enabled="{state.canSubmit}"',
      '          onClick="onSubmit">',
      '    {state.loading ? "A autenticar..." : "Entrar"}',
      '  </Button>',
      '</Column>',
    ],
  },
  css: {
    filename: 'login.css',
    language: 'CSS',
    lines: [
      '.screen {',
      '  padding: 24px;',
      '  gap: 14px;',
      '  background-color: #09090B;',
      '}',
      '',
      '.title {',
      '  font-size: 22px;',
      '  font-weight: 600;',
      '  color: #F4F4F5;',
      '}',
      '',
      '.subtitle {',
      '  font-size: 14px;',
      '  color: #A1A1AA;',
      '  margin-bottom: 8px;',
      '}',
      '',
      '.field {',
      '  height: 46px;',
      '  padding: 0 14px;',
      '  border: 1px solid #27272A;',
      '  border-radius: 8px;',
      '  background-color: #111115;',
      '  color: #F4F4F5;',
      '}',
      '',
      '.primary-btn {',
      '  height: 46px;',
      '  border-radius: 8px;',
      '  background-color: #4F46E5;',
      '  color: #FFFFFF;',
      '  font-weight: 600;',
      '}',
    ],
  },
  java: {
    filename: 'LoginView.java',
    language: 'Java',
    lines: [
      'public final class LoginView extends KairoView<LoginState> {',
      '',
      '  @Override',
      '  protected LoginState initialState() {',
      '    return new LoginState("", "", "", false);',
      '  }',
      '',
      '  public void onEmailChanged(String value) {',
      '    setState(s -> s.withEmail(value).clearMessage());',
      '  }',
      '',
      '  public void onPasswordChanged(String value) {',
      '    setState(s -> s.withPassword(value).clearMessage());',
      '  }',
      '',
      '  public void onSubmit() {',
      '    if (!state().email().contains("@")) {',
      '      setState(s -> s.withMessage("Introduza um email válido."));',
      '      return;',
      '    }',
      '    setState(s -> s.withLoading(true).withMessage("Sessão pronta."));',
      '  }',
      '}',
    ],
  },
};

function renderTokenizedLine(line: string, tab: FileTab) {
  if (!line.trim()) {
    return <span>&nbsp;</span>;
  }

  if (tab === 'xml') {
    const tagMatch = line.match(/^(\s*)(<\/?[A-Za-z]+)(.*?)(\/?>)$/);
    if (tagMatch) {
      const [, indent, openTag, attrs, closeTag] = tagMatch;
      return (
        <>
          <span>{indent}</span>
          <span className="text-indigo-600 dark:text-indigo-400">{openTag}</span>
          <span className="text-zinc-700 dark:text-zinc-300">{attrs}</span>
          <span className="text-indigo-600 dark:text-indigo-400">{closeTag}</span>
        </>
      );
    }
    return <span className="text-zinc-700 dark:text-zinc-300">{line}</span>;
  }

  if (tab === 'css') {
    if (line.includes('{') || line.includes('}')) {
      return <span className="text-indigo-600 dark:text-indigo-400">{line}</span>;
    }
    const colonIdx = line.indexOf(':');
    if (colonIdx !== -1) {
      const prop = line.slice(0, colonIdx);
      const val = line.slice(colonIdx);
      return (
        <>
          <span className="text-zinc-600 dark:text-zinc-400">{prop}</span>
          <span className="text-zinc-900 dark:text-zinc-200">{val}</span>
        </>
      );
    }
  }

  if (tab === 'java') {
    if (line.trim().startsWith('@')) {
      return <span className="text-indigo-600 dark:text-indigo-400">{line}</span>;
    }
    const keywords = ['public', 'final', 'class', 'extends', 'protected', 'return', 'new', 'void', 'if'];
    const parts = line.split(/(\b(?:public|final|class|extends|protected|return|new|void|if)\b)/g);
    return (
      <>
        {parts.map((part, i) =>
          keywords.includes(part) ? (
            <span key={i} className="text-indigo-600 dark:text-indigo-400 font-medium">
              {part}
            </span>
          ) : (
            <span key={i} className="text-zinc-800 dark:text-zinc-200">
              {part}
            </span>
          )
        )}
      </>
    );
  }

  return <span className="text-zinc-800 dark:text-zinc-200">{line}</span>;
}

export function CodeDemoSection() {
  const [activeTab, setActiveTab] = useState<FileTab>('xml');
  const [copied, setCopied] = useState(false);

  // Interactive emulator state reflecting LoginView.java
  const [email, setEmail] = useState('dev@empresa.pt');
  const [password, setPassword] = useState('••••••••');
  const [statusMessage, setStatusMessage] = useState<string>('');
  const [statusKind, setStatusKind] = useState<'neutral' | 'error' | 'success'>('neutral');
  const [lastPatch, setLastPatch] = useState<string>(
    'Mount -> LinearLayout(6 nós nativos)'
  );

  const currentSnippet = CODE_SNIPPETS[activeTab];

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(currentSnippet.lines.join('\n'));
      setCopied(true);
      setTimeout(() => setCopied(false), 1800);
    } catch {
      // Clipboard fallback ignored silently
    }
  };

  const handleEmailInput = (value: string) => {
    setEmail(value);
    setStatusMessage('');
    setStatusKind('neutral');
    setLastPatch(`Patch: EditText#email.setText("${value || '""'}")`);
  };

  const handlePasswordInput = (value: string) => {
    setPassword(value);
    setStatusMessage('');
    setStatusKind('neutral');
    setLastPatch(`Patch: EditText#password.setText(${value.length} chars)`);
  };

  const handleEmulatorSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!email.includes('@')) {
      setStatusMessage('Introduza um email válido.');
      setStatusKind('error');
      setLastPatch('Patch: TextView#feedback.setText("Introduza um email válido.")');
      return;
    }
    if (password.trim().length < 4) {
      setStatusMessage('Palavra-passe demasiado curta.');
      setStatusKind('error');
      setLastPatch('Patch: TextView#feedback.setText("Palavra-passe demasiado curta.")');
      return;
    }
    setStatusMessage(`Sessão pronta para ${email}.`);
    setStatusKind('success');
    setLastPatch('Patch: Button#submit.setText("A autenticar...") + TextView#feedback');
  };

  const handleResetDemo = () => {
    setEmail('dev@empresa.pt');
    setPassword('••••••••');
    setStatusMessage('');
    setStatusKind('neutral');
    setLastPatch('Mount -> LinearLayout(6 nós nativos)');
  };

  return (
    <section
      id="demo"
      className="py-20 lg:py-28 border-t border-zinc-200 dark:border-zinc-800/80"
    >
      <div className="max-w-[1200px] mx-auto px-4 sm:px-6 lg:px-8">
        <div className="max-w-2xl mb-12">
          <p className="text-xs font-medium tracking-wide text-[#4F46E5] dark:text-indigo-400 mb-3">
            01. Demo lado a lado
          </p>
          <h2 className="text-2xl sm:text-3xl font-semibold tracking-tight text-zinc-900 dark:text-zinc-100 text-balance">
            Um ecrã de login em XML, CSS e Java, ligado a widgets nativos.
          </h2>
          <p className="mt-3 text-base text-zinc-600 dark:text-zinc-400 leading-relaxed">
            Escreva a estrutura em XML, estilize com CSS familiar e controle o estado em Java puro.
            Interaja com o emulador ao lado para ver o único patch enviado à View nativa.
          </p>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-stretch">
          {/* Code Editor Panel */}
          <div className="lg:col-span-7 flex flex-col rounded-xl border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-[#111115] overflow-hidden">
            {/* Editor Toolbar */}
            <div className="flex items-center justify-between gap-4 px-4 py-2.5 border-b border-zinc-200 dark:border-zinc-800 bg-zinc-50 dark:bg-[#0D0D11]">
              <div className="flex items-center gap-1" role="tablist" aria-label="Ficheiros do exemplo">
                {(['xml', 'css', 'java'] as FileTab[]).map((tab) => {
                  const isActive = activeTab === tab;
                  return (
                    <button
                      key={tab}
                      type="button"
                      role="tab"
                      aria-selected={isActive}
                      onClick={() => setActiveTab(tab)}
                      className={`px-3 py-1.5 text-xs font-mono rounded-md transition-colors duration-150 whitespace-nowrap shrink-0 cursor-pointer focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#4F46E5] ${
                        isActive
                          ? 'bg-zinc-900 text-white dark:bg-zinc-800 dark:text-zinc-100'
                          : 'text-zinc-600 dark:text-zinc-400 hover:text-zinc-900 dark:hover:text-zinc-200'
                      }`}
                    >
                      {CODE_SNIPPETS[tab].filename}
                    </button>
                  );
                })}
              </div>

              <button
                type="button"
                onClick={handleCopy}
                aria-label="Copiar código"
                className="inline-flex items-center gap-1.5 px-2.5 py-1.5 text-xs font-medium text-zinc-600 dark:text-zinc-400 hover:text-zinc-900 dark:hover:text-zinc-100 rounded-md hover:bg-zinc-200/60 dark:hover:bg-zinc-800/80 transition-colors whitespace-nowrap shrink-0 cursor-pointer"
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

            {/* Code Lines */}
            <div className="flex-1 p-5 overflow-x-auto font-mono text-[13px] leading-6">
              <table className="w-full border-collapse">
                <tbody>
                  {currentSnippet.lines.map((line, index) => (
                    <tr key={index} className="align-top">
                      <td className="select-none pr-5 text-right text-zinc-400 dark:text-zinc-600 tabular-nums w-8">
                        {index + 1}
                      </td>
                      <td className="whitespace-pre">{renderTokenizedLine(line, activeTab)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            {/* Subtle file footer */}
            <div className="px-4 py-2.5 border-t border-zinc-200 dark:border-zinc-800 bg-zinc-50 dark:bg-[#0D0D11] flex items-center justify-between text-xs text-zinc-500 dark:text-zinc-400 font-mono">
              <span>{currentSnippet.language}</span>
              <span>{currentSnippet.lines.length} linhas · Sem Kotlin</span>
            </div>
          </div>

          {/* Android Emulator Panel */}
          <div className="lg:col-span-5 flex flex-col justify-between rounded-xl border border-zinc-200 dark:border-zinc-800 bg-zinc-100/70 dark:bg-[#111115] p-6">
            <div className="flex items-center justify-between mb-4">
              <div className="text-xs text-zinc-600 dark:text-zinc-400">
                <span className="font-medium text-zinc-900 dark:text-zinc-200">
                  Emulador Android
                </span>
                <span className="mx-1.5" aria-hidden="true">·</span>
                <span>Widgets nativos (API 26+)</span>
              </div>
              <button
                type="button"
                onClick={handleResetDemo}
                aria-label="Repor estado do emulador"
                className="inline-flex items-center gap-1 text-xs text-zinc-500 hover:text-zinc-900 dark:text-zinc-400 dark:hover:text-zinc-100 transition-colors cursor-pointer"
              >
                <RotateCcw className="w-3.5 h-3.5" />
                <span>Repor</span>
              </button>
            </div>

            {/* Device Frame */}
            <div className="mx-auto w-full max-w-[310px] rounded-[28px] border-4 border-zinc-300 dark:border-zinc-800 bg-[#09090B] text-zinc-100 shadow-sm overflow-hidden">
              {/* Android Status Bar */}
              <div className="px-5 pt-3 pb-2 flex items-center justify-between text-[11px] font-mono text-zinc-400">
                <span className="tabular-nums">09:41</span>
                <div className="w-12 h-2.5 rounded-full bg-zinc-800" aria-hidden="true" />
                <span className="tabular-nums">5G</span>
              </div>

              {/* Rendered Native Screen */}
              <form onSubmit={handleEmulatorSubmit} className="px-5 pt-5 pb-7 space-y-3.5">
                <div>
                  <h3 className="text-[20px] font-semibold tracking-tight text-zinc-100">
                    Entrar na conta
                  </h3>
                  <p className="text-[13px] text-zinc-400 mt-1">
                    Acesso direto ao painel em Java.
                  </p>
                </div>

                <div className="space-y-3 pt-1">
                  <label className="block">
                    <span className="sr-only">Email no emulador</span>
                    <input
                      type="text"
                      value={email}
                      onChange={(e) => handleEmailInput(e.target.value)}
                      placeholder="Email"
                      className="w-full h-11 px-3.5 rounded-lg border border-zinc-800 bg-[#111115] text-sm text-zinc-100 placeholder:text-zinc-500 focus:outline-none focus:border-[#4F46E5]"
                    />
                  </label>

                  <label className="block">
                    <span className="sr-only">Palavra-passe no emulador</span>
                    <input
                      type="password"
                      value={password}
                      onChange={(e) => handlePasswordInput(e.target.value)}
                      placeholder="Palavra-passe"
                      className="w-full h-11 px-3.5 rounded-lg border border-zinc-800 bg-[#111115] text-sm text-zinc-100 placeholder:text-zinc-500 focus:outline-none focus:border-[#4F46E5]"
                    />
                  </label>
                </div>

                <div className="min-h-[20px] text-xs font-medium">
                  {statusMessage && (
                    <p
                      className={
                        statusKind === 'error'
                          ? 'text-rose-400'
                          : statusKind === 'success'
                          ? 'text-emerald-400'
                          : 'text-zinc-400'
                      }
                    >
                      {statusMessage}
                    </p>
                  )}
                </div>

                <button
                  type="submit"
                  className="w-full h-11 rounded-lg bg-[#4F46E5] hover:bg-[#4338CA] text-white text-sm font-semibold transition-colors cursor-pointer"
                >
                  Entrar
                </button>
              </form>

              {/* Android Gesture Pill */}
              <div className="pb-2.5 flex justify-center">
                <div className="w-20 h-1 rounded-full bg-zinc-700" aria-hidden="true" />
              </div>
            </div>

            {/* Incremental Patch Inspector */}
            <div className="mt-5 pt-4 border-t border-zinc-200 dark:border-zinc-800">
              <div className="text-[11px] font-medium text-zinc-500 dark:text-zinc-400 mb-1">
                Último patch incremental aplicado
              </div>
              <div className="font-mono text-xs text-zinc-800 dark:text-zinc-200 truncate">
                {lastPatch}
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
