import {expect, test} from '@playwright/test';

/**
 * Paramétrage sans développement : l'administrateur ajoute un compte au plan comptable, crée un modèle de pièce, saisit
 * une pièce de ce modèle, la retrouve dans le journal et l'extourne.
 */
test.describe('Plan comptable, modèles et saisie de pièces', () => {
  const baseUrl = process.env.E2E_BASE_URL || 'http://127.0.0.1:4200';
  const username = process.env.E2E_USERNAME || 'admin@hemodialyse.fr';
  const password = process.env.E2E_PASSWORD || 'admin123';
  /** Code propre à l'exécution : le test se rejoue sans heurter un modèle déjà créé. */
  const code = `E2E${Date.now() % 100000}`;

  test.beforeEach(async ({page}) => {
    await page.goto(`${baseUrl}/login`);
    await page.fill('input[name="username"]', username);
    await page.fill('input[name="password"]', password);
    await page.click('button[type="submit"]');
    await page.waitForURL(`${baseUrl}/dashboard`, {timeout: 10000});
  });

  test('ajoute un compte au plan comptable et le retrouve par la recherche', async ({page}) => {
    await page.goto(`${baseUrl}/comptabilite/parametrage`);
    await page.waitForLoadState('networkidle');

    await page.getByTestId('plan-numero').fill('6139');
    await page.getByTestId('plan-libelle').fill('Locations diverses (e2e)');
    await page.getByTestId('plan-save').click();
    await expect(page.getByTestId('plan-succes')).toBeVisible();

    await page.getByTestId('plan-recherche').fill('6139');
    await expect(page.locator('tr[data-compte="6139"]')).toBeVisible();
  });

  test('crée un modèle, saisit une pièce équilibrée puis l\'extourne depuis le journal', async ({page}) => {
    await page.goto(`${baseUrl}/comptabilite/parametrage`);
    await page.waitForLoadState('networkidle');

    await page.getByTestId('modele-code').fill(code);
    await page.getByTestId('modele-libelle').fill('Loyer e2e');
    await page.getByTestId('modele-journal').click();
    await page.locator('mat-option').filter({hasText: '(AC)'}).click();
    await page.getByTestId('modele-compte-0').fill('613');
    await page.getByTestId('modele-compte-1').fill('401');
    await page.getByTestId('modele-save').click();
    await expect(page.getByTestId('modeles-succes')).toBeVisible();
    await expect(page.locator(`tr[data-modele="${code}"]`)).toBeVisible();

    // saisie : la pièce n'est enregistrable qu'équilibrée
    await page.locator(`tr[data-modele="${code}"] a[mat-icon-button]`).click();
    await expect(page).toHaveURL(/\/comptabilite\/pieces/);
    await page.getByTestId('piece-montant-0').fill('1500');
    await page.getByTestId('piece-montant-1').fill('1400');
    await expect(page.getByTestId('piece-save')).toBeDisabled();
    await page.getByTestId('piece-montant-1').fill('1500');
    await page.getByTestId('piece-save').click();
    await expect(page.getByTestId('piece-succes')).toBeVisible();

    // la pièce est dans le journal ; son extourne crée l'écriture inverse
    await page.goto(`${baseUrl}/comptabilite`);
    await page.waitForLoadState('networkidle');
    await expect(page.getByTestId('compta-saisir')).toBeVisible();
    await page.locator('.expand-btn').first().click();
    const extourner = page.locator('[data-testid^="compta-extourner-"]').first();
    await expect(extourner).toBeVisible();
    await extourner.click();
    await page.locator('mat-dialog-container button').last().click();
    await expect(page.getByTestId('compta-extourne-succes')).toBeVisible();
  });

  test('l\'écran de saisie reste utilisable sur un téléphone', async ({page}) => {
    await page.setViewportSize({width: 375, height: 812});
    await page.goto(`${baseUrl}/comptabilite/pieces`);
    await page.waitForLoadState('networkidle');

    const deborde = await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth + 1);
    expect(deborde).toBe(false);
  });
});
