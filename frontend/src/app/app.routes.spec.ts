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

  it('redirige l\'ancienne URL des modèles de documents vers le paramétrage de l\'administration', async () => {
    const ancienne = enfants.find((r) => r.path === 'modeles-document');
    const {adminRoutes} = await import('./features/admin/admin.routes');
    const {ModelesDocumentComponent} = await import('./features/reporting/modeles-document.component');
    const nouvelle = adminRoutes.find((r) => r.path === 'parametrage/modeles-document');

    expect(ancienne).toMatchObject({redirectTo: 'admin/parametrage/modeles-document', pathMatch: 'full'});
    expect(ancienne!.loadComponent).toBeUndefined();
    expect(await (nouvelle!.loadComponent as () => Promise<unknown>)()).toBe(ModelesDocumentComponent);
  });

  it('garde /dashboard (Accueil) comme route d\'arrivée par défaut', () => {
    expect(enfants.find((r) => r.path === 'dashboard')?.loadComponent).toBeDefined();
    expect(enfants.find((r) => r.path === '')).toMatchObject({redirectTo: 'dashboard'});
  });
});
