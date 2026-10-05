import {expect, Page, test} from '@playwright/test';

/**
 * Poste infirmier : scan → séance validée directement, file du jour, consommables en un toucher (un seul POST par
 * touche, sans « Enregistrer »), et affichage responsive mobile / tablette / PC. Les appels métier sont stubbés pour
 * fiabiliser le scénario ; le login et la navigation applicative sont réels.
 */
test.describe('Poste infirmier', () => {
  const env = (globalThis as unknown as { process?: { env?: Record<string, string | undefined> } }).process?.env ?? {};
  const baseUrl = env['E2E_BASE_URL'] || 'http://127.0.0.1:4200';
  const username = env['E2E_USERNAME'] || 'admin@hemodialyse.fr';
  const password = env['E2E_PASSWORD'] || 'admin123';
  const centerId = env['E2E_CENTER_ID'] || '11111111-1111-1111-1111-111111111111';
  const seanceId = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa';
  const patientId = 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb';
  const articleId = 'cccccccc-cccc-cccc-cccc-cccccccccccc';
  const today = new Date().toISOString().slice(0, 10);

  async function login(page: Page) {
    await page.goto(`${baseUrl}/login`);
    await page.fill('input[name="username"]', username);
    await page.fill('input[name="password"]', password);
    await page.click('button[type="submit"]');
    await page.waitForURL(`${baseUrl}/dashboard`, {timeout: 12000});
  }

  async function stubApi(page: Page, posts: string[]) {
    const json = (body: unknown) => ({status: 200, contentType: 'application/json', body: JSON.stringify(body)});
    await page.route('**/api/v1/seances/journal*', (route) => route.fulfill(json({
      dateSeance: today,
      patients: [{
        seanceId,
        patientId,
        dateSeance: today,
        status: 'VALIDEE',
        patientNom: 'Dupont',
        patientPrenom: 'Jean',
        patientCode: 'PAT-1'
      }],
      sortiesArticles: [{articleId, articleCode: 'A1', articleLibelle: 'Lignes', quantiteTotale: 4}],
    })));
    await page.route('**/api/v1/seances/raccourcis-consommables*', (route) => route.fulfill(json([])));
    await page.route('**/api/v1/seances/patient/*/recentes*', (route) => route.fulfill(json([
      {
        seanceId: 'r1',
        dateSeance: '2026-10-01',
        status: 'VALIDEE',
        poidsAvantKg: 72,
        poidsApresKg: 69.5,
        taAvant: '12/8',
        dureeMinutes: 240
      },
    ])));
    await page.route(`**/api/v1/seances/${seanceId}/consommables`, async (route) => {
      posts.push(route.request().postData() ?? '');
      await route.fulfill(json({added: true, articleId, quantite: 1}));
    });
    await page.route(`**/api/v1/seances/${seanceId}?*`, (route) => route.fulfill(json({
      seance: {id: seanceId, centerId, patientId, dateSeance: today, status: 'VALIDEE'},
      patient: {id: patientId, codePatient: 'PAT-1', nom: 'Dupont', prenom: 'Jean'},
      paramedical: null, medical: null, forfait: null, consommables: [],
    })));
    await page.route('**/api/v1/seances/scan', (route) => route.fulfill(json({
      id: seanceId, status: 'VALIDEE', dateSeance: today, created: true, validatedNow: true, alreadyValidated: false,
      patientNom: 'Dupont', patientPrenom: 'Jean', patientCode: 'PAT-1',
    })));
  }

  test('le scan valide la séance directement et l\'ouvre au poste infirmier', async ({page}) => {
    const posts: string[] = [];
    await login(page);
    await stubApi(page, posts);
    await page.goto(`${baseUrl}/seances`);

    await page.fill('.scan-field input', 'PAT-1');
    await page.press('.scan-field input', 'Enter');

    await expect(page.locator('.banner-success')).toContainText('Dupont');
    await expect(page.locator('.patient-head h2')).toContainText('Dupont Jean');
    await expect(page.locator('.queue-item').first()).toContainText('PAT-1');
  });

  test('un toucher sur un consommable envoie une seule ligne, sans bouton Enregistrer', async ({page}) => {
    const posts: string[] = [];
    await login(page);
    await stubApi(page, posts);
    await page.goto(`${baseUrl}/seances`);
    await page.locator('.queue-item').first().click();

    await page.locator('.step-pill').nth(1).click().catch(() => undefined);
    await page.locator('.quick-btn').first().click();

    await expect.poll(() => posts.length).toBe(1);
    expect(JSON.parse(posts[0])).toMatchObject({articleId, quantite: 1});
  });

  for (const viewport of [
    {name: 'mobile', width: 360, height: 780},
    {name: 'tablette', width: 820, height: 1180},
    {name: 'PC', width: 1366, height: 800},
  ]) {
    test(`aucun débordement horizontal sur ${viewport.name}`, async ({page}) => {
      const posts: string[] = [];
      await page.setViewportSize({width: viewport.width, height: viewport.height});
      await login(page);
      await stubApi(page, posts);
      await page.goto(`${baseUrl}/seances`);
      await page.locator('.queue-item').first().click();

      const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
      expect(overflow).toBeLessThanOrEqual(1);
      await expect(page.locator('.scan-field input')).toBeVisible();
    });
  }
});
