import {describe, expect, it} from 'vitest';
import {adminRoutes} from './admin.routes';
import {centreAdminGuard} from '../../core/auth/admin.guard';

describe('routes de l\'administration', () => {
  it('ouvre les clôtures mensuelles dans le paramétrage, réservées à l\'ADMIN du centre', async () => {
    const route = adminRoutes.find((r) => r.path === 'parametrage/clotures');
    const {CloturesMensuellesComponent} = await import('./clotures-mensuelles/clotures-mensuelles.component');

    expect(route).toBeDefined();
    expect(route!.canActivate).toEqual([centreAdminGuard]);
    expect(await (route!.loadComponent as () => Promise<unknown>)()).toBe(CloturesMensuellesComponent);
  });
});
