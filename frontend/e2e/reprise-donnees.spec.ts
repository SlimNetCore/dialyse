import {expect, Page, test} from '@playwright/test';

/**
 * Reprise des données : ouverture du lot, vérification d'un fichier (colonnes manquantes, valeur inconnue
 * associée à l'écran), import des assurés et des patients, puis annulation du lot (nettoyage).
 * S'exécute sur le vrai backend, dans le centre de l'utilisateur connecté (aucun lot ne doit y être en cours).
 */
test.describe('Reprise de données', () => {
  const env = (globalThis as unknown as { process?: { env?: Record<string, string | undefined> } }).process?.env ?? {};
  const baseUrl = env['E2E_BASE_URL'] || 'http://127.0.0.1:4200';
  const username = env['E2E_USERNAME'] || 'admin@hemodialyse.fr';
  const password = env['E2E_PASSWORD'] || 'admin123';
  const suffix = Date.now().toString(36).toUpperCase();

  async function login(page: Page) {
    await page.goto(`${baseUrl}/login`);
    await page.locator('[data-testid="login-center-select"]').click({force: true});
    await page.getByRole('option', {name: /annaba|rouiba/i}).first().click();
    await page.getByRole('textbox', {name: /nom d'utilisateur|username/i}).fill(username);
    await page.getByRole('textbox', {name: /mot de passe|password/i}).fill(password);
    await page.getByRole('button', {name: /se connecter|sign in/i}).click();
    await page.waitForURL(`${baseUrl}/dashboard`, {timeout: 10000});
  }

  function csv(name: string, content: string) {
    return {name, mimeType: 'text/csv', buffer: Buffer.from(content, 'utf8')};
  }

  test('vérifie, corrige et importe assurés et patients, puis annule le lot', async ({page}) => {
    await login(page);
    await page.goto(`${baseUrl}/admin/parametrage/reprise`);

    await page.locator('[data-testid="migration-libelle"]').fill(`E2E reprise ${suffix}`);
    await page.locator('[data-testid="migration-open"]').click();
    await expect(page.locator('[data-testid="migration-batch-status"]')).toBeVisible();

    // Assurés.
    await page.locator('[data-testid="migration-file-assures"]').setInputFiles(
      csv('assures.csv', `legacy_id;numero_assurance;nom;prenom\nA-${suffix};E2E${suffix}A;E2E;Assure\n`));
    await expect(page.locator('[data-testid="migration-report-assures"] [data-testid="migration-valid"]')).toBeVisible();
    await page.locator('[data-testid="migration-import-assures"]').click();
    await expect(page.locator('[data-testid="migration-report-assures"] [data-testid="migration-applied"]')).toBeVisible();

    // Patients : colonne obligatoire manquante.
    await page.locator('[data-testid="migration-file-patients"]').setInputFiles(
      csv('incomplet.csv', 'Nom;Prénom\nE2E;Patient\n'));
    await expect(page.locator('[data-testid="migration-missing-columns"]')).toBeVisible();
    await expect(page.locator('[data-testid="migration-import-patients"]')).toBeDisabled();

    // Patients : valeur inconnue « Z » associée à l'écran, puis import.
    const patients = csv('patients.csv',
      "Identifiant d'origine;N° d'assurance;Nom;Prénom;Sexe;Date de naissance;Date d'admission;État;Qualité;N° d'assurance de l'assuré\n"
      + `P-${suffix};E2E${suffix}P;E2E;Patient;M;15/03/1962;02/01/2019;Z;Enfant;E2E${suffix}A\n`);
    await page.locator('[data-testid="migration-file-patients"]').setInputFiles(patients);
    const report = page.locator('[data-testid="migration-report-patients"]');
    await expect(report.locator('[data-testid="migration-invalid"]')).toBeVisible();
    await report.locator('[data-testid="map-select-2"]').click();
    await page.getByRole('option').first().click();
    await report.locator('[data-testid="map-button-2"]').click();
    await expect(report.locator('[data-testid="migration-valid"]')).toBeVisible();
    await page.locator('[data-testid="migration-import-patients"]').click();
    await expect(report.locator('[data-testid="migration-applied"]')).toBeVisible();

    // Nettoyage : annulation du lot (les données créées sont supprimées).
    await page.locator('[data-testid="migration-cancel"]').click();
    await page.locator('mat-dialog-container').getByRole('button').last().click();
    await expect(page.locator('[data-testid="migration-batch-status"]')).toContainText(/annul|cancel/i);
  });

  test('reste utilisable en mobile sans débordement horizontal global', async ({page}) => {
    await page.setViewportSize({width: 360, height: 780});
    await login(page);
    await page.goto(`${baseUrl}/admin/parametrage/reprise`);
    await page.waitForLoadState('networkidle');

    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
    expect(overflow).toBeLessThanOrEqual(1);
  });
});

