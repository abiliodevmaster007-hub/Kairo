import React from 'react';

interface KairoMarkProps {
  className?: string;
}

export function KairoMark({ className = 'w-6 h-6' }: KairoMarkProps) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      className={className}
      aria-hidden="true"
    >
      <rect
        x="3.5"
        y="3.5"
        width="3.25"
        height="17"
        rx="0.75"
        fill="currentColor"
      />
      <path
        d="M17.75 3.5H13.5L7.75 11.25L13.75 20.5H18.25L11.75 11.25L17.75 3.5Z"
        fill="currentColor"
      />
      <circle cx="19" cy="12" r="2.25" fill="#4F46E5" />
    </svg>
  );
}
