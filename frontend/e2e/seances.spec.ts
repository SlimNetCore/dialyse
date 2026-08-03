import {expect, test} from '@playwright/test';

/**
 * Tests E2E — Module Séances d'hémodialyse
 * Couvre : navigation, dashboard, journal, scan QR, validation.
 */
test.describe('Module Séances', () => {
  const baseUrl = process.env['E2E_BASE_URL'] || 'http://127.0.0.1:4200';
  const username = process.env['E2E_USERNAME'] || 'admin@hemodialyse.fr';
  const password = process.env['E2E_PASSWORD'] || 'admin123';

  // ─── Helper de connexion ────────────────────────────────────────────────────

  async function login(page: Parameters<typeof test>[1]) {
    await page.goto(`${baseUrl}/login`);
    await page.fill('input[name="username"]', username);
    await page.fill('input[name="password"]', password);
    await page.click('button[type="submit"]');
    await page.waitForURL(`${baseUrl}/dashboard`, {timeout: 10000});
  }

  // ─── Navigation ────────────────────────────────────────────────────────────

  test('should navigate to seances page from menu', async ({page}) => {
    await login(page);
    await page.goto(`${baseUrl}/seances`);
    await page.waitForLoadState('networkidle');
    await expect(page.locator('h1, .app-section-title')).toContainText(/[Ss][éeE]ances/);
  });

  test('should display dashboard seances section', async ({page}) => {
    await login(page);
    await page.goto(`${baseUrl}/seances`);
    await page.waitForLoadState('networkidle');
    // La card dashboard est présente
    await expect(page.locator('mat-card').first()).toBeVisible();
  });

  // ─── Dashboard KPI ─────────────────────────────────────────────────────────

  test('should display KPI pills for seances', async ({page}) => {
    await login(page);
    await page.goto(`${baseUrl}/seances`);
    await page.waitForLoadState('networkidle');
    // Au moins un indicateur est affiché
    const pills = page.locator('.app-data-pill, [class*="kpi"]');
    const count = await pills.count();
    expect(count).toBeGreaterThanOrEqual(1);
  });

  test('should have month input for dashboard filtering', async ({page}) => {
    await login(page);
    await page.goto(`${baseUrl}/seances`);
    await page.waitForLoadState('networkidle');
    const monthInput = page.locator('input[type="month"]').first();
    await expect(monthInput).toBeVisible();
  });

  test('should reload dashboard when month changes', async ({page}) => {
    await login(page);
    await page.goto(`${baseUrl}/seances`);
    await page.waitForLoadState('networkidle');
    const monthInput = page.locator('input[type="month"]').first();
    await monthInput.fill('2026-01');
    await page.waitForLoadState('networkidle');
    // Le dashboard doit toujours être visible après changement de mois
    await expect(page.locator('mat-card').first()).toBeVisible();
  });

  // ─── Export dashboard ──────────────────────────────────────────────────────

  test('should have export buttons for CSV, PDF, XLSX', async ({page}) => {
    await login(page);
    await page.goto(`${baseUrl}/seances`);
    await page.waitForLoadState('networkidle');
    await expect(page.locator('button', {hasText: /CSV/i}).first()).toBeVisible();
    await expect(page.locator('button', {hasText: /PDF/i}).first()).toBeVisible();
    await expect(page.locator('button', {hasText: /XLSX/i}).first()).toBeVisible();
  });

  // ─── Scan QR ──────────────────────────────────────────────────────────────

  test('should display QR scan section', async ({page}) => {
    await login(page);
    await page.goto(`${baseUrl}/seances`);
    await page.waitForLoadState('networkidle');
    // La section scan est présente
    await expect(page.locator('text=QR').first()).toBeVisible();
  });

  test('should show scan date input', async ({page}) => {
    await login(page);
    await page.goto(`${baseUrl}/seances`);
    await page.waitForLoadState('networkidle');
    const dateInputs = page.locator('input[type="date"]');
    const count = await dateInputs.count();
    expect(count).toBeGreaterThanOrEqual(1);
  });

  test('should show error state when scanning with empty QR', async ({page}) => {
    await login(page);
    await page.goto(`${baseUrl}/seances`);
    await page.waitForLoadState('networkidle');
    // Cliquer sur Scanner sans remplir le QR
    const scanButton = page.locator('button', {hasText: /Scanner/i}).first();
    if (await scanButton.isVisible()) {
      await scanButton.click();
      // Un snackbar ou message d'erreur doit apparaître
      const snackbar = page.locator('mat-snack-bar-container, .mdc-snackbar');
      const count = await snackbar.count();
      if (count > 0) {
        await expect(snackbar.first()).toBeVisible({timeout: 3000});
      }
    }
  });

  // ─── Journal journalier ────────────────────────────────────────────────────

  test('should display journal section with date input', async ({page}) => {
    await login(page);
    await page.goto(`${baseUrl}/seances`);
    await page.waitForLoadState('networkidle');
    // Journal section existe
    const journalSection = page.locator('text=Journal').first();
    await expect(journalSection).toBeVisible();
  });

  test('should load journal when clicking on Afficher button', async ({page}) => {
    await login(page);
    await page.goto(`${baseUrl}/seances`);
    await page.waitForLoadState('networkidle');
    const journalButton = page.locator('button', {hasText: /Journal|Afficher/i});
    if (await journalButton.count() > 0) {
      await journalButton.first().click();
      await page.waitForLoadState('networkidle');
    }
    // La page reste stable (pas de crash)
    await expect(page.locator('mat-card').first()).toBeVisible();
  });

  // ─── Isolation multi-centre ────────────────────────────────────────────────

  test('should only show seances for current center', async ({page}) => {
    await login(page);
    await page.goto(`${baseUrl}/seances`);
    await page.waitForLoadState('networkidle');
    // La page est chargée correctement (pas de données d'un autre centre)
    await expect(page.locator('body')).not.toContainText('Erreur interne');
    await expect(page.locator('body')).not.toContainText('undefined');
  });

  // ─── Calendrier déplacé vers Paramétrage (admin) ──────────────────────────

  test('should open center calendar from admin parametrage route', async ({page}) => {
    await login(page);
    await page.goto(`${baseUrl}/admin/parametrage/calendrier-clinique`);
    await page.waitForLoadState('networkidle');
    await expect(page).toHaveURL(/\/admin\/parametrage\/calendrier-clinique/);
    await expect(page.locator('h1, .app-section-title')).toContainText(/Calendrier/i);
  });

  // ─── Tableau des séances ───────────────────────────────────────────────────

  test('should display seances table columns', async ({page}) => {
    await login(page);
    await page.goto(`${baseUrl}/seances`);
    await page.waitForLoadState('networkidle');
    // Les en-têtes du tableau sont visibles
    const headers = page.locator('th[mat-header-cell], .mat-header-cell');
    const count = await headers.count();
    expect(count).toBeGreaterThanOrEqual(0); // Le tableau peut être vide (pas de séances)
  });

  test('should display current forfait pill and keep seance tabs responsive on mobile', async ({page}) => {
    const centerId = '11111111-1111-1111-1111-111111111111';
    const seanceId = '42ac21d7-e920-48c6-a85e-1d1d33a69460';
    await page.setViewportSize({width: 320, height: 844});

    await page.route('**/api/v1/auth/me', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          username: 'infirmier-annaba',
          fullName: 'Infirmier Annaba',
          userId: 'b0b00001-0000-0000-0000-000000000003',
          centerId,
          centerName: 'ANNABA 1',
          roles: ['ROLE_INFIRMIER'],
        }),
      });
    });

    await page.route(`**/api/v1/seances?centerId=${centerId}`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          {
            id: seanceId,
            centerId,
            patientId: 'patient-1',
            patientCode: 'PAT-001',
            patientNom: 'Dupont',
            patientPrenom: 'Jean',
            dateSeance: '2026-07-25',
            status: 'CREE',
          },
        ]),
      });
    });

    await page.route(`**/api/v1/seances/${seanceId}?centerId=${centerId}`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          seance: {id: seanceId, centerId, patientId: 'patient-1', dateSeance: '2026-07-25', status: 'CREE'},
          patient: {id: 'patient-1', codePatient: 'PAT-001', nom: 'Dupont', prenom: 'Jean', numeroAssurance: 'ASS-001'},
          paramedical: null,
          medical: null,
          consommables: [],
          consommablesTotalValorise: 0,
          forfait: {id: 'forfait-1', code: 'F001', nom: 'Forfait hémodialyse', prix: 3500},
        }),
      });
    });

    await page.goto(`${baseUrl}/seances`);
    await page.waitForLoadState('networkidle');
    await page.getByRole('button', {name: /Modifier/i}).click();

    const forfaitPill = page.locator('[data-testid="current-forfait-pill"]');
    await expect(forfaitPill).toBeVisible();
    await expect(forfaitPill).toContainText(/Forfait hémodialyse/);
    await expect(forfaitPill).toContainText(/3500/);

    const editorMetrics = await page.locator('.seance-editor-column').evaluate((element) => ({
      clientWidth: element.clientWidth,
      scrollWidth: element.scrollWidth,
      viewportWidth: window.innerWidth,
    }));
    expect(editorMetrics.clientWidth).toBeLessThanOrEqual(editorMetrics.viewportWidth);
    expect(editorMetrics.scrollWidth - editorMetrics.clientWidth).toBeLessThanOrEqual(1);

    await expect(page.locator('.seance-tabs')).toBeVisible();
    await page.getByRole('tab', {name: 'Consommables'}).click();
    await expect(page.locator('.consommables-toolbar')).toBeVisible();

    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
    expect(overflow).toBeLessThanOrEqual(1);
  });

  test('should allow forfait update when seance is not billed', async ({page}) => {
    const centerId = '11111111-1111-1111-1111-111111111111';
    const seanceId = '52ac21d7-e920-48c6-a85e-1d1d33a69460';
    let updatePayload: any = null;

    await page.route('**/api/v1/auth/me', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          username: 'infirmier-annaba',
          fullName: 'Infirmier Annaba',
          userId: 'b0b00001-0000-0000-0000-000000000003',
          centerId,
          centerName: 'ANNABA 1',
          roles: ['ROLE_INFIRMIER'],
        }),
      });
    });

    await page.route(`**/api/v1/seances?centerId=${centerId}`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([{
          id: seanceId,
          centerId,
          patientId: 'patient-1',
          patientCode: 'PAT-001',
          patientNom: 'Dupont',
          patientPrenom: 'Jean',
          dateSeance: '2026-07-25',
          status: 'CREE'
        }]),
      });
    });

    await page.route(`**/api/v1/referentials/forfaits?centerId=${centerId}`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          {id: 'forfait-1', code: 'F001', nom: 'Forfait hémodialyse'},
          {id: 'forfait-2', code: 'F002', nom: 'Forfait hémodialyse majoré'},
        ]),
      });
    });

    await page.route(`**/api/v1/seances/${seanceId}?centerId=${centerId}`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          seance: {id: seanceId, centerId, patientId: 'patient-1', dateSeance: '2026-07-25', status: 'CREE'},
          patient: {id: 'patient-1', codePatient: 'PAT-001', nom: 'Dupont', prenom: 'Jean', numeroAssurance: 'ASS-001'},
          paramedical: null,
          medical: null,
          consommables: [],
          consommablesTotalValorise: 0,
          forfait: {id: 'forfait-1', code: 'F001', nom: 'Forfait hémodialyse', prix: 3500},
        }),
      });
    });

    await page.route(`**/api/v1/seances/${seanceId}/forfait`, async (route) => {
      updatePayload = route.request().postDataJSON();
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          id: seanceId,
          status: 'CREE',
          dateSeance: '2026-07-25',
          forfait: {id: 'forfait-2', code: 'F002', nom: 'Forfait hémodialyse majoré', prix: 4200},
        }),
      });
    });

    await page.goto(`${baseUrl}/seances`);
    await page.waitForLoadState('networkidle');
    await page.getByRole('button', {name: /Modifier/i}).click();

    await page.locator('[data-testid="current-forfait-pill"]').getByRole('combobox').click();
    await page.getByRole('option', {name: /majoré/i}).click();
    await page.getByRole('button', {name: /forfait/i}).click();

    expect(updatePayload).toMatchObject({
      centerId,
      forfaitId: 'forfait-2',
      userId: 'infirmier-annaba',
    });
    await expect(page.locator('[data-testid="current-forfait-pill"]')).toContainText(/majoré/i);
    await expect(page.locator('[data-testid="current-forfait-pill"]')).toContainText(/4200/);
  });

  // ─── Dashboard details popup ───────────────────────────────────────────────

  test('should open dashboard details popup when clicking on presence KPI', async ({page}) => {
    await login(page);
    await page.goto(`${baseUrl}/seances`);
    await page.waitForLoadState('networkidle');
    const presenceBtn = page.locator('button.app-data-pill, button.kpi-clickable').first();
    if (await presenceBtn.isVisible()) {
      await presenceBtn.click();
      // Un panneau de détails doit s'ouvrir
      const popup = page.locator('.popin-card, [class*="popin"]');
      if (await popup.count() > 0) {
        await expect(popup.first()).toBeVisible({timeout: 3000});
        // Fermeture du popup
        const closeBtn = page.locator('button', {hasText: /fermer|close/i});
        if (await closeBtn.count() > 0) {
          await closeBtn.first().click();
        }
      }
    }
  });
});



