import {expect, Page, test} from '@playwright/test';

test.describe('Module facturation', () => {
  const env = (globalThis as unknown as {process?: {env?: Record<string, string | undefined>}}).process?.env ?? {};
  const baseUrl = env['E2E_BASE_URL'] || 'http://127.0.0.1:4200';
  const username = env['E2E_USERNAME'] || 'admin@hemodialyse.fr';
  const password = env['E2E_PASSWORD'] || 'admin123';

  async function login(page: Page) {
    await page.goto(`${baseUrl}/login`);

    const centerCombo = page.locator('[data-testid="login-center-select"]');
    await centerCombo.click({force: true});
    await page.getByRole('option', {name: /annaba|rouiba/i}).first().click();

    await page.getByRole('textbox', {name: /nom d'utilisateur|username/i}).fill(username);
    await page.getByRole('textbox', {name: /mot de passe|password/i}).fill(password);
    await page.getByRole('button', {name: /se connecter|sign in/i}).click();
    await page.waitForURL(`${baseUrl}/dashboard`, {timeout: 10000});
  }

  test('affiche le shell facturation et les sections principales', async ({page}) => {
    await login(page);
    await page.goto(`${baseUrl}/facturation`);
    await page.waitForLoadState('networkidle');

    await expect(page.locator('h1, .app-section-title')).toContainText(/facturation|billing/i);
    await expect(page.locator('.flow-grid')).toBeVisible();
    await expect(page.locator('.dashboard-grids')).toBeVisible();
  });

  test('reste utilisable en mobile sans debordement horizontal global', async ({page}) => {
    await page.setViewportSize({width: 360, height: 780});
    await login(page);
    await page.goto(`${baseUrl}/facturation`);
    await page.waitForLoadState('networkidle');

    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
    expect(overflow).toBeLessThanOrEqual(1);
    await expect(page.locator('.filters-grid')).toBeVisible();
  });

  test('ouvre le parametrage facturation depuis administration', async ({page}) => {
    await login(page);
    await page.goto(`${baseUrl}/admin/parametrage/facturation`);
    await page.waitForLoadState('networkidle');

    await expect(page.locator('h1, .app-section-title')).toContainText(/paramétrage facturation|billing settings/i);
    await expect(page.locator('input[type="number"], input[type="text"]')).toHaveCount(2);
  });

  test('affiche l action impression de la synthese mensuelle', async ({page}) => {
    await login(page);
    await page.goto(`${baseUrl}/facturation`);
    await page.waitForLoadState('networkidle');

    await expect(page.getByRole('button', {name: /imprimer la synthese mensuelle|print monthly summary/i})).toBeVisible();
  });
});
