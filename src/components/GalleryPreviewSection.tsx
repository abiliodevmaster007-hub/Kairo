import React, { useState } from 'react';
import { Check, ChevronLeft, Loader2, Plus, Trash2 } from 'lucide-react';

interface GalleryCategory {
  id: number;
  title: string;
  components: string;
}

const GALLERY_CATEGORIES: GalleryCategory[] = [
  { id: 1, title: '01. Layout', components: 'Column, Row, Box, Stack, Scroll' },
  { id: 2, title: '02. Texto e ações', components: 'Text, Button' },
  { id: 3, title: '03. Input', components: 'Input, TextArea' },
  { id: 4, title: '04. Seleção', components: 'Checkbox, Switch, Select' },
  { id: 5, title: '05. Media e superfície', components: 'Image, Card, Divider' },
  { id: 6, title: '06. Feedback', components: 'Progress' },
  { id: 7, title: '07. Form', components: 'Form, FormField, validação' },
  { id: 8, title: '08. Listas', components: 'List com StableKey e mutações' },
  { id: 9, title: '09. Navegação', components: 'Router, push, pop, replace, back' },
  { id: 10, title: '10. Async', components: 'FutureState, erro e cancelamento' },
  { id: 11, title: '11. Temas e estados', components: 'Claro/escuro, disabled, error' },
  { id: 12, title: '12. Stress', components: '10 000 itens, aninhamento e texto longo' },
];

