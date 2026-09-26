import {expect, Page, test} from '@playwright/test';

/**
 * E2E — Direction d'une société (API simulée : aucun backend requis).
 * Couvre : connexion sans centre (mode « Direction »), cantonnement à /direction, affichage des agrégats
 * anonymes (effectifs faibles masqués « < 5 ») et absence de débordement horizontal sur mobile.
 */
const env = (globalThis as unknown as { process?: { env?: Record<string, string | undefined> } }).process?.env ?? {};
const baseUrl = env['E2E_BASE_URL'] || 'http://127.0.0.1:4200';

const SOCIETE = {id: '11111111-1111-1111-1111-111111111111', name: 'Société Démo'};
const SESSION = {
  username: 'direction', fullName: 'Direction Démo', userId: '22222222-2222-2222-2222-222222222222',
  centerId: null, centerName: null, roles: ['DIRECTION'], societeId: SOCIETE.id, societeName: SOCIETE.name,
  scope: 'SOCIETE',
};
const stats = (nom: string, patients: number | null, caTtc: number) => ({
  centerId: nom, nom, actif: true, patients, patientsSousKt: patients === null ? null : 6, seances: 12, factures: 3,
  caHt: caTtc / 1.19, caTtc, encaisse: caTtc / 2, resteARecouvrer: caTtc / 2, tauxEncaissement: 50,
});
const OVERVIEW = {
  societeId: SOCIETE.id, societeNom: SOCIETE.name, from: '2026-01-01', to: '2026-09-26',
  generatedAt: '2026-09-26T10:00:00Z', seuilAnonymat: 5,
  centres: [stats('Centre Alpha', 42, 900000), stats('Centre Beta', null, 300000)],
  totaux: {...stats('Total', 44, 1200000), centerId: null},
  mensuel: [
    {mois: '2026-01', centerId: 'Centre Alpha', seances: 5, caHt: 1, caTtc: 400000},
    {mois: '2026-02', centerId: 'Centre Alpha', seances: 7, caHt: 1, caTtc: 500000},
  ],
};

const marker = (pct: number | null) => ({
  evalues: pct === null ? null : 12,
  pctDansCible: pct,
  pctSousCible: pct === null ? null : 100 - pct,
  pctAuDessus: pct === null ? null : 0,
});
const indicators = (nom: string | null, id: string | null, pct: number | null, sousSeuil: number) => ({
  centerId: id, nom, actif: true,
  clinique: {
    ktV: marker(pct), hemoglobine: marker(pct), phosphore: marker(pct), pth: marker(pct), albumine: marker(pct),
    vhbPositifs: pct === null ? null : 6, vhcPositifs: null, vihPositifs: null, patientsObservanceEnRetard: null,
    greffeListeAttente: null, greffeBilanEnCours: null, greffesPeriode: null,
  },
  stock: {
    articlesActifs: 40,
    articlesSousSeuil: sousSeuil,
    lotsPerimes: 0,
    lotsPeremptionProche: 1,
    valeurStock: 250000
  },
});
const INDICATORS = {
  societeId: SOCIETE.id, from: '2026-01-01', to: '2026-09-26', generatedAt: '2026-09-26T10:00:00Z', seuilAnonymat: 5,
  centres: [indicators('Centre Alpha', 'Centre Alpha', 62.5, 3), indicators('Centre Beta', 'Centre Beta', null, 0)],
  totaux: indicators(null, null, 62.5, 3),
  alertes: [{
    centerId: 'Centre Alpha',
    centre: 'Centre Alpha',
    code: 'STOCK_SOUS_SEUIL',
    severity: 'CRITICAL',
    valeur: 3
  }],
};

