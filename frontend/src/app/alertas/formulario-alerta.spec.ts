import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { CONFIGURACION } from '../nucleo/configuracion';
import { Sesion } from '../nucleo/sesion';
import { FormularioAlerta } from './formulario-alerta';

describe('FormularioAlerta', () => {
  let servidor: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'alertas/nueva', component: FormularioAlerta },
          { path: 'alertas', children: [] },
        ]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: CONFIGURACION, useValue: { api: '/api', oidc: { emisor: 'x', cliente: 'y' } } },
        {
          provide: Sesion,
          useValue: { usuario: signal({ nombre: 'Ana', correo: 'ana@demo.example' }) },
        },
      ],
    });
    servidor = TestBed.inject(HttpTestingController);
  });

  function responderLoPendiente(): void {
    servidor.match('/api/v1/catalogos').forEach((p) =>
      p.flush({
        estados: [],
        tiposContrato: [{ codigo: '2', nombre: 'Servicios' }],
        procedimientos: [],
        resultados: [],
        tiposOrgano: [],
        comunidades: [],
        provincias: [],
      }),
    );
    servidor
      .match((p) => p.url === '/api/v1/licitaciones')
      .forEach((p) => p.flush({ elementos: [], pagina: 1, tamano: 3, total: 12, paginas: 4 }));
    servidor.match((p) => p.url === '/api/v1/catalogos/cpv').forEach((p) => p.flush([]));
  }

  it('llega rellena con los criterios de la búsqueda', async () => {
    const harness = await RouterTestingHarness.create('/alertas/nueva?q=software&tipo=2&cpv=72');
    TestBed.tick();
    responderLoPendiente();
    await harness.fixture.whenStable();
    const pantalla = harness.routeNativeElement as HTMLElement;
    expect((pantalla.querySelector('#alerta-nombre') as HTMLInputElement).value).toBe('software');
    expect((pantalla.querySelector('#alerta-texto') as HTMLInputElement).value).toBe('software');
    expect(pantalla.querySelector('rl-selector-cpv')?.textContent).toContain('72');
  });

  it('no envía una alerta sin nombre ni criterios y explica por qué', async () => {
    const harness = await RouterTestingHarness.create('/alertas/nueva');
    TestBed.tick();
    responderLoPendiente();
    await harness.fixture.whenStable();
    const pantalla = harness.routeNativeElement as HTMLElement;
    (pantalla.querySelector('button[type="submit"]') as HTMLButtonElement).click();
    await harness.fixture.whenStable();
    const errores = pantalla.querySelector('#errores-alerta')?.textContent ?? '';
    expect(errores).toContain('Ponle un nombre');
    expect(errores).toContain('al menos un criterio');
    servidor.expectNone((p) => p.method === 'POST');
  });

  it('crea la alerta con lo rellenado', async () => {
    const harness = await RouterTestingHarness.create('/alertas/nueva?q=software');
    TestBed.tick();
    responderLoPendiente();
    await harness.fixture.whenStable();
    const pantalla = harness.routeNativeElement as HTMLElement;
    (pantalla.querySelector('button[type="submit"]') as HTMLButtonElement).click();
    const envio = servidor.expectOne((p) => p.method === 'POST' && p.url === '/api/v1/alertas');
    expect(envio.request.body).toMatchObject({
      nombre: 'software',
      texto: 'software',
      porCorreo: true,
      activa: true,
    });
    envio.flush({ id: 1 });
  });
});
