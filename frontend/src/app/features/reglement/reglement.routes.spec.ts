import {describe, expect, it} from 'vitest';
import {reglementRoutes} from './reglement.routes';
import {ReglementWorkspaceComponent} from './reglement-workspace.component';
import {ReglementTableauDeBordComponent} from './tableau-de-bord/reglement-tableau-de-bord.component';

describe('routes Règlement', () => {
  it('l\'arrivée dans le module mène à la saisie des règlements, sans guard propre', async () => {
    const arrivee = reglementRoutes.find((r) => r.path === '');

    expect(arrivee?.redirectTo).toBeUndefined();
    expect(arrivee?.canActivate).toBeUndefined();
    expect(await arrivee?.loadComponent?.()).toBe(ReglementWorkspaceComponent);
  });

  it('le tableau de bord est servi sur /reglement/tableau-de-bord avec les mêmes droits', async () => {
    const tableauDeBord = reglementRoutes.find((r) => r.path === 'tableau-de-bord');

    expect(tableauDeBord?.canActivate).toBeUndefined();
    expect(await tableauDeBord?.loadComponent?.()).toBe(ReglementTableauDeBordComponent);
  });

  it('ne garde aucune redirection vide (route morte supprimée)', () => {
    expect(reglementRoutes.filter((r) => r.path === '')).toHaveLength(1);
    expect(reglementRoutes.some((r) => r.redirectTo !== undefined)).toBe(false);
  });
});
