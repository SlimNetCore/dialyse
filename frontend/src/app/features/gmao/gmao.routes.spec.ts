import {TestBed} from '@angular/core/testing';
import {provideZonelessChangeDetection} from '@angular/core';
import {provideRouter, Router} from '@angular/router';
import {beforeEach, describe, expect, it} from 'vitest';
import {GMAO_ROUTES} from './gmao.routes';
import {GmaoDashboardComponent} from './components/dashboard/dashboard.component';

describe('routes GMAO', () => {
  let router: Router;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([{path: 'gmao', children: GMAO_ROUTES}]),
      ],
    });
    router = TestBed.inject(Router);
  });

  it('l\'arrivée dans le module GMAO mène à l\'écran de travail des équipements', async () => {
    await router.navigateByUrl('/gmao');

    expect(router.url).toBe('/gmao/equipements');
  });

  it('l\'ancienne URL du tableau de bord redirige vers /gmao/tableau-de-bord', async () => {
    await router.navigateByUrl('/gmao/dashboard');

    expect(router.url).toBe('/gmao/tableau-de-bord');
  });

  it('le tableau de bord GMAO est servi sur /gmao/tableau-de-bord', () => {
    const enfants = GMAO_ROUTES[0].children ?? [];

    expect(enfants.find((r) => r.path === 'tableau-de-bord')?.component).toBe(GmaoDashboardComponent);
  });
});
