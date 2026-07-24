import {expect, test} from '@playwright/test';

test.describe('Cahier de dialyse', () => {
  const baseUrl = process.env['E2E_BASE_URL'] || 'http://127.0.0.1:4200';
  const username = process.env['E2E_USERNAME'] || 'admin@hemodialyse.fr';
  const password = process.env['E2E_PASSWORD'] || 'admin123';
  const patientId = process.env['E2E_PATIENT_ID'];

  test.skip(!patientId, 'E2E_PATIENT_ID est requis pour tester le cahier de dialyse.');

  async function login(page: Parameters<typeof test>[1]) {
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
});

