import {expect, Page, test} from '@playwright/test';

/**
 * Administration des référentiels : saisie, import CSV vérifié (colonnes manquantes, anomalies par ligne),
 * import effectif puis suppression — sur le vrai backend, dans le centre de l'utilisateur connecté.
 */
test.describe('Administration des référentiels', () => {
  const env = (globalThis as unknown as { process?: { env?: Record<string, string | undefined> } }).process?.env ?? {};
  const baseUrl = env['E2E_BASE_URL'] || 'http://127.0.0.1:4200';
  const username = env['E2E_USERNAME'] || 'admin@hemodialyse.fr';
  const password = env['E2E_PASSWORD'] || 'admin123';
  const suffix = Date.now().toString(36).toUpperCase();

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

  async function openTransporteurs(page: Page) {
    await page.goto(`${baseUrl}/admin/parametrage/referentiels`);
    await page.locator('[data-testid="kind-transporteurs"]').click();
    await expect(page.locator('[data-testid="referential-search"]')).toBeVisible();
  }

  function csv(content: string) {
    return {name: 'transporteurs.csv', mimeType: 'text/csv', buffer: Buffer.from(content, 'utf8')};
  }

  test('crée un transporteur au formulaire puis le supprime', async ({page}) => {
    await login(page);
    await openTransporteurs(page);

    await page.locator('[data-testid="referential-add"]').click();
    await page.locator('[data-testid="referential-entry-save"]').click();
    await expect(page.locator('mat-error')).toHaveCount(1); // « Nom » obligatoire

    await page.locator('[data-testid="field-nom"]').fill(`E2E Ambulances ${suffix}`);
    await page.locator('[data-testid="field-telephone"]').fill('0795 00 61 36');
    await page.locator('[data-testid="referential-entry-save"]').click();
    await expect(page.locator('mat-dialog-container')).toHaveCount(0);

    await page.locator('[data-testid="referential-search"]').fill(suffix);
    const row = page.locator('[data-testid="referential-table"] tr.mat-mdc-row', {hasText: suffix});
    await expect(row).toHaveCount(1);

    await row.getByRole('button', {name: /supprimer|delete/i}).click();
    await page.locator('mat-dialog-container').getByRole('button', {name: /supprimer|delete/i}).click();
    await expect(row).toHaveCount(0);
  });

  test('vérifie un fichier, liste ce qui manque puis importe un fichier valide', async ({page}) => {
    await login(page);
    await openTransporteurs(page);
    await page.locator('[data-testid="referential-import"]').click();
    const fileInput = page.locator('[data-testid="import-file-input"]');

    // Colonne obligatoire absente : signalée, rien n'est importable.
    await fileInput.setInputFiles(csv('telephone\n0795006136\n'));
    await expect(page.locator('[data-testid="import-missing-columns"]')).toContainText(/nom|name/i);
    await expect(page.locator('[data-testid="import-confirm"]')).toBeDisabled();

    // Anomalies ligne par ligne : doublon et téléphone invalide.
    await fileInput.setInputFiles(csv(`nom;telephone\nE2E A ${suffix};0795006136\nE2E A ${suffix};0795006137\nE2E B ${suffix};abc\n`));
    const errors = page.locator('[data-testid="import-errors"]');
    await expect(errors).toContainText('3');
    await expect(errors).toContainText('4');
    await expect(page.locator('[data-testid="import-confirm"]')).toBeDisabled();

    // Fichier valide : vérifié puis importé.
    await fileInput.setInputFiles(csv(`nom;telephone\nE2E A ${suffix};0795006136\nE2E B ${suffix};\n`));
    await expect(page.locator('[data-testid="import-valid"]')).toBeVisible();
    await page.locator('[data-testid="import-confirm"]').click();
    await expect(page.locator('[data-testid="import-applied"]')).toBeVisible();
    await page.getByRole('button', {name: /fermer|close/i}).click();

    await page.locator('[data-testid="referential-search"]').fill(suffix);
    const rows = page.locator('[data-testid="referential-table"] tr.mat-mdc-row', {hasText: suffix});
    await expect(rows).toHaveCount(2);

    // Nettoyage.
    for (let i = 0; i < 2; i++) {
      await rows.first().getByRole('button', {name: /supprimer|delete/i}).click();
      await page.locator('mat-dialog-container').getByRole('button', {name: /supprimer|delete/i}).click();
      await expect(rows).toHaveCount(1 - i);
    }
  });

  test('reste utilisable en mobile sans débordement horizontal global', async ({page}) => {
    await page.setViewportSize({width: 360, height: 780});
    await login(page);
    await openTransporteurs(page);

    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
    expect(overflow).toBeLessThanOrEqual(1);
    await expect(page.locator('[data-testid="referential-add"]')).toBeVisible();
  });
});

