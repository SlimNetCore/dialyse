import {expect, Page, test} from '@playwright/test';

/**
 * Tests E2E — Accueil du centre (/dashboard, URL conservée) : une tuile par module accessible au profil, chacune menant
 * au tableau de bord du module. Remplace dashboard-month-filter.spec.ts (le filtre de mois et les KPI PEC de l'ancien
 * tableau de bord du centre n'existent plus : les indicateurs PEC sont dans /patients/tableau-de-bord).
 */
test.describe('Accueil du centre', () => {
  const env = (globalThis as unknown as { process?: { env?: Record<string, string | undefined> } }).process?.env ?? {};
  const baseUrl = env['E2E_BASE_URL'] || 'http://127.0.0.1:4200';
  const username = env['E2E_USERNAME'] || 'admin@hemodialyse.fr';
  const password = env['E2E_PASSWORD'] || 'admin123';

  async function login(page: Page) {
    await page.goto(`${baseUrl}/login`);
    await page.fill('input[name="username"]', username);
    await page.fill('input[name="password"]', password);
    await page.click('button[type="submit"]');
    await page.waitForURL(`${baseUrl}/dashboard`, {timeout: 10000});
  }

  test('should show one tile per module with a link to its dashboard', async ({page}) => {
    await login(page);
    await page.waitForLoadState('networkidle');

    await expect(page.locator('[data-testid="accueil-tuile-patients"]')).toBeVisible();
    await expect(page.locator('[data-testid="accueil-tuile-stock"]')).toBeVisible();
    await expect(page.locator('input[type="month"]')).toHaveCount(0);

    await page.locator('[data-testid="accueil-lien-patients"]').click();
    await page.waitForURL(`${baseUrl}/patients/tableau-de-bord`, {timeout: 10000});
  });

  test('should redirect the former document templates URL to the administration settings', async ({page}) => {
    await login(page);
    await page.goto(`${baseUrl}/modeles-document`);
    await page.waitForURL(`${baseUrl}/admin/parametrage/modeles-document`, {timeout: 10000});
  });
});
