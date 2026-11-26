import React, { useState } from 'react';
import { Menu, X } from 'lucide-react';
import { KairoMark } from './KairoMark';

interface HeaderProps {
  onWaitlistClick: () => void;
  onNavClick?: () => void;
}

const NAV_LINKS = [
  { href: '#demo', label: 'Demo' },
  { href: '#problema', label: 'O problema' },
  { href: '#como-funciona', label: 'Como funciona' },
  { href: '#roadmap', label: 'Roadmap' },
];

export function Header({ onWaitlistClick, onNavClick }: HeaderProps) {
  const [mobileOpen, setMobileOpen] = useState(false);

  return (
    <header className="sticky top-0 z-40 border-b border-zinc-200/80 dark:border-zinc-800/80 bg-zinc-50/90 dark:bg-[#09090B]/90 backdrop-blur-md">
      <div className="max-w-[1200px] mx-auto px-4 sm:px-6 lg:px-8 h-16 flex items-center justify-between gap-8">
        {/* Zone 1: Single-line brand wordmark */}
        <a
          href="#"
          className="inline-flex items-center gap-2.5 text-lg font-semibold tracking-tight text-zinc-900 dark:text-zinc-100 whitespace-nowrap shrink-0 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#4F46E5] rounded-md"
        >
          <KairoMark className="w-5 h-5" />
          <span>Kairo</span>
        </a>

        {/* Zone 2: Concise single-line nav links */}
        <nav
          aria-label="Navegação principal"
          className="hidden md:flex items-center gap-7 text-sm font-medium text-zinc-600 dark:text-zinc-400"
        >
          {NAV_LINKS.map((link) => (
            <a
              key={link.href}
              href={link.href}
              onClick={() => onNavClick?.()}
              className="hover:text-zinc-900 dark:hover:text-zinc-100 transition-colors duration-150 whitespace-nowrap shrink-0 underline-offset-4 hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#4F46E5] rounded-sm"
            >
              {link.label}
            </a>
          ))}
        </nav>

        {/* Zone 3: Single primary action */}
        <div className="flex items-center gap-3 shrink-0">
          <button
            type="button"
            onClick={onWaitlistClick}
            className="hidden sm:inline-flex items-center justify-center px-4 py-2 text-sm font-medium text-white bg-[#4F46E5] hover:bg-[#4338CA] transition-colors duration-150 rounded-lg whitespace-nowrap shrink-0 cursor-pointer focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#4F46E5] focus-visible:ring-offset-2 focus-visible:ring-offset-zinc-50 dark:focus-visible:ring-offset-[#09090B]"
          >
            Entrar na lista de espera
          </button>

          <button
            type="button"
            onClick={() => setMobileOpen((prev) => !prev)}
            aria-expanded={mobileOpen}
            aria-label={mobileOpen ? 'Fechar menu' : 'Abrir menu'}
            className="md:hidden inline-flex items-center justify-center w-10 h-10 rounded-lg border border-zinc-200 dark:border-zinc-800 text-zinc-700 dark:text-zinc-300 hover:bg-zinc-100 dark:hover:bg-zinc-900 transition-colors cursor-pointer"
          >
            {mobileOpen ? <X className="w-5 h-5" /> : <Menu className="w-5 h-5" />}
          </button>
        </div>
      </div>

      {mobileOpen && (
        <div className="md:hidden border-t border-zinc-200 dark:border-zinc-800 bg-zinc-50 dark:bg-[#09090B] px-4 py-4 space-y-3">
          <nav aria-label="Navegação móvel" className="flex flex-col space-y-2">
            {NAV_LINKS.map((link) => (
              <a
                key={link.href}
                href={link.href}
                onClick={() => {
                  setMobileOpen(false);
                  onNavClick?.();
                }}
                className="py-2 text-sm font-medium text-zinc-700 dark:text-zinc-300 hover:text-zinc-900 dark:hover:text-zinc-100"
              >
                {link.label}
              </a>
            ))}
          </nav>
          <button
            type="button"
            onClick={() => {
              setMobileOpen(false);
              onWaitlistClick();
            }}
            className="w-full py-2.5 px-4 text-sm font-medium text-white bg-[#4F46E5] hover:bg-[#4338CA] rounded-lg transition-colors cursor-pointer"
          >
            Entrar na lista de espera
          </button>
        </div>
      )}
    </header>
  );
}
