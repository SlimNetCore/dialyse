import {expect, test} from '@playwright/test';

const centerId = process.env.E2E_CENTER_ID || '11111111-1111-1111-1111-111111111111';
const username = process.env.E2E_USERNAME || '';
const password = process.env.E2E_PASSWORD || '';

test.describe('Parcours patient -> stats -> export', () => {
  test.skip(!username || !password, 'Definir E2E_USERNAME et E2E_PASSWORD pour executer ce scenario.');

  test('ouvre les stats depuis la liste puis exporte en PDF', async ({page}) => {
    await page.route('**/api/v1/patients/search', async (route) => {
      const body = {
        items: [
          {
            id: '00000000-0000-0000-0000-000000000123',
            code: 'P-123',
            nom: 'Test',
            prenom: 'Patient',
            sexe: 'M',
            dateAdmission: '2026-01-01',
            numeroAssurance: 'ASS-001',
            etatPatient: 'PERMANENT'
          }
        ],
        total: 1,
        page: 0,
        size: 10
      };
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(body)
      });
    });

    await page.route('**/api/v1/patients/*/stats/paramedical**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          seanceCount: 2,
          avgPoidsAvantKg: 71.2,
          avgPoidsApresKg: 69.8,
          avgUfReelleMl: 1450,
          poidsEvolution: [
            {date_seance: '2026-03-01', poids_avant_kg: 72.0, poids_apres_kg: 70.0, uf_reelle_ml: 2000},
            {date_seance: '2026-03-15', poids_avant_kg: 70.4, poids_apres_kg: 69.6, uf_reelle_ml: 800}
          ],
          taEvolution: [
            {
              date_seance: '2026-03-01',
              ta_systolique_avant: 150,
              ta_diastolique_avant: 90,
              ta_systolique_apres: 138,
              ta_diastolique_apres: 82
            },
            {
              date_seance: '2026-03-15',
              ta_systolique_avant: 145,
              ta_diastolique_avant: 88,
              ta_systolique_apres: 136,
              ta_diastolique_apres: 80
            }
          ]
        })
      });
    });

    await page.route('**/api/v1/patients/*/stats/medical**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          avgHbGDl: 10.8,
          avgKtV: 1.25,
          avgFerritineNgMl: 320,
          hbTrend: [
            {date_prelevement: '2026-03-01', hb_g_dl: 10.5, kt_v_mensuel: 1.2, ferritine_ng_ml: 300},
            {date_prelevement: '2026-04-01', hb_g_dl: 11.1, kt_v_mensuel: 1.3, ferritine_ng_ml: 340}
          ],
          epoTrend: []
        })
      });
    });

    await page.route('**/api/v1/patients/*/stats/export?**format=pdf**', async (route) => {
      const pdfBytes = Buffer.from('%PDF-1.4\n1 0 obj\n<<>>\nendobj\ntrailer\n<<>>\n%%EOF');
      await route.fulfill({
        status: 200,
        headers: {
          'content-type': 'application/pdf',
          'content-disposition': 'attachment; filename=patient-stats-e2e.pdf'
        },
        body: pdfBytes
      });
    });

    await page.goto('/login');
    await page.getByTestId('login-center-select').click();
    await page.locator(`mat-option[value="${centerId}"]`).click();
    await page.getByTestId('login-username').fill(username);
    await page.getByTestId('login-password').fill(password);
    await page.getByTestId('login-submit').click();

    await page.getByTestId('nav-patients').click();

    const firstRow = page.locator('tr.patient-row').first();
    await expect(firstRow).toBeVisible();
    const rowId = await firstRow.getAttribute('data-row-id');
    expect(rowId).toBeTruthy();

    await page.getByTestId(`open-stats-${rowId}`).click();
    await expect(page).toHaveURL(new RegExp(`/patients/${rowId}/stats$`));

    await expect(page.getByTestId('stats-chart-poids')).toBeVisible();
    await expect(page.getByTestId('stats-chart-ta')).toBeVisible();
    await expect(page.getByTestId('stats-chart-uf')).toBeVisible();
    await expect(page.getByTestId('stats-chart-hb')).toBeVisible();

    const downloadPromise = page.waitForEvent('download');
    await page.getByTestId('stats-export-pdf').click();
    const download = await downloadPromise;
    expect(download.suggestedFilename()).toContain('.pdf');
  });
});

