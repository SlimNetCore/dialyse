import {describe, expect, it} from 'vitest';
import {adminRoutes} from './admin.routes';
import {adminGuard, centreAdminGuard} from '../../core/auth/admin.guard';

describe('routes de l\'administration', () => {
  it('ouvre les clôtures mensuelles dans le paramétrage, réservées à l\'ADMIN du centre', async () => {
    const route = adminRoutes.find((r) => r.path === 'parametrage/clotures');
    const {CloturesMensuellesComponent} = await import('./clotures-mensuelles/clotures-mensuelles.component');

    expect(route).toBeDefined();
    expect(route!.canActivate).toEqual([centreAdminGuard]);
    expect(await (route!.loadComponent as () => Promise<unknown>)()).toBe(CloturesMensuellesComponent);
  });

  it('ouvre le paramétrage comptable dans le paramétrage, réservé à l\'ADMIN ; l\'ancienne URL y redirige', async () => {
    const route = adminRoutes.find((r) => r.path === 'parametrage/comptabilite');
    const {ComptabiliteParametrageComponent} = await import('../comptabilite/comptabilite-parametrage.component');
    const {comptabiliteRoutes} = await import('../comptabilite/comptabilite.routes');

    expect(route).toBeDefined();
    expect(route!.canActivate).toEqual([adminGuard]);
    expect(await (route!.loadComponent as () => Promise<unknown>)()).toBe(ComptabiliteParametrageComponent);
    expect(comptabiliteRoutes.find((r) => r.path === 'parametrage')?.redirectTo).toBe('/admin/parametrage/comptabilite');
  });
});
