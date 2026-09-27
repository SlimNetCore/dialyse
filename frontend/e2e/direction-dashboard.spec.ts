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
  periodePrecedente: {
    from: '2025-04-06', to: '2025-12-31', seances: 40, caHt: 900000, caTtc: 1071000, encaisse: 500000,
    resteARecouvrer: 571000, tauxEncaissement: 46.7,
  },
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

const BREAKDOWN = {
  societeId: SOCIETE.id, from: '2026-01-01', to: '2026-09-26', generatedAt: '2026-09-26T10:00:00Z', seuilAnonymat: 5,
  sexe: [
    {centerId: 'Centre Alpha', nom: 'Centre Alpha', masculin: 20, feminin: 22, autre: 0},
    {centerId: 'Centre Beta', nom: 'Centre Beta', masculin: null, feminin: null, autre: 0},
  ],
  ages: [
    {
      centerId: 'Centre Alpha', nom: 'Centre Alpha', tranches: ['0_17', '18_29', '30_44', '45_59', '60_PLUS', 'INCONNU']
        .map((code) => ({code, count: code === '45_59' ? 30 : code === '60_PLUS' ? 12 : 0}))
    },
    {
      centerId: 'Centre Beta', nom: 'Centre Beta', tranches: ['0_17', '18_29', '30_44', '45_59', '60_PLUS', 'INCONNU']
        .map((code) => ({code, count: null}))
    },
  ],
  caisses: [
    {
      centerId: 'Centre Alpha',
      centre: 'Centre Alpha',
      caisseCode: 'CNAS',
      caisse: 'CNAS Nationale',
      patients: 30,
      seances: 400,
      caHt: 1500000
    },
    {
      centerId: 'Centre Beta',
      centre: 'Centre Beta',
      caisseCode: 'CNAS',
      caisse: 'CNAS Nationale',
      patients: null,
      seances: 40,
      caHt: 90000
    },
  ],
  caisseTotaux: [{caisseCode: 'CNAS', caisse: 'CNAS Nationale', patients: 32, seances: 440, caHt: 1590000}],
  anemie: [
    {
      centerId: 'Centre Alpha',
      nom: 'Centre Alpha',
      patientsEpo: 18,
      patientsFer: null,
      administreesEpo: 90,
      administreesFer: 12,
      nonAdministrees: 3,
      tauxAdministration: 97.1,
      patientsSousEpo: 20,
      patientsSousFer: null
    },
    {
      centerId: 'Centre Beta',
      nom: 'Centre Beta',
      patientsEpo: null,
      patientsFer: null,
      administreesEpo: 2,
      administreesFer: 0,
      nonAdministrees: 0,
      tauxAdministration: 100,
      patientsSousEpo: null,
      patientsSousFer: null
    },
  ],
};

/** Envoie un message STOMP sur le canal de la société, comme le ferait le serveur à un changement de données. */
type Push = (body: unknown) => void;

