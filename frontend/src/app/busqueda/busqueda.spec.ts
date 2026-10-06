import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  TestRequest,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { CONFIGURACION } from '../nucleo/configuracion';
import { LicitacionResumen, Pagina } from '../nucleo/modelo';
import { Busqueda } from './busqueda';

const CATALOGOS = {
  estados: [
    { codigo: 'PUB', nombre: 'En plazo' },
    { codigo: 'EV', nombre: 'Pendiente de adjudicación' },
  ],
  tiposContrato: [{ codigo: '2', nombre: 'Servicios' }],
  procedimientos: [{ codigo: '1', nombre: 'Abierto' }],
  resultados: [],
  tiposOrgano: [],
  comunidades: [],
  provincias: [],
};

function pagina(
  total: number,
  elementos: Partial<LicitacionResumen>[] = [],
): Pagina<LicitacionResumen> {
  return {
    elementos: elementos.map((e, i) => ({
      id: i + 1,
      expediente: `E-${i}`,
      titulo: `Licitación ${i + 1}`,
      estado: 'PUB',
      tipoContrato: '2',
      procedimiento: '1',
      organoId: 1,
      organo: 'Órgano',
      importeSinIva: 1000,
      plazoPresentacion: null,
      fechaPublicacion: '2026-10-05',
      nuts: null,
      lugar: null,
      cpv: [],
      numLotes: 0,
      financiacionUe: false,
      anulada: false,
      ...e,
    })),
    pagina: 1,
    tamano: 20,
    total,
    paginas: Math.ceil(total / 20),
  };
}

describe('Busqueda', () => {
  let servidor: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: '', component: Busqueda }]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: CONFIGURACION, useValue: { api: '/api', oidc: { emisor: 'x', cliente: 'y' } } },
      ],
    });
    servidor = TestBed.inject(HttpTestingController);
  });

  async function abrir(
    url: string,
  ): Promise<{ harness: RouterTestingHarness; peticion: TestRequest }> {
    const harness = await RouterTestingHarness.create(url);
    TestBed.tick();
    servidor.match('/api/v1/catalogos').forEach((p) => p.flush(CATALOGOS));
    const peticion = servidor.expectOne((p) => p.url === '/api/v1/licitaciones');
    return { harness, peticion };
  }

  it('busca con los criterios de la URL', async () => {
    const { peticion } = await abrir('/?q=limpieza&tipo=2&orden=plazo');
    expect(peticion.request.params.get('q')).toBe('limpieza');
    expect(peticion.request.params.get('tipo')).toBe('2');
    expect(peticion.request.params.get('estado')).toBe('PUB');
    expect(peticion.request.params.get('orden')).toBe('plazo');
    peticion.flush(pagina(0));
  });

  it('enseña el total y los resultados', async () => {
    const { harness, peticion } = await abrir('/');
    peticion.flush(
      pagina(2, [{ titulo: 'Limpieza de colegios' }, { titulo: 'Mantenimiento de ascensores' }]),
    );
    await harness.fixture.whenStable();
    const pantalla = harness.routeNativeElement as HTMLElement;
    expect(pantalla.querySelector('#titulo-resultados')?.textContent).toContain('2');
    expect(pantalla.querySelectorAll('rl-tarjeta-licitacion').length).toBe(2);
    expect(pantalla.textContent).toContain('Limpieza de colegios');
  });

  it('sin resultados lo dice y sugiere qué hacer', async () => {
    const { harness, peticion } = await abrir('/?q=nadadenada');
    peticion.flush(pagina(0));
    await harness.fixture.whenStable();
    expect((harness.routeNativeElement as HTMLElement).textContent).toContain(
      'No hay licitaciones con estos criterios',
    );
  });

  it('el botón de crear alerta lleva los criterios de la búsqueda', async () => {
    const { harness, peticion } = await abrir('/?q=software&cpv=72');
    peticion.flush(pagina(0));
    await harness.fixture.whenStable();
    const enlace = (harness.routeNativeElement as HTMLElement).querySelector(
      'a[href^="/alertas/nueva"]',
    );
    expect(enlace?.getAttribute('href')).toBe('/alertas/nueva?q=software&cpv=72');
  });

  it('si la API falla lo explica y deja reintentar', async () => {
    const { harness, peticion } = await abrir('/');
    peticion.flush({ detail: 'Algo ha ido mal' }, { status: 500, statusText: 'Error' });
    await harness.fixture.whenStable();
    const pantalla = harness.routeNativeElement as HTMLElement;
    expect(pantalla.querySelector('[role="alert"]')?.textContent).toContain('Algo ha ido mal');
    expect(pantalla.textContent).toContain('Reintentar');
  });
});
