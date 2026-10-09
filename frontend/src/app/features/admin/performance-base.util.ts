/** Durée lisible : « 12 ms », « 3,4 s », « 2 min 05 s ». */
export function formatDuree(ms: number): string {
  if (!Number.isFinite(ms) || ms < 0) return '—';
  if (ms < 1000) return `${ms < 10 ? ms.toFixed(1).replace('.', ',') : Math.round(ms)} ms`;
  const secondes = ms / 1000;
  if (secondes < 60) return `${secondes.toFixed(1).replace('.', ',')} s`;
  const minutes = Math.floor(secondes / 60);
  const reste = Math.round(secondes - minutes * 60);
  return `${minutes} min ${String(reste).padStart(2, '0')} s`;
}

/** Taille lisible : « 512 o », « 8 Ko », « 12,4 Mo », « 1,2 Go ». */
export function formatTaille(octets: number): string {
  if (!Number.isFinite(octets) || octets < 0) return '—';
  const unites = ['o', 'Ko', 'Mo', 'Go', 'To'];
  let valeur = octets;
  let rang = 0;
  while (valeur >= 1024 && rang < unites.length - 1) {
    valeur /= 1024;
    rang++;
  }
  const texte = rang === 0 || valeur >= 100 ? String(Math.round(valeur)) : valeur.toFixed(1).replace('.', ',').replace(/,0$/, '');
  return `${texte} ${unites[rang]}`;
}

/** Part du temps total : « 12,3 % », « < 0,1 % » pour une part infime mais non nulle. */
export function formatPart(pct: number): string {
  if (!Number.isFinite(pct) || pct <= 0) return '0 %';
  if (pct < 0.1) return '< 0,1 %';
  return `${pct.toFixed(1).replace('.', ',')} %`;
}
