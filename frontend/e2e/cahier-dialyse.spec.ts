import {expect, Page, test} from '@playwright/test';

test.describe('Cahier de dialyse', () => {
  const env = (globalThis as unknown as { process?: { env?: Record<string, string | undefined> } }).process?.env ?? {};
  const baseUrl = env['E2E_BASE_URL'] || 'http://127.0.0.1:4200';
  const username = env['E2E_USERNAME'] || 'admin@hemodialyse.fr';
  const password = env['E2E_PASSWORD'] || 'admin123';
  const patientId = env['E2E_PATIENT_ID'];
  const centerId = env['E2E_CENTER_ID'] || '11111111-1111-1111-1111-111111111111';

  test.skip(!patientId, 'E2E_PATIENT_ID est requis pour tester le cahier de dialyse.');

  async function login(page: Page) {
    await page.goto(`${baseUrl}/login`);
    await page.fill('input[name="username"]', username);
    await page.fill('input[name="password"]', password);
    await page.click('button[type="submit"]');
    await page.waitForURL(`${baseUrl}/dashboard`, {timeout: 12000});
  }

  test('affiche une interface type cahier avec navigation de pages', async ({page}) => {
    await login(page);
    await page.goto(`${baseUrl}/patients/${patientId}/cahier`);
    await page.waitForLoadState('networkidle');

    await expect(page.locator('[data-testid="cahier-notebook-page"]')).toBeVisible();
    await expect(page.locator('text=Sommaire des séances')).toBeVisible();

    const pageButtons = page.locator('.toc-item');
    const count = await pageButtons.count();
    expect(count).toBeGreaterThanOrEqual(1);

    await expect(page.locator('button', {hasText: /Page précédente|Page suivante/i}).first()).toBeVisible();
  });

  test('affiche aussi les seances facturees dans le cahier', async ({page}) => {
    await login(page);

    await page.route('**/api/v1/seances?*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          {
            id: 'seance-facturee',
            centerId,
            patientId,
            dateSeance: '2026-08-01',
            status: 'FACTUREE'
          },
          {
            id: 'seance-signee',
            centerId,
            patientId,
            dateSeance: '2026-07-20',
            status: 'SIGNEE'
          }
        ])
      });
    });

    await page.route('**/api/v1/seances/seance-facturee?*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          seance: {
            id: 'seance-facturee',
            centerId,
            patientId,
            dateSeance: '2026-08-01',
            status: 'FACTUREE'
          },
          patient: {id: patientId},
          paramedical: null,
          medical: null,
          consommables: [],
          consommablesTotalValorise: 0,
          forfait: null
        })
      });
    });

    await page.route('**/api/v1/seances/seance-signee?*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          seance: {
            id: 'seance-signee',
            centerId,
            patientId,
            dateSeance: '2026-07-20',
            status: 'SIGNEE'
          },
          patient: {id: patientId},
          paramedical: null,
          medical: null,
          consommables: [],
          consommablesTotalValorise: 0,
          forfait: null
        })
      });
    });

    await page.goto(`${baseUrl}/patients/${patientId}/cahier`);
    await page.waitForLoadState('networkidle');

    await expect(page.locator('.toc-item')).toHaveCount(2);
    await expect(page.locator('.toc-item').first()).toContainText('FACTUREE');
    await expect(page.locator('.page-meta .meta-pill').filter({hasText: 'FACTUREE'})).toBeVisible();
  });
});