export function GalleryPreviewSection() {
  const [activeCategory, setActiveCategory] = useState<number>(7);
  const [navStack, setNavStack] = useState<string[]>(['/gallery', '/gallery/form']);
  const [formEmail, setFormEmail] = useState('equipa@java.pt');
  const [formError, setFormError] = useState<string | null>(null);
  const [formSaved, setFormSaved] = useState(false);

  const [checkVal, setCheckVal] = useState(true);
  const [switchVal, setSwitchVal] = useState(false);
  const [selectVal, setSelectVal] = useState('API 26 (Android 8.0)');

  const [listItems, setListItems] = useState([
    { key: 'k-1', label: 'kairo-core · Identidade lógica' },
    { key: 'k-2', label: 'kairo-layout · Constraints & Fill' },
    { key: 'k-3', label: 'kairo-renderer-android · ViewGroup host' },
  ]);

  const [asyncStatus, setAsyncStatus] = useState<'IDLE' | 'RUNNING' | 'SUCCESS' | 'CANCELLED'>('IDLE');

  const selectCategory = (id: number) => {
    setActiveCategory(id);
    const slug = GALLERY_CATEGORIES.find((c) => c.id === id)?.title.split(' ')[1]?.toLowerCase() ?? 'item';
    setNavStack(['/gallery', `/gallery/${slug}`]);
  };

  const handleBack = () => {
    if (navStack.length > 1) {
      setNavStack((prev) => prev.slice(0, -1));
    }
  };

  return (
    <section className="py-16 lg:py-24">
      <div className="max-w-[1200px] mx-auto px-4 sm:px-6 lg:px-8">
        <div className="max-w-3xl mb-10">
          <p className="text-xs font-medium tracking-wide text-[#4F46E5] dark:text-indigo-400 mb-3">
            Etapa 12 · examples/gallery/
          </p>
          <h2 className="text-2xl sm:text-3xl font-semibold tracking-tight text-zinc-900 dark:text-zinc-100 text-balance">
            Kairo Gallery: matriz interativa das 12 categorias da v1.
          </h2>
          <p className="mt-3 text-base text-zinc-600 dark:text-zinc-400 leading-relaxed">
            Cada ecrã do Gallery consome exclusivamente as APIs públicas do Kairo (<code className="text-sm text-zinc-900 dark:text-zinc-200">ComponentRegistry</code>,{' '}
            <code className="text-sm text-zinc-900 dark:text-zinc-200">FormController</code>, <code className="text-sm text-zinc-900 dark:text-zinc-200">KairoListDiff</code> e{' '}
            <code className="text-sm text-zinc-900 dark:text-zinc-200">Router</code>).
          </p>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
          {/* Category Index */}
          <div className="lg:col-span-6 grid grid-cols-1 sm:grid-cols-2 gap-2.5">
            {GALLERY_CATEGORIES.map((cat) => {
              const active = cat.id === activeCategory;
              return (
                <button
                  key={cat.id}
                  type="button"
                  onClick={() => selectCategory(cat.id)}
                  className={`text-left p-3.5 rounded-xl border transition-colors cursor-pointer ${
                    active
                      ? 'border-[#4F46E5] bg-[#4F46E5]/[0.07] dark:bg-[#4F46E5]/10'
                      : 'border-zinc-200 dark:border-zinc-800 bg-white dark:bg-[#111115] hover:border-zinc-300 dark:hover:border-zinc-700'
                  }`}
                >
                  <div className="text-sm font-semibold text-zinc-900 dark:text-zinc-100">
                    {cat.title}
                  </div>
                  <div className="text-xs text-zinc-500 dark:text-zinc-400 mt-1 truncate">
                    {cat.components}
                  </div>
                </button>
              );
            })}
          </div>

          {/* Interactive Gallery Screen Preview */}
          <div className="lg:col-span-6 rounded-xl border border-zinc-200 dark:border-zinc-800 bg-zinc-100/70 dark:bg-[#111115] p-6">
            <div className="flex items-center justify-between gap-3 pb-4 mb-5 border-b border-zinc-200 dark:border-zinc-800">
              <div className="flex items-center gap-2 text-xs font-mono text-zinc-600 dark:text-zinc-400">
                <button
                  type="button"
                  onClick={handleBack}
                  disabled={navStack.length <= 1}
                  className="inline-flex items-center gap-1 px-2 py-1 rounded border border-zinc-300 dark:border-zinc-700 disabled:opacity-40 cursor-pointer"
                >
                  <ChevronLeft className="w-3.5 h-3.5" />
                  <span>Back</span>
                </button>
                <span className="truncate">{navStack[navStack.length - 1]}</span>
              </div>
              <span className="text-xs font-mono text-zinc-500">
                Stack: {navStack.length}
              </span>
            </div>

            {activeCategory === 4 && (
              <div className="space-y-4">
                <h3 className="text-base font-semibold">Seleção: Checkbox, Switch, Select</h3>
                <label className="flex items-center gap-3 text-sm cursor-pointer">
                  <input type="checkbox" checked={checkVal} onChange={(e) => setCheckVal(e.target.checked)} className="accent-[#4F46E5] w-4 h-4" />
                  <span>Bind bidirecional Checkbox ({String(checkVal)})</span>
                </label>
                <label className="flex items-center justify-between text-sm cursor-pointer">
                  <span>Virtualização ativa</span>
                  <button
                    type="button"
                    onClick={() => setSwitchVal((v) => !v)}
                    className={`w-10 h-6 rounded-full transition-colors p-0.5 cursor-pointer ${switchVal ? 'bg-[#4F46E5]' : 'bg-zinc-400 dark:bg-zinc-700'}`}
                  >
                    <span className={`block w-5 h-5 rounded-full bg-white transition-transform ${switchVal ? 'translate-x-4' : 'translate-x-0'}`} />
                  </button>
                </label>
                <select
                  value={selectVal}
                  onChange={(e) => setSelectVal(e.target.value)}
                  className="w-full h-10 px-3 rounded-lg border border-zinc-300 dark:border-zinc-700 bg-white dark:bg-[#09090B] text-sm"
                >
                  <option>API 26 (Android 8.0)</option>
                  <option>API 34 (Android 14)</option>
                  <option>API 35 (Android 15)</option>
                </select>
              </div>
            )}

            {activeCategory === 7 && (
              <div className="space-y-4">
                <h3 className="text-base font-semibold">Form + FormField + Validação</h3>
                <div>
                  <label className="block text-xs font-medium mb-1">Email obrigatório</label>
                  <input
                    type="email"
                    value={formEmail}
                    onChange={(e) => {
                      setFormEmail(e.target.value);
                      setFormError(null);
                      setFormSaved(false);
                    }}
                    className={`w-full h-10 px-3 rounded-lg border bg-white dark:bg-[#09090B] text-sm ${
                      formError ? 'border-rose-500' : 'border-zinc-300 dark:border-zinc-700'
                    }`}
                  />
                  {formError && <p className="text-xs text-rose-500 mt-1">{formError}</p>}
                  {formSaved && <p className="text-xs text-emerald-500 mt-1">FormController.validateAll() = true</p>}
                </div>
                <button
                  type="button"
                  onClick={() => {
                    if (!formEmail.includes('@')) {
                      setFormError('Email inválido (Validator.email)');
                      setFormSaved(false);
                    } else {
                      setFormError(null);
                      setFormSaved(true);
                    }
                  }}
                  className="px-4 py-2 text-xs font-medium text-white bg-[#4F46E5] hover:bg-[#4338CA] rounded-lg cursor-pointer"
                >
                  Validar e Submeter
                </button>
              </div>
            )}

            {activeCategory === 8 && (
              <div className="space-y-3">
                <div className="flex items-center justify-between">
                  <h3 className="text-base font-semibold">List (StableKey + diff incremental)</h3>
                  <button
                    type="button"
                    onClick={() =>
                      setListItems((prev) => [
                        ...prev,
                        { key: `k-${Date.now()}`, label: `Novo item #${prev.length + 1}` },
                      ])
                    }
                    className="inline-flex items-center gap-1 px-2.5 py-1 text-xs font-medium bg-[#4F46E5] text-white rounded-md cursor-pointer"
                  >
                    <Plus className="w-3.5 h-3.5" />
                    <span>Adicionar</span>
                  </button>
                </div>
                <div className="space-y-2">
                  {listItems.map((item) => (
                    <div
                      key={item.key}
                      className="flex items-center justify-between px-3 py-2 rounded-lg border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-[#09090B] text-xs font-mono"
                    >
                      <span>{item.label}</span>
                      <button
                        type="button"
                        onClick={() => setListItems((prev) => prev.filter((x) => x.key !== item.key))}
                        className="text-zinc-400 hover:text-rose-500 cursor-pointer"
                        aria-label="Remover item"
                      >
                        <Trash2 className="w-3.5 h-3.5" />
                      </button>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {activeCategory === 10 && (
              <div className="space-y-4">
                <h3 className="text-base font-semibold">Async (FutureState + Cancelamento)</h3>
                <div className="text-xs font-mono text-zinc-500">Status: {asyncStatus}</div>
                <div className="flex items-center gap-2">
                  <button
                    type="button"
                    onClick={() => {
                      setAsyncStatus('RUNNING');
                      setTimeout(() => {
                        setAsyncStatus((curr) => (curr === 'RUNNING' ? 'SUCCESS' : curr));
                      }, 800);
                    }}
                    className="inline-flex items-center gap-1.5 px-3.5 py-2 text-xs font-medium bg-[#4F46E5] text-white rounded-lg cursor-pointer"
                  >
                    {asyncStatus === 'RUNNING' && <Loader2 className="w-3.5 h-3.5 animate-spin" />}
                    <span>Executar tarefa IO</span>
                  </button>
                  <button
                    type="button"
                    onClick={() => setAsyncStatus('CANCELLED')}
                    className="px-3.5 py-2 text-xs font-medium border border-zinc-300 dark:border-zinc-700 rounded-lg cursor-pointer"
                  >
                    Cancelar
                  </button>
                </div>
              </div>
            )}

            {![4, 7, 8, 10].includes(activeCategory) && (
              <div className="space-y-3">
                <h3 className="text-base font-semibold text-zinc-900 dark:text-zinc-100">
                  {GALLERY_CATEGORIES.find((c) => c.id === activeCategory)?.title}
                </h3>
                <p className="text-sm text-zinc-600 dark:text-zinc-400">
                  Componentes exercitados: <span className="font-mono text-xs">{GALLERY_CATEGORIES.find((c) => c.id === activeCategory)?.components}</span>
                </p>
                <div className="p-3.5 rounded-lg border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-[#09090B] text-xs space-y-1.5">
                  <div className="flex items-center gap-2 text-emerald-500 font-medium">
                    <Check className="w-4 h-4" />
                    <span>Contrato neutro verificado via FakeRenderer (testkit)</span>
                  </div>
                  <div className="text-zinc-500 font-mono">
                    Sem imports android.* · Sem fuga de subscrições após unmount/destroy
                  </div>
                </div>
              </div>
            )}
          </div>
        </div>
      </div>
    </section>
  );
}
