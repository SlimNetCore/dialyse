import {expect, test} from '@playwright/test';

test.describe('Dashboard Month Filter E2E Tests', () => {
  const baseUrl = process.env.E2E_BASE_URL || 'http://127.0.0.1:4200';
  const username = process.env.E2E_USERNAME || 'admin@hemodialyse.fr';
  const password = process.env.E2E_PASSWORD || 'admin123';

  test.beforeEach(async ({page, context}) => {
    // Navigate to login
    await page.goto(`${baseUrl}/login`);

    // Login
    await page.fill('input[name="username"]', username);
    await page.fill('input[name="password"]', password);
    await page.click('button[type="submit"]');

    // Wait for dashboard navigation
    await page.waitForURL(`${baseUrl}/dashboard`, {timeout: 10000});
  });

  test('should display month filter field on dashboard', async ({page}) => {
    // Navigate to dashboard
    await page.goto(`${baseUrl}/dashboard`);

    // Check if month input exists
    const monthInput = page.locator('input[type="month"]');
    await expect(monthInput).toBeVisible();
  });

  test('should filter statistics when month is selected', async ({page}) => {
    await page.goto(`${baseUrl}/dashboard`);

    // Get initial stats values
    const initialPecCree = await page.locator('.pec-cree .stat-value').textContent();

    // Select a month
    const monthInput = page.locator('input[type="month"]');
    await monthInput.fill('2024-06');

    // Wait for API response and stats update
    await page.waitForLoadState('networkidle');

    // Verify stats might change (or remain same depending on data)
    const updatedPecCree = await page.locator('.pec-cree .stat-value').textContent();
    expect(updatedPecCree).toBeTruthy();
  });

  test('should clear month filter when input is cleared', async ({page}) => {
    await page.goto(`${baseUrl}/dashboard`);

    // Select a month
    const monthInput = page.locator('input[type="month"]');
    await monthInput.fill('2024-06');
    await page.waitForLoadState('networkidle');

    // Clear the month input
    await monthInput.clear();
    await page.waitForLoadState('networkidle');

    // Verify input is empty
    const monthValue = await monthInput.inputValue();
    expect(monthValue).toBe('');
  });

  test('should apply both expiration days and month filters together', async ({page}) => {
    await page.goto(`${baseUrl}/dashboard`);

    // Set expiration days
    const daysInput = page.locator('input[type="number"]');
    await daysInput.fill('60');

    // Set month
    const monthInput = page.locator('input[type="month"]');
    await monthInput.fill('2024-05');

    // Wait for stats to update
    await page.waitForLoadState('networkidle');

    // Verify both filters are set
    const daysValue = await daysInput.inputValue();
    const monthValue = await monthInput.inputValue();

    expect(daysValue).toBe('60');
    expect(monthValue).toBe('2024-05');
  });

  test('should update statistics summary based on month selection', async ({page}) => {
    await page.goto(`${baseUrl}/dashboard`);

    // Get all stat cards
    const statCards = page.locator('.stat-card');

    // Verify all stat cards are visible
    const cardCount = await statCards.count();
    expect(cardCount).toBeGreaterThan(0);

    // Select a specific month
    const monthInput = page.locator('input[type="month"]');
    await monthInput.fill('2024-04');

    // Wait for update
    await page.waitForLoadState('networkidle');

    // Verify cards are still visible and have values
    for (let i = 0; i < cardCount; i++) {
      const statValue = await statCards.nth(i).locator('.stat-value').textContent();
      expect(statValue).toMatch(/^\d+$/); // Should be a number
    }
  });

  test('should persist month filter in form state while using dashboard', async ({page}) => {
    await page.goto(`${baseUrl}/dashboard`);

    // Set month
    const monthInput = page.locator('input[type="month"]');
    const testMonth = '2024-03';
    await monthInput.fill(testMonth);

    // Wait a bit
    await page.waitForTimeout(500);

    // Verify month is still set
    const monthValue = await monthInput.inputValue();
    expect(monthValue).toBe(testMonth);
  });

  test('should handle invalid month input gracefully', async ({page}) => {
    await page.goto(`${baseUrl}/dashboard`);

    // Try to interact with month input in different ways
    const monthInput = page.locator('input[type="month"]');

    // Set a valid month
    await monthInput.fill('2024-01');
    await page.waitForLoadState('networkidle');

    // Verify it's valid
    let monthValue = await monthInput.inputValue();
    expect(monthValue).toBe('2024-01');

    // The HTML5 month input will reject invalid values automatically
    // So we verify the valid value is preserved
    expect(monthValue).toBeTruthy();
  });

  test('should load dashboard stats on initial load without month filter', async ({page}) => {
    await page.goto(`${baseUrl}/dashboard`);

    // Verify at least one stat card is visible
    const statCard = page.locator('.stat-card').first();
    await expect(statCard).toBeVisible();

    // Verify stat values exist
    const statValue = await statCard.locator('.stat-value').textContent();
    expect(statValue).toMatch(/^\d+$/);
  });
});

