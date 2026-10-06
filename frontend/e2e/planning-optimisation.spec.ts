import {expect, Page, Route, test} from '@playwright/test';

/**
 * Optimisation du planning (Timefold) : lancement, suivi, comparaison avant / après, application par l'administrateur
 * et affichage responsive. Le login est réel ; le premier scénario s'appuie sur le serveur réel (calcul court), les
 * suivants stubbent l'API d'optimisation pour rester déterministes.
 */
test.describe('Optimisation du planning', () => {
  const env = (globalThis as unknown as { process?: { env?: Record<string, string | undefined> } }).process?.env ?? {};
  const baseUrl = env['E2E_BASE_URL'] || 'http://localhost:4200';
  const username = env['E2E_USERNAME'] || 'admin';
  const password = env['E2E_PASSWORD'] || 'admin$$2026dz';
  const centerId = env['E2E_CENTER_ID'] || '11111111-1111-1111-1111-111111111111'; // centre des exécutions stubbées

  const indicateurs = (surcharge: Record<string, number> = {}) => ({
    generateursUtilises: 6, sallesOuvertes: 12, vacationsRequises: 12, placesInfirmierInutilisees: 12,
    patientsNonPlaces: 1, vacationsNonPourvues: 12, infirmiersMobilises: 0, ecartCharge: 0, depassementsHebdo: 0,
    ...surcharge,
  });

  const parametres = {
    perimetre: 'COMPLET', debutSemaine: '2026-10-04', nbSemaines: 1, dureeMaxSecondes: 20, stabilite: 5,
    objectif: 'EQUITE', maxVacationsParJour: 2, maxVacationsParSemaine: 6,
  };

  const resultat = () => ({
    salles: [{id: 's1', nom: 'Salle A'}, {id: 's2', nom: 'Salle B'}],
    creneaux: [{id: 'c1', libelle: 'Matin', ordre: 1}],
    deplacements: Array.from({length: 12}, (_, i) => ({
      patientId: `p${i}`, nom: `Patient ${i}`,
      de: {salleId: 's2', creneauId: 'c1', generateurId: `g${i}`, generateurCode: `B-G${i}`},
      vers: {salleId: 's1', creneauId: 'c1', generateurId: `h${i}`, generateurCode: `A-G${i}`},
    })),
    nonPlaces: [{patientId: 'px', nom: 'Sans place', cause: 'AUCUNE_PLACE'}],
    vacations: [{
      date: '2026-10-05', jour: 'LUNDI', salleId: 's1', creneauId: 'c1', infirmierId: 'i1', nom: 'Marie',
      existante: false,
    }],
    manques: [],
    avant: indicateurs(),
    apres: indicateurs({sallesOuvertes: 6, vacationsRequises: 6, placesInfirmierInutilisees: 0, patientsNonPlaces: 0,
      vacationsNonPourvues: 0, infirmiersMobilises: 3}),
  });

  const run = (statut: string, extra: Record<string, unknown> = {}) => ({
    id: 'run-1', centerId, statut, parametres, creeLe: '2026-10-06T08:00:00Z', termineLe: null, lancePar: 'u', phase: null,
    score: null, resume: null, resultat: null, erreur: null, appliqueLe: null, ...extra,
  });

  async function login(page: Page) {
    await page.goto(`${baseUrl}/login`);
    await page.click('[data-testid="login-societe-select"]');
    await page.locator('mat-option').first().click();
    await page.click('[data-testid="login-center-select"]');
    await page.locator('mat-option').first().click();
    await page.fill('[data-testid="login-username"]', username);
    await page.fill('[data-testid="login-password"]', password);
    await page.click('[data-testid="login-submit"]');
    await page.waitForURL(/\/dashboard/, {timeout: 20000});
  }

  const json = (body: unknown, status = 200) => ({status, contentType: 'application/json', body: JSON.stringify(body)});

  /** Stub : le premier suivi répond « en cours », les suivants « terminée » avec la proposition. */
  async function stubCalcul(page: Page, appelsApplication: string[]) {
    let lectures = 0;
    await page.route('**/api/v1/planning/optimisations?*', async (route: Route) => {
      if (route.request().method() === 'POST') {
        await route.fulfill(json(run('EN_COURS'), 202));
      } else {
        await route.fulfill(json({items: [run('TERMINEE', {resume: null})], total: 1, page: 0, size: 10}));
      }
    });
    await page.route('**/api/v1/planning/optimisations/run-1?*', async (route) => {
      lectures += 1;
      await route.fulfill(json(lectures < 2
        ? run('EN_COURS', {phase: 'PATIENTS', score: '0hard/0medium/-100soft'})
        : run('TERMINEE', {resultat: resultat(), score: '0hard/0medium/-40soft', termineLe: '2026-10-06T08:00:20Z'})));
    });
    await page.route('**/api/v1/planning/optimisations/run-1/application?*', async (route) => {
      appelsApplication.push(route.request().url());
      await route.fulfill(json(run('TERMINEE', {resultat: resultat(), appliqueLe: '2026-10-06T09:00:00Z'})));
    });
  }

  test('calcule une proposition sur le serveur réel puis affiche la comparaison avant / après', async ({page}) => {
    await login(page);
    await page.goto(`${baseUrl}/seances/optimisation`);
    await expect(page.locator('[data-testid="optimisation-page"]')).toBeVisible();

    await page.fill('[data-testid="opt-duree"]', '3');
    await page.click('[data-testid="opt-lancer"]');

    await expect(page.locator('[data-testid="opt-resultat"]')).toBeVisible({timeout: 90_000});
    await expect(page.locator('[data-testid="opt-indicateurs"] tbody tr')).toHaveCount(9);
    await expect(page.locator('[data-testid="opt-historique"] tbody tr').first()).toBeVisible();
    // pendant le calcul le bouton de lancement était bloqué ; il est de nouveau disponible
    await expect(page.locator('[data-testid="opt-lancer"]')).toBeEnabled();
  });

  test('suit le calcul, compare les indicateurs et pagine les déplacements', async ({page}) => {
    await login(page);
    const appels: string[] = [];
    await stubCalcul(page, appels);
    await page.goto(`${baseUrl}/seances/optimisation`);

    await page.click('[data-testid="opt-lancer"]');
    await expect(page.locator('[data-testid="opt-encours"]')).toBeVisible();
    await expect(page.locator('[data-testid="opt-score"]')).toContainText('-100soft');
    await expect(page.locator('[data-testid="opt-resultat"]')).toBeVisible({timeout: 10_000});

    const vacations = page.locator('[data-testid="opt-indicateurs"] tbody tr', {hasText: 'vacationsRequises'});
    await expect(page.locator('[data-testid="opt-indicateurs"] .opt-ecart.mieux').first()).toBeVisible();
    await expect(vacations.or(page.locator('[data-testid="opt-indicateurs"]'))).toContainText('-6');
    await expect(page.locator('[data-testid="opt-deplacements"] tbody tr')).toHaveCount(10);
    await page.locator('.opt-tabs .mat-mdc-paginator-navigation-next').click();
    await expect(page.locator('[data-testid="opt-deplacements"] tbody tr')).toHaveCount(2);
  });

  test('applique la proposition après confirmation (administrateur)', async ({page}) => {
    await login(page);
    const appels: string[] = [];
    await stubCalcul(page, appels);
    await page.goto(`${baseUrl}/seances/optimisation`);
    await page.click('[data-testid="opt-lancer"]');
    await expect(page.locator('[data-testid="opt-resultat"]')).toBeVisible({timeout: 10_000});

    await page.click('[data-testid="opt-appliquer"]');
    await page.locator('app-confirm-dialog .confirm-btn').click();

    await expect(page.locator('[data-testid="optimisation-ok"]')).toBeVisible();
    await expect(page.locator('[data-testid="opt-appliquer"]')).toBeDisabled();
    expect(appels).toHaveLength(1);
    expect(appels[0]).toMatch(/centerId=[0-9a-f-]{36}/); // le centre de la session, jamais un centre choisi par l'écran
  });

  for (const [nom, largeur, hauteur] of [['mobile', 375, 800], ['tablette', 768, 1024]] as const) {
    test(`reste lisible sans défilement horizontal de la page (${nom})`, async ({page}) => {
      await page.setViewportSize({width: largeur, height: hauteur});
      await login(page);
      const appels: string[] = [];
      await stubCalcul(page, appels);
      await page.goto(`${baseUrl}/seances/optimisation`);
      await page.click('[data-testid="opt-lancer"]');
      await expect(page.locator('[data-testid="opt-resultat"]')).toBeVisible({timeout: 10_000});

      // Le corps de page défile dans son propre conteneur : on vérifie que rien, hors conteneurs de défilement local
      // (tableaux), ne dépasse l'écran — sinon le contenu serait rogné sans que la page ne défile.
      const debordements = await page.evaluate(() => {
        const largeur = window.innerWidth;
        const page = document.querySelector('[data-testid="optimisation-page"]')!;
        return Array.from(page.querySelectorAll<HTMLElement>('*'))
          // hors conteneurs de défilement local (tableaux, en-têtes d'onglets) et hors onglets inactifs (placés à côté)
          .filter((el) => !el.closest('.opt-scroll') && !el.closest('.mat-mdc-tab-header'))
          .filter((el) => !el.closest('mat-tab-body:not(.mat-mdc-tab-body-active)'))
          .filter((el) => el.getBoundingClientRect().right > largeur + 1)
          .map((el) => `${el.tagName.toLowerCase()}.${el.className}`.slice(0, 80));
      });
      expect(debordements).toEqual([]);
      await expect(page.locator('[data-testid="opt-lancer"]')).toBeVisible();
      await expect(page.locator('[data-testid="opt-appliquer"]')).toBeVisible();
    });
  }
});