async function mockBackend(page: Page): Promise<{ loginBodies: unknown[] }> {
  const loginBodies: unknown[] = [];
  let signedIn = false;
  await page.routeWebSocket(/\/ws/, (ws) => {
    ws.onMessage((message) => {
      if (typeof message === 'string' && message.startsWith('CONNECT')) {
        ws.send('CONNECTED\nversion:1.2\nheart-beat:0,0\n\n\0');
      }
    });
  });
  await page.route('**/actuator/health*', (r) => r.fulfill({json: {status: 'UP'}}));
  // Le rafraîchissement ne doit jamais atteindre un vrai backend local : indisponible, sans redirection.
  await page.route('**/api/v1/auth/refresh', (r) => r.fulfill({status: 503, json: {}}));
  await page.route('**/api/v1/auth/societes', (r) => r.fulfill({json: [SOCIETE]}));
  await page.route('**/api/v1/auth/societes/*/centres', (r) => r.fulfill({json: []}));
  await page.route('**/api/v1/auth/me', (r) =>
    signedIn ? r.fulfill({json: SESSION}) : r.fulfill({status: 401, json: {}}));
  await page.route('**/api/v1/auth/login', async (r) => {
    loginBodies.push(r.request().postDataJSON());
    signedIn = true;
    await r.fulfill({json: SESSION});
  });
  await page.route('**/api/v1/direction/overview*', (r) => r.fulfill({json: OVERVIEW}));
  await page.route('**/api/v1/direction/indicators*', (r) => r.fulfill({json: INDICATORS}));
  await page.route('**/api/v1/direction/snapshots', (r) => r.fulfill({json: []}));
  await page.route('**/api/v1/auth/setup/status', (r) => r.fulfill({json: {required: false, tokenRequired: false}}));
  return {loginBodies};
}

test.describe('Direction — tableau de bord consolidé', () => {
  test('se connecte sans centre puis affiche des agrégats anonymes', async ({page}) => {
    const {loginBodies} = await mockBackend(page);
    await page.goto(`${baseUrl}/login`);

    await page.getByTestId('login-mode-direction').click();
    await expect(page.getByTestId('login-center-select')).toHaveCount(0);
    await page.getByTestId('login-societe-select').click();
    await page.getByRole('option', {name: SOCIETE.name}).click();
    await page.getByTestId('login-username').fill('direction');
    await page.getByTestId('login-password').fill('un-mot-de-passe-de-test-1');
    await page.getByTestId('login-submit').click();

    await page.waitForURL('**/direction');
    expect(loginBodies).toHaveLength(1);
    expect(loginBodies[0]).toMatchObject({societeId: SOCIETE.id, username: 'direction'});
    expect(loginBodies[0]).not.toHaveProperty('centerId');

    const table = page.getByTestId('direction-centres');
    await expect(table).toContainText('Centre Alpha');
    await expect(table.locator('tr', {hasText: 'Centre Beta'})).toContainText('< 5');
    await expect(page.getByTestId('direction-kpis')).toContainText('44');

    await expect(page.getByTestId('direction-alerts')).toContainText('Centre Alpha');
    const clinical = page.getByTestId('direction-clinical');
    await expect(clinical.locator('tr', {hasText: 'Centre Alpha'})).toContainText('62.5 %');
    await expect(clinical.locator('tr', {hasText: 'Centre Beta'})).not.toContainText('%');
    await expect(page.getByTestId('direction-stock').locator('tr', {hasText: 'Centre Alpha'})).toContainText('3');
  });

  test('cantonne la direction à /direction', async ({page}) => {
    await mockBackend(page);
    await page.goto(`${baseUrl}/login`);
    await page.getByTestId('login-mode-direction').click();
    await page.getByTestId('login-societe-select').click();
    await page.getByRole('option', {name: SOCIETE.name}).click();
    await page.getByTestId('login-username').fill('direction');
    await page.getByTestId('login-password').fill('un-mot-de-passe-de-test-1');
    await page.getByTestId('login-submit').click();
    await page.waitForURL('**/direction');

    await page.goto(`${baseUrl}/patients`);
    await page.waitForURL('**/direction');
  });

  test('reste dans le viewport sur mobile (320 px)', async ({page}) => {
    await page.setViewportSize({width: 320, height: 800});
    await mockBackend(page);
    await page.goto(`${baseUrl}/login`);
    await page.getByTestId('login-mode-direction').click();
    await page.getByTestId('login-societe-select').click();
    await page.getByRole('option', {name: SOCIETE.name}).click();
    await page.getByTestId('login-username').fill('direction');
    await page.getByTestId('login-password').fill('un-mot-de-passe-de-test-1');
    await page.getByTestId('login-submit').click();
    await page.waitForURL('**/direction');
    await expect(page.getByTestId('direction-dashboard')).toBeVisible();

    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
    expect(overflow).toBeLessThanOrEqual(1);
  });
});
