import {expect, test} from '@playwright/test';

test.describe('Reglement module', () => {
  const baseUrl = process.env.E2E_BASE_URL || 'http://127.0.0.1:4200';
  const username = process.env.E2E_USERNAME || 'admin@hemodialyse.fr';
  const password = process.env.E2E_PASSWORD || 'admin123';

  test.beforeEach(async ({page}) => {
    await page.goto(`${baseUrl}/login`);
    await page.fill('input[name="username"]', username);
    await page.fill('input[name="password"]', password);
    await page.click('button[type="submit"]');
    await page.waitForURL(`${baseUrl}/dashboard`, {timeout: 10000});
  });

  test('should display filters and paginator on reglement page', async ({page}) => {
    await page.goto(`${baseUrl}/reglement`);
    await page.waitForLoadState('networkidle');

    await expect(page.locator('text=Règlement').or(page.locator('text=Payments'))).toBeVisible();
    await expect(page.locator('[data-testid="reglement-table"] ng-table-paginator')).toBeVisible();
    await expect(page.locator('input[matinput], input[matInput]').first()).toBeVisible();
    // les indicateurs ont rejoint le tableau de bord : plus de graphiques sur l'écran de saisie
    await expect(page.locator('canvas')).toHaveCount(0);
  });

  test('should display KPIs and charts on reglement dashboard', async ({page}) => {
    await page.goto(`${baseUrl}/reglement/tableau-de-bord`);
    await page.waitForLoadState('networkidle');

    await expect(page.locator('.kpi-grid .app-data-pill')).toHaveCount(6);
    await expect(page.locator('canvas')).toHaveCount(2);
    await expect(page.locator('mat-paginator, ng-table-paginator')).toHaveCount(0);
  });
});

