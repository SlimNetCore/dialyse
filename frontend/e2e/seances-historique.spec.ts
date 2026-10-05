import {expect, Page, test} from '@playwright/test';

/**
 * Historique des séances : statistiques + tableau paginé dont la recherche, les filtres et le tri sont faits par le
 * serveur (le test vérifie les paramètres envoyés). Les appels métier sont stubbés ; le login est réel.
 */
test.describe('Historique des séances', () => {
  const env = (globalThis as unknown as { process?: { env?: Record<string, string | undefined> } }).process?.env ?? {};
  const baseUrl = env['E2E_BASE_URL'] || 'http://127.0.0.1:4200';
  const username = env['E2E_USERNAME'] || 'admin@hemodialyse.fr';
  const password = env['E2E_PASSWORD'] || 'admin123';
  const centerId = env['E2E_CENTER_ID'] || '11111111-1111-1111-1111-111111111111';

  async function login(page: Page) {
    await page.goto(`${baseUrl}/login`);
    await page.fill('input[name="username"]', username);
    await page.fill('input[name="password"]', password);
    await page.click('button[type="submit"]');
    await page.waitForURL(`${baseUrl}/dashboard`, {timeout: 12000});
  }

  async function stub(page: Page, requests: URL[]) {
    const json = (body: unknown) => ({status: 200, contentType: 'application/json', body: JSON.stringify(body)});
    await page.route('**/api/v1/seances?*', async (route) => {
      requests.push(new URL(route.request().url()));
      await route.fulfill(json({
        items: [{
          id: 's1', centerId, patientId: 'p1', patientCode: 'PAT-1', patientNom: 'Dupont', patientPrenom: 'Jean',
          dateSeance: '2026-10-03', status: 'VALIDEE', forfait: null,
        }],
        total: 57, page: 0, size: 20,
      }));
    });
    await page.route('**/api/v1/seances/dashboard*', (route) => route.fulfill(json({
      year: 2026,
      month: 10,
      expectedSeances: 50,
      presenceCount: 40,
      absenceCount: 10,
      totalSeances: 40,
      sexeDistribution: {M: 20, F: 20, AUTRE: 0},
      ageDistribution: {'0-17': 0, '18-39': 5, '40-59': 15, '60+': 20, INCONNU: 0},
    })));
  }

  test('affiche les statistiques et la liste paginée, triée par date décroissante', async ({page}) => {
    const requests: URL[] = [];
    await login(page);
    await stub(page, requests);
    await page.goto(`${baseUrl}/seances/historique`);

    await expect(page.locator('.kpi-grid')).toContainText('40');
    await expect(page.locator('app-configurable-list')).toContainText('Dupont');
    await expect(page.locator('mat-paginator')).toContainText('57');
    const first = requests[0];
    expect(first.searchParams.get('page')).toBe('0');
    expect(first.searchParams.get('sortBy')).toBe('dateSeance');
    expect(first.searchParams.get('sortDir')).toBe('desc');
  });

  test('la période est envoyée au serveur', async ({page}) => {
    const requests: URL[] = [];
    await login(page);
    await stub(page, requests);
    await page.goto(`${baseUrl}/seances/historique`);

    await page.locator('.period input[type="date"]').first().fill('2026-10-01');
    await page.locator('.period input[type="date"]').first().blur();

    await expect.poll(() => requests.some((r) => r.searchParams.get('from') === '2026-10-01')).toBe(true);
  });

  for (const viewport of [
    {name: 'mobile', width: 360, height: 780},
    {name: 'tablette', width: 820, height: 1180},
    {name: 'PC', width: 1366, height: 800},
  ]) {
    test(`aucun débordement horizontal sur ${viewport.name}`, async ({page}) => {
      await page.setViewportSize({width: viewport.width, height: viewport.height});
      await login(page);
      await stub(page, []);
      await page.goto(`${baseUrl}/seances/historique`);
      await expect(page.locator('app-configurable-list')).toBeVisible();

      const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
      expect(overflow).toBeLessThanOrEqual(1);
    });
  }
});
