import {describe, expect, it} from 'vitest';
import {PlanningSemaineComponent} from './features/planning/planning-semaine.component';
import {routes} from './app.routes';

describe('routes de l\'application', () => {
  const enfants = routes.find((r) => r.path === '' && r.children)?.children ?? [];

  it('donne au médecin, à son accueil, exactement le composant du planning des séances (vues semaine et jour)', async () => {
    const accueil = enfants.find((r) => r.path === 'medecin');
    const seances = await (await import('./features/seances/seances.routes')).seancesRoutes
      .find((r) => r.path === 'planning')!.loadComponent!();

    expect(accueil).toBeDefined();
    expect(accueil!.canActivate).toHaveLength(1);
    expect(await (accueil!.loadComponent as () => Promise<unknown>)()).toBe(PlanningSemaineComponent);
    expect(seances).toBe(PlanningSemaineComponent);
  });
});