async function mockBackend(page: Page): Promise<{ loginBodies: unknown[]; push: Push }> {
  const loginBodies: unknown[] = [];
  let signedIn = false;
  const subscriptions: { id: string; destination: string; send: (frame: string) => void }[] = [];
  await page.routeWebSocket(/\/ws/, (ws) => {
    ws.onMessage((message) => {
      if (typeof message !== 'string') return;
      if (message.startsWith('CONNECT')) {
        ws.send('CONNECTED\nversion:1.2\nheart-beat:0,0\n\n\0');
      } else if (message.startsWith('SUBSCRIBE')) {
        const id = /(?:^|\n)id:([^\n]+)/.exec(message)?.[1] ?? 'sub-0';
        const destination = /(?:^|\n)destination:([^\n]+)/.exec(message)?.[1] ?? '';
        subscriptions.push({id, destination, send: (frame) => ws.send(frame)});
      }
    });
  });
  const push: Push = (body) => {
    for (const s of subscriptions.filter((x) => x.destination.startsWith('/topic/societe/'))) {
      s.send(`MESSAGE\ndestination:${s.destination}\nsubscription:${s.id}\nmessage-id:m${Date.now()}\n`
        + `content-type:application/json\n\n${JSON.stringify(body)}\0`);
    }
  };
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
  await page.route('**/api/v1/direction/breakdown*', (r) => r.fulfill({json: BREAKDOWN}));
  await page.route('**/api/v1/direction/snapshots', (r) =>
    r.fulfill({
      json: [{mois: '2026-08', generatedAt: '2026-09-01T02:30:00Z'},
        {mois: '2026-07', generatedAt: '2026-08-01T02:30:00Z'}]
    }));
  await page.route('**/api/v1/direction/snapshots/2026-08', (r) => r.fulfill({
    json: {
      mois: '2026-08', generatedAt: '2026-09-01T02:30:00Z',
      overview: {...OVERVIEW, totaux: {...OVERVIEW.totaux, seances: 20, caTtc: 600000, encaisse: 300000}},
      indicators: INDICATORS, breakdown: BREAKDOWN,
    }
  }));
  await page.route('**/api/v1/direction/snapshots/2026-07', (r) => r.fulfill({
    json: {
      mois: '2026-07', generatedAt: '2026-08-01T02:30:00Z',
      overview: {...OVERVIEW, totaux: {...OVERVIEW.totaux, seances: 10, caTtc: 500000, encaisse: 250000}},
      indicators: INDICATORS, breakdown: BREAKDOWN,
    }
  }));
  await page.route('**/api/v1/direction/report*', (r) =>
    r.fulfill({status: 200, contentType: 'application/pdf', body: '%PDF-1.4 test'}));
  await page.route('**/api/v1/auth/setup/status', (r) => r.fulfill({json: {required: false, tokenRequired: false}}));
  return {loginBodies, push};
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

    // répartitions par centre : sexe, âge, caisses (patients / séances / CA HT), anémie — effectifs faibles masqués
    await expect(page.getByTestId('direction-sexe').locator('tr', {hasText: 'Centre Alpha'})).toContainText('20');
    await expect(page.getByTestId('direction-sexe').locator('tr', {hasText: 'Centre Beta'})).toContainText('< 5');
    await expect(page.getByTestId('direction-ages').locator('tr', {hasText: 'Centre Alpha'})).toContainText('30');
    const caisses = page.getByTestId('direction-caisses-table');
    await expect(caisses).toContainText('CNAS Nationale');
    await page.getByTestId('caisse-metric-seances').click();
    await expect(caisses.locator('tr', {hasText: 'CNAS Nationale'})).toContainText('440');
    await page.getByTestId('caisse-metric-patients').click();
    await expect(caisses.locator('tr', {hasText: 'CNAS Nationale'})).toContainText('< 5');
    await expect(page.getByTestId('direction-anemie').locator('tr', {hasText: 'Centre Alpha'})).toContainText('97.1');

    // deltas vs la période précédente et raccourcis de période
    await expect(page.getByTestId('delta-seances')).toContainText('-70.0 %');
    await expect(page.getByTestId('delta-ca-ttc')).toContainText('+12.0 %');
    await expect(page.getByTestId('delta-encaisse')).toContainText('+20.0 %');
    await expect(page.getByTestId('direction-presets')).toContainText('Ce mois');

    // filtre par centre : isole Centre Beta, les KPI et le comparatif se recentrent dessus
    await page.getByTestId('direction-centre-filter').click();
    await page.getByRole('option', {name: 'Centre Beta'}).click();
    await expect(page.getByTestId('direction-kpis')).toContainText('< 5');
    await expect(page.getByTestId('direction-centres').locator('tbody tr')).toHaveCount(1);
    await expect(page.getByTestId('direction-centres')).toContainText('Centre Beta');
    await expect(page.getByTestId('direction-centres')).not.toContainText('Centre Alpha');

    // retour à la vue consolidée
    await page.getByTestId('direction-centre-filter').click();
    await page.getByRole('option', {name: 'Tous les centres'}).click();
    await expect(page.getByTestId('direction-centres').locator('tbody tr')).toHaveCount(2);

    // rapport imprimable de la période affichée
    const download = page.waitForEvent('download');
    await page.getByTestId('direction-print').click();
    expect((await download).suggestedFilename()).toMatch(/^rapport-direction-.*\.pdf$/);

    // export CSV des tableaux affichés
    const csvDownload = page.waitForEvent('download');
    await page.getByTestId('direction-export-csv').click();
    const csv = await csvDownload;
    expect(csv.suggestedFilename()).toMatch(/^tableau-de-bord-direction-.*\.csv$/);

    // comparateur de deux mois figés
    await page.getByTestId('compare-month-a').click();
    await page.getByRole('option', {name: '2026-07'}).click();
    await page.getByTestId('compare-month-b').click();
    await page.getByRole('option', {name: '2026-08'}).click();
    const compare = page.getByTestId('compare-table');
    const seancesRow = compare.locator('tr', {hasText: 'Séances'});
    await expect(seancesRow).toContainText('10');
    await expect(seancesRow).toContainText('20');
    await expect(seancesRow).toContainText('+100.0 %');
    await expect(compare.locator('tr', {hasText: 'Encaissé'})).toContainText('+20.0 %');
  });

  test('reçoit les changements en temps réel : notification et données relues', async ({page}) => {
    const {push} = await mockBackend(page);
    await page.goto(`${baseUrl}/login`);
    await page.getByTestId('login-mode-direction').click();
    await page.getByTestId('login-societe-select').click();
    await page.getByRole('option', {name: SOCIETE.name}).click();
    await page.getByTestId('login-username').fill('direction');
    await page.getByTestId('login-password').fill('un-mot-de-passe-de-test-1');
    await page.getByTestId('login-submit').click();
    await page.waitForURL('**/direction');
    await expect(page.getByTestId('direction-live')).toContainText(/temps réel actif/i);

    // le serveur signale un changement : une séance de plus au Centre Alpha
    push({
      type: 'DASHBOARD_CHANGED', societeId: SOCIETE.id, at: new Date().toISOString(),
      changes: [{
        centerId: 'Centre Alpha',
        centre: 'Centre Alpha',
        family: 'SEANCES',
        name: 'seances',
        before: 12,
        after: 13
      }],
    });
    const bell = page.getByTestId('direction-bell');
    await expect(bell).toContainText('notifications_active');
    await bell.click();
    await expect(page.getByTestId('direction-notifications')).toContainText('Centre Alpha');
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
