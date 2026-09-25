// Icônes SVG en ligne (tracés de 24 × 24), décoratives : masquées aux lecteurs d'écran.

function Icone({ taille = 20, children, ...proprietes }) {
  return (
    <svg
      width={taille}
      height={taille}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
      {...proprietes}
    >
      {children}
    </svg>
  );
}

export function IconeErreur(proprietes) {
  return (
    <Icone {...proprietes}>
      <circle cx="12" cy="12" r="10" />
      <line x1="12" y1="8" x2="12" y2="12" />
      <line x1="12" y1="16" x2="12.01" y2="16" />
    </Icone>
  );
}

export function IconeSucces(proprietes) {
  return (
    <Icone {...proprietes}>
      <circle cx="12" cy="12" r="10" />
      <polyline points="8 12.5 11 15.5 16 9.5" />
    </Icone>
  );
}

export function IconeCopier(proprietes) {
  return (
    <Icone {...proprietes}>
      <rect x="9" y="9" width="13" height="13" rx="2" />
      <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1" />
    </Icone>
  );
}

export function IconeCoche(proprietes) {
  return (
    <Icone {...proprietes}>
      <polyline points="20 6 9 17 4 12" />
    </Icone>
  );
}

export function IconeHorloge(proprietes) {
  return (
    <Icone {...proprietes}>
      <circle cx="12" cy="12" r="10" />
      <polyline points="12 6 12 12 16 14" />
    </Icone>
  );
}

export function IconeFormateur(proprietes) {
  return (
    <Icone {...proprietes}>
      <rect x="2" y="3" width="20" height="14" rx="2" />
      <line x1="8" y1="21" x2="16" y2="21" />
      <line x1="12" y1="17" x2="12" y2="21" />
    </Icone>
  );
}

export function IconeExercice(proprietes) {
  return (
    <Icone {...proprietes}>
      <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
      <polyline points="14 2 14 8 20 8" />
      <line x1="12" y1="18" x2="12" y2="12" />
      <polyline points="9 15 12 12 15 15" />
    </Icone>
  );
}

export function IconeRelecture(proprietes) {
  return (
    <Icone {...proprietes}>
      <path d="M12 20h9" />
      <path d="M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z" />
    </Icone>
  );
}

export function IconeEtudiant(proprietes) {
  return (
    <Icone {...proprietes}>
      <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
      <circle cx="12" cy="7" r="4" />
    </Icone>
  );
}
