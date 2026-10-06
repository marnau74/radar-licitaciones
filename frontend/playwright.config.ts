import { defineConfig, devices } from '@playwright/test';

/**
 * Pruebas de punta a punta contra la aplicación completa levantada con compose.e2e.yaml (web, API, Keycloak y
 * PostgreSQL reales, con la página de muestra del feed como datos).
 */
export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  forbidOnly: !!process.env['CI'],
  retries: process.env['CI'] ? 1 : 0,
  workers: 1,
  timeout: 60_000,
  reporter: process.env['CI'] ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: process.env['E2E_URL'] ?? 'http://localhost:8088',
    locale: 'es-ES',
    timezoneId: 'Europe/Madrid',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
