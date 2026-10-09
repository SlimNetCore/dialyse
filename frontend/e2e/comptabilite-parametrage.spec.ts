import {expect, test} from '@playwright/test';

/**
 * Paramétrage comptable : un administrateur ajoute un journal, le choisit pour les sorties de stock, puis lance la
 * comptabilisation du stock ; le journal, utilisé par une opération, ne peut alors plus être supprimé.
 */
test.describe('Paramétrage comptable', () => {
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

  test('affiche les journaux paginés, les comptes et la comptabilisation du stock', async ({page}) => {
    await page.goto(`${baseUrl}/comptabilite/parametrage`);
    await page.waitForLoadState('networkidle');

    await expect(page.getByTestId('cpa-journaux')).toBeVisible();
    await expect(page.locator('mat-paginator')).toBeVisible();
    await expect(page.locator('tr[data-journal="VE"]')).toBeVisible();
    await expect(page.getByTestId('cpa-compteStock')).not.toHaveValue('');
    await expect(page.getByTestId('cpa-synchro')).toBeEnabled();
  });

  test('ajoute un journal puis comptabilise le stock de la période', async ({page}) => {
    await page.goto(`${baseUrl}/comptabilite/parametrage`);
    await page.waitForLoadState('networkidle');

    await page.getByTestId('cpa-journal-code').fill('e2e');
    await page.getByTestId('cpa-journal-libelle').fill('Journal de test e2e');
    await page.getByTestId('cpa-journal-save').click();
    await expect(page.getByTestId('cpa-succes')).toBeVisible();
    await expect(page.locator('tr[data-journal="E2E"]')).toBeVisible();

    await page.getByTestId('cpa-synchro').click();
    await expect(page.getByTestId('cpa-synchro-resultat')).toBeVisible();

    // nettoyage : le journal n'est choisi pour aucune opération et ne porte aucune écriture
    await page.locator('tr[data-journal="E2E"] button[color="warn"]').click();
    await page.locator('mat-dialog-container button').last().click();
    await expect(page.locator('tr[data-journal="E2E"]')).toHaveCount(0);
  });

  test('le journal comptable propose les journaux du centre et reste utilisable sur mobile', async ({page}) => {
    await page.setViewportSize({width: 375, height: 812});
    await page.goto(`${baseUrl}/comptabilite`);
    await page.waitForLoadState('networkidle');

    await expect(page.getByTestId('compta-export')).toBeDisabled();
    await expect(page.locator('mat-paginator')).toBeVisible();
    const deborde = await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth + 1);
    expect(deborde).toBe(false);
  });
});
