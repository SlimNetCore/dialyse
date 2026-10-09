import {expect, test} from '@playwright/test';

/**
 * Cloche de notifications : chaque notification porte un libellé, un message et une action qui mène à l'écran où la
 * traiter ; aucun code technique ni contenu brut n'est affiché.
 */
test.describe('Notifications et leurs actions', () => {
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

  test('chaque notification affichée a un libellé lisible et une action', async ({page}) => {
    await page.locator('app-notification-bell .notif-btn').click();
    await expect(page.locator('.notif-panel')).toBeVisible();
    for (const onglet of [0, 1]) {
      await page.locator('.notif-panel .tab').nth(onglet).click();
      const lignes = page.getByTestId('notif-item');
      for (let i = 0; i < await lignes.count(); i++) {
        const ligne = lignes.nth(i);
        await expect(ligne.locator('.message-title')).not.toHaveText(/^[A-Z_]+$/);
        await expect(ligne.locator('.message-text')).not.toBeEmpty();
        await expect(ligne.getByTestId('notif-item-action')).toBeVisible();
      }
    }
    await expect(page.locator('.notif-panel pre')).toHaveCount(0);
  });

  test('l\'action d\'une notification ouvre son écran et ferme le panneau', async ({page}) => {
    await page.locator('app-notification-bell .notif-btn').click();
    await page.locator('.notif-panel .tab').nth(1).click();
    const actions = page.getByTestId('notif-item-action');
    if (await actions.count() === 0) {
      await page.locator('.notif-panel .tab').nth(0).click();
    }
    test.skip(await actions.count() === 0, 'aucune notification dans le centre de démonstration');

    const cible = await actions.first().getAttribute('href');
    await actions.first().click();

    await expect(page.locator('.notif-panel')).toHaveCount(0);
    expect(page.url()).toContain((cible ?? '').split('?')[0]);
  });

  test('sur un téléphone, le panneau tient dans l\'écran et l\'action reste sous chaque notification', async ({page}) => {
    await page.setViewportSize({width: 375, height: 812});
    await page.locator('app-notification-bell .notif-btn').click();

    await expect(page.locator('.notif-panel')).toBeVisible();
    await expect(page.locator('.message-detail')).toBeHidden();
    const deborde = await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth + 1);
    expect(deborde).toBe(false);
  });
});
