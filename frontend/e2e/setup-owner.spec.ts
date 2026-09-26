import {expect, Page, test} from '@playwright/test';

/**
 * E2E — Installation initiale (API simulée) : à la première ouverture, la connexion renvoie vers la création du
 * compte propriétaire, qui exige un mot de passe exigeant ; une fois créé, /setup renvoie vers la connexion.
 */
const env = (globalThis as unknown as { process?: { env?: Record<string, string | undefined> } }).process?.env ?? {};
const baseUrl = env['E2E_BASE_URL'] || 'http://127.0.0.1:4200';

async function mockBackend(page: Page, state: { required: boolean }): Promise<{ created: unknown[] }> {
  const created: unknown[] = [];
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
  await page.route('**/api/v1/auth/me', (r) => r.fulfill({status: 401, json: {}}));
  await page.route('**/api/v1/auth/societes', (r) => r.fulfill({json: []}));
  await page.route('**/api/v1/auth/setup/status', (r) =>
    r.fulfill({json: {required: state.required, tokenRequired: false}}));
  await page.route('**/api/v1/auth/setup/superadmin', async (r) => {
    created.push(r.request().postDataJSON());
    state.required = false;
    await r.fulfill({status: 201, body: ''});
  });
  return {created};
}

test.describe('Installation initiale', () => {
  test('propose la création du propriétaire puis renvoie vers la connexion', async ({page}) => {
    const state = {required: true};
    const {created} = await mockBackend(page, state);
    await page.goto(`${baseUrl}/login`);
    await page.waitForURL('**/setup');

    await page.getByTestId('setup-username').fill('gestionnaire');
    await page.getByTestId('setup-password').fill('court');
    await page.getByTestId('setup-confirm').fill('court');
    await expect(page.getByTestId('setup-submit')).toBeDisabled();

    const strong = 'Proprietaire#Solide-2026';
    await page.getByTestId('setup-password').fill(strong);
    await page.getByTestId('setup-confirm').fill(strong);
    await expect(page.getByTestId('setup-rules').locator('li.ok')).toHaveCount(6);
    await page.getByTestId('setup-submit').click();

    await page.waitForURL('**/login?setup=done');
    await expect(page.getByTestId('setup-done')).toBeVisible();
    expect(created).toHaveLength(1);
    expect(created[0]).toMatchObject({username: 'gestionnaire', password: strong});

    await page.goto(`${baseUrl}/setup`);
    await page.waitForURL('**/login');
  });

  test('reste dans le viewport sur mobile (320 px)', async ({page}) => {
    await page.setViewportSize({width: 320, height: 800});
    await mockBackend(page, {required: true});
    await page.goto(`${baseUrl}/setup`);
    await expect(page.getByTestId('setup-card')).toBeVisible();
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
    expect(overflow).toBeLessThanOrEqual(1);
  });
});
