import AxeBuilder from '@axe-core/playwright';
import { Page, expect, test } from '@playwright/test';

/**
 * Los datos son la página de muestra del feed (10 licitaciones reales y 2 anulaciones). Las búsquedas usan
 * «abiertas=false» y todos los estados: los plazos de la muestra son de octubre de 2026 y estas pruebas tienen que
 * pasar igual cuando esas fechas queden atrás.
 */
const TODAS = '/?estado=&abiertas=false';

test.beforeAll(async ({ request }) => {
  // La API carga la muestra al arrancar, en segundo plano: se espera a que esté.
  await expect
    .poll(
      async () => {
        const respuesta = await request.get('/api/v1/ingesta/estado');
        return respuesta.ok()
          ? ((await respuesta.json()) as { licitaciones: number }).licitaciones
          : 0;
      },
      { timeout: 120_000, intervals: [1_000] },
    )
    .toBe(10);
});

async function sinProblemasDeAccesibilidad(page: Page): Promise<void> {
  const resultado = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21aa'])
    .analyze();
  const graves = resultado.violations.filter(
    (v) => v.impact === 'serious' || v.impact === 'critical',
  );
  expect(
    graves.map((v) => `${v.id}: ${v.nodes.map((n) => n.target.join(' ')).join(', ')}`),
  ).toEqual([]);
}

test('busca por texto sin tildes y abre la ficha', async ({ page }) => {
  await page.goto(TODAS);
  await page.getByRole('searchbox', { name: 'Buscar' }).fill('polizas seguros');
  await page.getByRole('button', { name: 'Buscar', exact: true }).click();

  await expect(page).toHaveURL(/q=polizas/);
  await expect(page.getByRole('heading', { name: /1 licitación/ })).toBeVisible();
  await page.getByRole('link', { name: /Servicio de pólizas de seguros/ }).click();

  await expect(page.getByRole('heading', { level: 1 })).toHaveText(
    /Servicio de pólizas de seguros/,
  );
  await expect(page.getByRole('heading', { name: 'Lotes (5)' })).toBeVisible();
  // Ganó dos de los cinco lotes.
  await expect(page.getByText('ALLIANZ,COMPAÑÍA DE SEGUROS Y REASEGUROS S.A.')).toHaveCount(2);
  await expect(page.getByText('Autoridad local')).toBeVisible();
  await sinProblemasDeAccesibilidad(page);
});

test('filtra por tipo de contrato y lugar desde la URL y desde el panel', async ({ page }) => {
  await page.goto(`${TODAS}&tipo=3&nuts=ES111`);
  await expect(page.getByRole('heading', { name: /2 licitaciones/ })).toBeVisible();

  await page.goto(TODAS);
  await expect(page.getByRole('heading', { name: /9 licitaciones/ })).toBeVisible();
  await page.getByRole('checkbox', { name: 'Obras' }).check();
  await expect(page).toHaveURL(/tipo=3/);
  await expect(page.getByRole('heading', { name: /2 licitaciones/ })).toBeVisible();
  await sinProblemasDeAccesibilidad(page);
});

test('las anuladas no salen salvo que se pidan', async ({ page }) => {
  await page.goto('/?estado=ANUL&abiertas=false');
  await expect(page.getByText('No hay licitaciones con estos criterios')).toBeVisible();
});

test('las cifras cuentan lo cargado', async ({ page }) => {
  await page.goto('/cifras');
  await expect(page.getByRole('heading', { name: 'Cifras', level: 1 })).toBeVisible();
  await expect(page.getByText('licitaciones en el radar')).toBeVisible();
  await expect(page.locator('.dato').last()).toContainText('10');
  await sinProblemasDeAccesibilidad(page);
});

test('con sesión se crea, se ve y se borra una alerta', async ({ page }) => {
  await page.goto('/alertas');

  // Keycloak (realm de desarrollo, cuenta de ejemplo).
  await page.locator('#username').fill('ana@demo.example');
  await page.locator('#password').fill('radar-demo-2026');
  await page.locator('#kc-login').click();

  await expect(page.getByRole('heading', { name: 'Mis alertas' })).toBeVisible();
  await expect(page.getByText('Aún no tienes alertas.')).toBeVisible();

  await page.getByRole('link', { name: 'Nueva alerta' }).first().click();
  await page.getByLabel('Nombre').fill('Obras en A Coruña');
  await page.getByRole('checkbox', { name: 'Obras', exact: true }).check();
  await page.getByLabel('Lugar de ejecución').selectOption('ES111');
  await expect(page.getByRole('heading', { name: 'Ahora mismo' })).toBeVisible();
  await page.getByRole('button', { name: 'Crear alerta' }).click();

  await expect(page).toHaveURL(/\/alertas$/);
  await expect(page.getByRole('link', { name: 'Obras en A Coruña' })).toBeVisible();
  await expect(page.getByText(/Obras · en A Coruña/)).toBeVisible();
  await sinProblemasDeAccesibilidad(page);

  page.once('dialog', (dialogo) => void dialogo.accept());
  await page.getByRole('button', { name: 'Borrar' }).click();
  await expect(page.getByText('Aún no tienes alertas.')).toBeVisible();
});

test('una dirección que no existe da una página clara', async ({ page }) => {
  await page.goto('/no-existe');
  await expect(page.getByRole('heading', { name: 'Esta página no existe' })).toBeVisible();
  await page.goto('/licitaciones/1');
  await expect(
    page.getByRole('heading', { name: 'No hemos encontrado esa licitación' }),
  ).toBeVisible();
});
