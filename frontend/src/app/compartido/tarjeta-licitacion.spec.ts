import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { CONFIGURACION } from '../nucleo/configuracion';
import { LicitacionResumen } from '../nucleo/modelo';
import { TarjetaLicitacion } from './tarjeta-licitacion';

const LICITACION: LicitacionResumen = {
  id: 20622371,
  expediente: '3686/2026',
  titulo: 'Obras de urbanización',
  estado: 'PUB',
  tipoContrato: '3',
  procedimiento: '9',
  organoId: 7,
  organo: 'Ayuntamiento de Ejemplo',
  importeSinIva: 47120.52,
  plazoPresentacion: '2099-10-26T23:59:00+02:00',
  fechaPublicacion: '2026-10-05',
  nuts: 'ES111',
  lugar: 'A Coruña',
  cpv: ['45000000'],
  numLotes: 0,
  financiacionUe: true,
  anulada: false,
};

describe('TarjetaLicitacion', () => {
  async function pintar(licitacion: LicitacionResumen): Promise<HTMLElement> {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: CONFIGURACION, useValue: { api: '/api', oidc: { emisor: 'x', cliente: 'y' } } },
      ],
    });
    const fixture = TestBed.createComponent(TarjetaLicitacion);
    fixture.componentRef.setInput('licitacion', licitacion);
    TestBed.tick();
    TestBed.inject(HttpTestingController)
      .expectOne('/api/v1/catalogos')
      .flush({
        estados: [{ codigo: 'PUB', nombre: 'En plazo' }],
        tiposContrato: [{ codigo: '3', nombre: 'Obras' }],
        procedimientos: [],
        resultados: [],
        tiposOrgano: [],
        comunidades: [],
        provincias: [],
      });
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  it('enseña el título con enlace a la ficha, el estado y el tipo con su nombre', async () => {
    const tarjeta = await pintar(LICITACION);
    const enlace = tarjeta.querySelector('h3 a') as HTMLAnchorElement;
    expect(enlace.textContent?.trim()).toBe('Obras de urbanización');
    expect(enlace.getAttribute('href')).toBe('/licitaciones/20622371');
    expect(tarjeta.querySelector('.estado')?.textContent?.trim()).toBe('En plazo');
    expect(tarjeta.textContent).toContain('Obras');
    expect(tarjeta.textContent).toContain('Fondos UE');
    expect(tarjeta.textContent).toContain('A Coruña');
  });

  it('una anulada se marca como tal', async () => {
    const tarjeta = await pintar({ ...LICITACION, anulada: true });
    expect(tarjeta.querySelector('.estado')?.textContent?.trim()).toBe('Anulada');
    expect(tarjeta.querySelector('.tarjeta')?.classList).toContain('anulada');
  });
});
