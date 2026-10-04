/** Codes de jours de dialyse d'un mouvement (`LUNDI,MERCREDI`) sous forme de liste ; vide si aucun jour. */
export function joursDeMouvement(jours: string | null | undefined): string[] {
  return (jours ?? '').split(',').map((j) => j.trim()).filter((j) => j.length > 0);
}
