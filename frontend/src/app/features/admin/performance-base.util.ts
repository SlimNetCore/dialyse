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

/** Part du temps total : « 12,3 % », « < 0,1 % » pour une part infime mais non nulle. */
export function formatPart(pct: number): string {
  if (!Number.isFinite(pct) || pct <= 0) return '0 %';
  if (pct < 0.1) return '< 0,1 %';
  return `${pct.toFixed(1).replace('.', ',')} %`;
}
