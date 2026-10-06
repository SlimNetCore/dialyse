import {expect, Page, test} from '@playwright/test';

/**
 * Planning de la semaine : une séance validée le lundi reste visible là où elle a eu lieu même si les jours du patient
 * sont ensuite passés au mercredi ; la séance du mercredi est signalée (peut-être en trop) ; une séance sans case est
 * listée à part. Le login est réel ; le planning de la semaine et le suivi des séances sont stubbés.
 */
test.describe('Planning — séances réalisées hors du planning actuel', () => {
  const env = (globalThis as unknown as { process?: { env?: Record<string, string | undefined> } }).process?.env ?? {};
  const baseUrl = env['E2E_BASE_URL'] || 'http://localhost:4200';
  const username = env['E2E_USERNAME'] || 'admin';
  const password = env['E2E_PASSWORD'] || 'admin$$2026dz';

  const JOURS = ['DIMANCHE', 'LUNDI', 'MARDI', 'MERCREDI', 'JEUDI', 'VENDREDI', 'SAMEDI'];
  const date = (i: number) => `2026-10-0${4 + i}`;
  const occupant = (extra: Record<string, unknown>) => ({
    patientId: 'p1', nom: 'Karim B.', generateurCode: 'G01', aRisque: false, libereLe: null, temporaire: false,
    realiseeHorsPlanning: false, dejaRealiseeLe: null, ...extra,
  });
  const semaine = {
    debut: date(0), fin: date(6),
    jours: JOURS.map((jour, i) => ({jour, date: date(i), ouvertHebdomadaire: true, fermetureMotif: null})),
    salles: [{id: 's1', nom: 'Salle A'}],
    creneaux: [{id: 'c1', libelle: 'Matin', ordre: 1}],
    cellules: JOURS.map((jour) => ({
      salleId: 's1', creneauId: 'c1', jour, capacite: 2,
      occupants: jour === 'LUNDI' ? [occupant({realiseeHorsPlanning: true})]
        : jour === 'MERCREDI' ? [occupant({generateurCode: 'G02', dejaRealiseeLe: date(1)})] : [],
    })),
    conflits: [],
    patientsAReplanifier: 0,
    seancesSansCase: [{patientId: 'p9', nom: 'Nadia E.', date: date(2), jour: 'MARDI'}],
  };

  async function login(page: Page) {
    await page.goto(`${baseUrl}/login`);
    await page.click('[data-testid="login-societe-select"]');
    await page.locator('mat-option').first().click();
    await page.click('[data-testid="login-center-select"]');
    await page.locator('mat-option').first().click();
    await page.fill('[data-testid="login-username"]', username);
    await page.fill('[data-testid="login-password"]', password);
    await page.click('[data-testid="login-submit"]');
    await page.waitForURL(/\/dashboard/, {timeout: 20000});
  }

  async function stub(page: Page) {
    const json = (body: unknown) => ({status: 200, contentType: 'application/json', body: JSON.stringify(body)});
    await page.route('**/api/v1/planning/semaine?*', (route) => route.fulfill(json(semaine)));
    await page.route('**/api/v1/absences-patients/semaine?*', (route) => route.fulfill(json({
      absences: [], seancesRealisees: [{patientId: 'p1', dateSeance: date(1)}],
    })));
  }

  test('montre la séance du lundi là où elle a eu lieu et signale la séance du mercredi', async ({page}) => {
    await login(page);
    await stub(page);
    await page.goto(`${baseUrl}/seances/planning`);

    await expect(page.locator('[data-testid="week-hors-planning"]').first()).toBeVisible({timeout: 15_000});
    await expect(page.locator('[data-testid="week-deja-realisee"]').first()).toBeVisible();
    await expect(page.locator('[data-testid="week-sans-case"]')).toContainText('Nadia E.');
    await expect(page.locator('.chip.done.hors').first()).toBeVisible();
  });

  test('reste lisible sans défilement horizontal de la page (mobile)', async ({page}) => {
    await page.setViewportSize({width: 375, height: 800});
    await login(page);
    await stub(page);
    await page.goto(`${baseUrl}/seances/planning`);
    await expect(page.locator('[data-testid="week-sans-case"]')).toBeVisible({timeout: 15_000});

    const debordements = await page.evaluate(() => {
      const largeur = window.innerWidth;
      return Array.from(document.querySelectorAll<HTMLElement>('[data-testid="week-sans-case"], .week-warn *'))
        .filter((el) => el.getBoundingClientRect().right > largeur + 1)
        .map((el) => el.tagName.toLowerCase());
    });
    expect(debordements).toEqual([]);
  });
});
