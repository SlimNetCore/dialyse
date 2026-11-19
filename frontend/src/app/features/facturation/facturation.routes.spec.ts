import {describe, expect, it} from 'vitest';
import {facturationRoutes} from './facturation.routes';
import {centreAdminGuard} from '../../core/auth/admin.guard';
import {FacturationWorkspaceComponent} from './facturation-workspace.component';
import {FacturationTableauDeBordComponent} from './tableau-de-bord/facturation-tableau-de-bord.component';

describe('routes Facturation', () => {
  it('l\'arrivée dans le module mène à l\'écran de calcul et de validation, réservé à l\'admin du centre', async () => {
    const arrivee = facturationRoutes.find((r) => r.path === '');

    expect(arrivee?.redirectTo).toBeUndefined();
    expect(arrivee?.canActivate).toEqual([centreAdminGuard]);
    expect(await arrivee?.loadComponent?.()).toBe(FacturationWorkspaceComponent);
  });

  it('le tableau de bord est servi sur /facturation/tableau-de-bord avec les mêmes droits', async () => {
    const tableauDeBord = facturationRoutes.find((r) => r.path === 'tableau-de-bord');

    expect(tableauDeBord?.canActivate).toEqual([centreAdminGuard]);
    expect(await tableauDeBord?.loadComponent?.()).toBe(FacturationTableauDeBordComponent);
  });

  it('ne garde aucune redirection vide (route morte supprimée)', () => {
    expect(facturationRoutes.filter((r) => r.path === '')).toHaveLength(1);
    expect(facturationRoutes.some((r) => r.redirectTo !== undefined)).toBe(false);
  });
});
