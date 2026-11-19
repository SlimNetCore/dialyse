import {describe, expect, it} from 'vitest';
import {stockRoutes} from './stock.routes';
import {EtatDuStockComponent} from './etat-du-stock.component';
import {StockTableauDeBordComponent} from './stock-tableau-de-bord.component';

describe('routes Stock', () => {
  const enfants = stockRoutes[0].children ?? [];

  it('l\'arrivée dans le module Stock mène à l\'état du stock (écran de travail)', async () => {
    const arrivee = enfants.find((r) => r.path === '');

    expect(arrivee?.redirectTo).toBeUndefined();
    expect(await arrivee?.loadComponent?.()).toBe(EtatDuStockComponent);
  });

  it('le tableau de bord du stock est servi sur /stock/tableau-de-bord', async () => {
    const tableauDeBord = enfants.find((r) => r.path === 'tableau-de-bord');

    expect(await tableauDeBord?.loadComponent?.()).toBe(StockTableauDeBordComponent);
  });
});
