import {expect, Page, test} from '@playwright/test';

/**
 * Tests E2E — Stock : tableau de bord (/stock/tableau-de-bord) et état du stock (/stock, écran de travail)
 * Couvre : affichage initial et stabilité responsive sur mobile.
 */
test.describe('Tableau de bord et état du stock', () => {
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

  test('should display the stock dashboard with its filters and no stock table', async ({page}) => {
    await login(page);
    await page.goto(`${baseUrl}/stock/tableau-de-bord`);
    await page.waitForLoadState('networkidle');

    await expect(page.locator('h1, .app-section-title').first()).toContainText(/stock/i);
    await expect(page.locator('.filters-grid')).toBeVisible();
    await expect(page.locator('.kpi-grid')).toBeVisible();
    await expect(page.locator('app-configurable-list, ng-table')).toHaveCount(0);
  });

  test('should land on the stock status with the valued stock table', async ({page}) => {
    await login(page);
    await page.goto(`${baseUrl}/stock`);
    await page.waitForLoadState('networkidle');

    await expect(page.locator('[data-testid="stock-table"] ng-table')).toBeVisible();
    await expect(page.locator('[data-testid="stock-table"] ng-table-paginator')).toBeVisible();
    await expect(page.locator('.filters-grid')).toHaveCount(0);
  });

  test('should stay within the viewport on mobile', async ({page}) => {
    await page.setViewportSize({width: 320, height: 844});
    await login(page);
    for (const url of ['/stock/tableau-de-bord', '/stock']) {
      await page.goto(`${baseUrl}${url}`);
      await page.waitForLoadState('networkidle');

      const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
      expect(overflow, url).toBeLessThanOrEqual(1);
    }
  });
});
