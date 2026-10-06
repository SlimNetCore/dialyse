import {expect, Page, test} from '@playwright/test';

/**
 * Suppression d'une séance par l'administrateur depuis l'historique (serveur réel) : motif obligatoire, puis la séance
 * disparaît de la liste avec un message de succès ; la boîte de dialogue reste dans l'écran sur mobile.
 */
test.describe('Historique des séances — suppression par l\'administrateur', () => {
  const env = (globalThis as unknown as { process?: { env?: Record<string, string | undefined> } }).process?.env ?? {};
  const baseUrl = env['E2E_BASE_URL'] || 'http://localhost:4200';
  const username = env['E2E_USERNAME'] || 'admin';
  const password = env['E2E_PASSWORD'] || 'admin$$2026dz';

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

  test('supprime une séance avec un motif', async ({page}) => {
    await login(page);
    await page.goto(`${baseUrl}/seances/historique`);
    const boutons = page.locator('[data-testid="seance-delete"]');
    await expect(boutons.first()).toBeVisible({timeout: 20_000});

    const reponse = page.waitForResponse((r) => r.request().method() === 'DELETE' && r.url().includes('/api/v1/seances/'));
    await boutons.first().click();
    const confirmer = page.locator('[data-testid="seance-delete-confirm"]');
    await expect(confirmer).toBeDisabled();
    await page.fill('[data-testid="seance-delete-motif"]', 'Séance saisie en double (test e2e)');
    await confirmer.click();

    expect((await reponse).status()).toBe(204);
    expect((await reponse).url()).toMatch(/centerId=[0-9a-f-]{36}/);
    await expect(page.locator('[data-testid="seance-delete-ok"]')).toBeVisible();
  });

  test('la boîte de dialogue reste dans l\'écran sur mobile', async ({page}) => {
    await page.setViewportSize({width: 375, height: 800});
    await login(page);
    await page.goto(`${baseUrl}/seances/historique`);
    const bouton = page.locator('[data-testid="seance-delete"]').first();
    await expect(bouton).toBeAttached({timeout: 20_000});
    await bouton.scrollIntoViewIfNeeded();
    await bouton.click();

    const formulaire = page.locator('[data-testid="seance-delete-form"]');
    await expect(formulaire).toBeVisible();
    const boite = await formulaire.boundingBox();
    expect(boite).not.toBeNull();
    expect(boite!.x).toBeGreaterThanOrEqual(0);
    expect(boite!.x + boite!.width).toBeLessThanOrEqual(375 + 1);
    await page.keyboard.press('Escape');
  });
});
