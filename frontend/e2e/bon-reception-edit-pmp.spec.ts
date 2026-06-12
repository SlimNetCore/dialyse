// e2e/bon-reception-edit-pmp.spec.ts
import {expect, Page, test} from '@playwright/test';

const BASE_URL = 'http://localhost:4200';
const API_URL = 'http://localhost:8090/api/v1';

test.describe('Bon de Réception - Édition & PMP', () => {
  let page: Page;
  let centerId: string;
  let userId: string;

  test.beforeAll(async ({browser}) => {
    page = await browser.newPage();
    // Setup : auth & get centerId
    await page.goto(`${BASE_URL}/auth`);
    await page.fill('[name="username"]', 'admin');
    await page.fill('[name="password"]', 'admin');
    await page.click('button[type="submit"]');
    await page.waitForNavigation();

    // Extract centerId from auth store or API response
    centerId = 'test-center-id';
    userId = 'test-user';
  });

  test.afterAll(async () => {
    await page.close();
  });

  // ═════════════════════════════════════════════════════════════════════════
  // TEST 1 : Créer un BR en brouillon
  // ═════════════════════════════════════════════════════════════════════════
  test('Créer un bon de réception en brouillon', async () => {
    await page.goto(`${BASE_URL}/stock/bons-reception`);

    // Remplir formulaire
    await page.selectOption('mat-select:has-text("Fournisseur")', {index: 0});
    await page.fill('input[matInput][formcontrolname="numeroLot"]', 'LOT-20260612-001');
    await page.fill('input[matInput][type="number"][formcontrolname="quantite"]', '10');
    await page.fill('input[matInput][type="number"][formcontrolname="prixUnitaire"]', '50.50');

    // Soumettre
    await page.click('button:has-text("Créer (brouillon)")');

    // Vérifier notification
    const toast = page.locator('.mdc-snackbar');
    await expect(toast).toContainText('Bon de réception créé');

    // Vérifier BR dans la table
    const table = page.locator('table');
    await expect(table).toContainText('BROUILLON');
  });

  // ═════════════════════════════════════════════════════════════════════════
  // TEST 2 : Éditer un BR en brouillon
  // ═════════════════════════════════════════════════════════════════════════
  test('Éditer un bon de réception existant', async () => {
    await page.goto(`${BASE_URL}/stock/bons-reception`);

    // Attendre table chargée
    await page.waitForSelector('table');

    // Cliquer bouton éditer (icône pencil)
    const editButton = page.locator('button[aria-label="Éditer"]').first();
    await editButton.click();

    // Vérifier titre changé
    await expect(page.locator('mat-card-title')).toContainText('Éditer bon de réception');

    // Formulaire pré-rempli
    const qteField = page.locator('input[formcontrolname="quantite"]').first();
    const originalQte = await qteField.inputValue();
    expect(originalQte).toBe('10');

    // Modifier
    await qteField.fill('15');

    // Sauvegarder
    await page.click('button:has-text("Mettre à jour")');

    // Vérifier notification
    const toast = page.locator('.mdc-snackbar');
    await expect(toast).toContainText('Bon de réception mis à jour');

    // Vérifier table mise à jour
    await page.waitForTimeout(500);
    const table = page.locator('table');
    await expect(table).toContainText('15'); // Nouvelle quantité
  });

  // ═════════════════════════════════════════════════════════════════════════
  // TEST 3 : Annuler l'édition
  // ═════════════════════════════════════════════════════════════════════════
  test('Annuler l\'édition d\'un bon de réception', async () => {
    await page.goto(`${BASE_URL}/stock/bons-reception`);
    await page.waitForSelector('table');

    // Cliquer éditer
    await page.locator('button[aria-label="Éditer"]').first().click();

    // Modifier sans sauvegarder
    await page.locator('input[formcontrolname="quantite"]').first().fill('999');

    // Cliquer annuler
    await page.click('button:has-text("Annuler")');

    // Vérifier titre revenu à "Nouveau"
    await expect(page.locator('mat-card-title')).toContainText('Nouveau bon de réception');

    // Formulaire réinitialisé
    const qteField = page.locator('input[formcontrolname="quantite"]').first();
    const value = await qteField.inputValue();
    expect(value).toBe('1'); // Valeur par défaut
  });

  // ═════════════════════════════════════════════════════════════════════════
  // TEST 4 : Voir explication du PMP
  // ═════════════════════════════════════════════════════════════════════════
  test('Afficher dialogue explication du PMP', async () => {
    await page.goto(`${BASE_URL}/stock/bons-reception`);
    await page.waitForSelector('table');

    // Cliquer "Voir PMP"
    const pmpButton = page.locator('button:has-text("Voir PMP")').first();
    await pmpButton.click();

    // Dialog apparaît
    const dialog = page.locator('mat-dialog-container');
    await expect(dialog).toBeVisible();

    // Vérifier titre
    await expect(dialog).toContainText('Calcul du PMP');

    // Vérifier éléments clés
    await expect(dialog).toContainText('Méthode');
    await expect(dialog).toContainText('PMP courant');

    // Tableau présent
    const table = dialog.locator('table');
    await expect(table).toBeVisible();

    // Colonnes visibles
    await expect(table.locator('th')).toContainText('Type');
    await expect(table.locator('th')).toContainText('Formule');

    // Fermer dialog
    await page.click('button:has-text("Fermer")');
    await expect(dialog).not.toBeVisible();
  });

  // ═════════════════════════════════════════════════════════════════════════
  // TEST 5 : Valider un BR (calcul PMP)
  // ═════════════════════════════════════════════════════════════════════════
  test('Valider un bon de réception et recalculer le PMP', async () => {
    await page.goto(`${BASE_URL}/stock/bons-reception`);
    await page.waitForSelector('table');

    // Cliquer "Valider"
    const validateButton = page.locator('button:has-text("Valider")').first();
    await validateButton.click();

    // Toast confirmation
    const toast = page.locator('.mdc-snackbar');
    await expect(toast).toContainText('Réception validée · PMP recalculé');

    // Attendre table rafraîchie
    await page.waitForTimeout(1000);

    // Vérifier statut changé à "VALIDÉ"
    const table = page.locator('table');
    await expect(table).toContainText('VALIDÉ');

    // Bouton "Valider" doit disparaître (pas BROUILLON)
    const br = page.locator('table tbody tr').first();
    const buttons = br.locator('button:has-text("Valider")');
    await expect(buttons).not.toBeVisible();
  });

  // ═════════════════════════════════════════════════════════════════════════
  // TEST 6 : Éditer non-allowed si statut non-BROUILLON
  // ═════════════════════════════════════════════════════════════════════════
  test('Empêcher édition d\'un BR validé', async () => {
    await page.goto(`${BASE_URL}/stock/bons-reception`);
    await page.waitForSelector('table');

    // Chercher BR VALIDÉ
    const validatedRow = page.locator('table tbody tr:has-text("VALIDÉ")').first();

    // Bouton éditer ne doit pas exister
    const editBtn = validatedRow.locator('button[aria-label="Éditer"]');
    await expect(editBtn).not.toBeVisible();

    // Seul "Voir PMP" doit rester
    const pmpBtn = validatedRow.locator('button:has-text("Voir PMP")');
    await expect(pmpBtn).toBeVisible();
  });

  // ═════════════════════════════════════════════════════════════════════════
  // TEST 7 : Constantes métier
  // ═════════════════════════════════════════════════════════════════════════
  test('Vérifier formulaire respects les validations', async () => {
    await page.goto(`${BASE_URL}/stock/bons-reception`);

    // Vérifier bouton "Créer" désactivé au départ
    let createBtn = page.locator('button:has-text("Créer")');
    await expect(createBtn).toBeDisabled(); // Pas tous les champs remplis

    // Remplir article (required)
    await page.selectOption('mat-select[formcontrolname="articleId"]', {index: 0});

    // Essayer quantité négative (invalid)
    await page.fill('input[formcontrolname="quantite"]', '-5');
    createBtn = page.locator('button:has-text("Créer")');
    await expect(createBtn).toBeDisabled();

    // Corriger
    await page.fill('input[formcontrolname="quantite"]', '10');

    // Vérifier enabled
    await expect(createBtn).not.toBeDisabled();
  });

  // ═════════════════════════════════════════════════════════════════════════
  // TEST 8 : Multi-articles PMP
  // ═════════════════════════════════════════════════════════════════════════
  test('BR avec plusieurs articles - PMP pour 1er article', async () => {
    await page.goto(`${BASE_URL}/stock/bons-reception`);

    // Créer BR avec 3 lots
    await page.selectOption('mat-select[formcontrolname="fournisseurId"]', {index: 0});

    // Lot 1
    await page.selectOption('mat-select[formcontrolname="articleId"]', {index: 0});
    await page.fill('input[formcontrolname="numeroLot"]', 'LOT001');
    await page.fill('input[formcontrolname="quantite"]', '10');
    await page.fill('input[formcontrolname="prixUnitaire"]', '50');

    // Ajouter lot 2
    await page.click('button:has-text("Ajouter un lot")');
    await page.selectOption('mat-select[formcontrolname="articleId"]:nth-of-type(2)', {index: 1});
    await page.fill('input[formcontrolname="numeroLot"]:nth-of-type(2)', 'LOT002');
    await page.fill('input[formcontrolname="quantite"]:nth-of-type(2)', '20');
    await page.fill('input[formcontrolname="prixUnitaire"]:nth-of-type(2)', '60');

    // Créer
    await page.click('button:has-text("Créer (brouillon)")');
    await page.waitForTimeout(1000);

    // Ouvrir PMP
    await page.click('button:has-text("Voir PMP")');

    // Vérifier dialogue montre 1er article
    const dialog = page.locator('mat-dialog-container');
    const subtitle = dialog.locator('mat-card-subtitle');

    // Doit contenir article 1
    const text = await subtitle.textContent();
    expect(text).toContain('LOT001'); // 1er article référencé
  });

  // ═════════════════════════════════════════════════════════════════════════
  // TEST 9 : Réactivité temps réel
  // ═════════════════════════════════════════════════════════════════════════
  test('Table BR rafraîchit après création', async () => {
    const page2 = await page.context().newPage();

    // Page 1 : View
    await page.goto(`${BASE_URL}/stock/bons-reception`);
    const countBefore = await page.locator('table tbody tr').count();

    // Page 2 : Create
    await page2.goto(`${BASE_URL}/stock/bons-reception`);
    await page2.selectOption('mat-select[formcontrolname="fournisseurId"]', {index: 0});
    await page2.selectOption('mat-select[formcontrolname="articleId"]', {index: 0});
    await page2.fill('input[formcontrolname="numeroLot"]', 'LOT-NEW-' + Date.now());
    await page2.fill('input[formcontrolname="quantite"]', '5');
    await page2.fill('input[formcontrolname="prixUnitaire"]', '100');
    await page2.click('button:has-text("Créer (brouillon)")');

    // Page 1 : Vérifier reload
    await page.waitForTimeout(2000);
    const countAfter = await page.locator('table tbody tr').count();

    expect(countAfter).toBeGreaterThan(countBefore);

    await page2.close();
  });
});

