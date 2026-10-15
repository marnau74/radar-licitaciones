import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { CONFIGURACION } from '../nucleo/configuracion';
import { DetalleDeLicitacion, EstadoDeIa, ResumenGuardado } from '../nucleo/modelo';
import { Sesion } from '../nucleo/sesion';
import { ResumenesIa } from './resumenes-ia';

const PLIEGO = 'https://contrataciondelestado.es/pliego-1.pdf';
const DOCUMENTOS: DetalleDeLicitacion['documentos'] = [
  { tipo: 'PLIEGO_ADMINISTRATIVO', nombre: 'PCAP', url: PLIEGO },
  { tipo: 'OTRO', nombre: 'Anuncio', url: 'https://contrataciondelestado.es/anuncio.pdf' },
];
const RESUMEN: ResumenGuardado = {
  licitacionId: 7,
  documentoUrl: PLIEGO,
  documentoNombre: 'PCAP',
  modelo: 'claude/claude-opus-5-5',
  resumen: {
    objeto: 'Limpieza de colegios',
    presupuesto: '120.000 € sin IVA',
    duracion: '2 años',
    solvenciaEconomica: [],
    solvenciaTecnica: [],
    criteriosDeAdjudicacion: [{ criterio: 'Precio', peso: '60 puntos', automatico: true }],
    garantias: [],
    puntosDeAtencion: ['Subrogación de 12 trabajadores'],
  },
  aviso: null,
  creadoEn: '2026-10-16T10:00:00Z',
};

describe('ResumenesIa', () => {
  let servidor: HttpTestingController;
  const entrar = vi.fn();

  function preparar(iniciada: boolean): void {
    entrar.mockReset();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: CONFIGURACION, useValue: { api: '/api', oidc: { emisor: 'x', cliente: 'y' } } },
        { provide: Sesion, useValue: { iniciada: signal(iniciada), entrar } },
      ],
    });
    servidor = TestBed.inject(HttpTestingController);
  }

  async function pintar(estado: EstadoDeIa, guardados: ResumenGuardado[]): Promise<HTMLElement> {
    const fixture = TestBed.createComponent(ResumenesIa);
    fixture.componentRef.setInput('licitacion', 7);
    fixture.componentRef.setInput('documentos', DOCUMENTOS);
    TestBed.tick();
    servidor.expectOne('/api/v1/ia/estado').flush(estado);
    servidor.expectOne('/api/v1/licitaciones/7/resumenes').flush(guardados);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  it('enseña lo ya resumido sin sesión, solo de los pliegos y con el aviso de que es IA', async () => {
    preparar(false);
    const panel = await pintar({ resumenes: false, modelo: null, busqueda: false }, [RESUMEN]);
    expect(panel.textContent).toContain('Limpieza de colegios');
    expect(panel.textContent).toContain('Precio: 60 puntos');
    expect(panel.textContent).toContain('Subrogación de 12 trabajadores');
    expect(panel.textContent).toContain('puede equivocarse');
    expect(panel.textContent).not.toContain('Anuncio');
    expect(panel.querySelector('button')).toBeNull();
  });

  it('sin IA en el servidor y sin nada resumido no enseña nada', async () => {
    preparar(true);
    const panel = await pintar({ resumenes: false, modelo: null, busqueda: false }, []);
    expect(panel.querySelector('section')).toBeNull();
  });

  it('sin sesión, el botón lleva a entrar en lugar de pedir el resumen', async () => {
    preparar(false);
    const panel = await pintar(
      { resumenes: true, modelo: 'claude/claude-opus-5-5', busqueda: false },
      [],
    );
    const boton = panel.querySelector('button') as HTMLButtonElement;
    expect(boton.textContent).toContain('Entra para resumirlo');
    boton.click();
    expect(entrar).toHaveBeenCalled();
    servidor.expectNone('/api/v1/licitaciones/7/resumenes');
  });

  it('con sesión pide el resumen del pliego y lo enseña al llegar', async () => {
    preparar(true);
    const panel = await pintar(
      { resumenes: true, modelo: 'claude/claude-opus-5-5', busqueda: false },
      [],
    );
    (panel.querySelector('button') as HTMLButtonElement).click();
    const peticion = servidor.expectOne(
      (p) => p.method === 'POST' && p.url === '/api/v1/licitaciones/7/resumenes',
    );
    expect(peticion.request.body).toEqual({ documento: PLIEGO });
    peticion.flush(RESUMEN);
    // La respuesta del POST se resuelve en una promesa: se deja pasar antes de mirar
    await new Promise((resolver) => setTimeout(resolver, 0));
    TestBed.tick();
    expect(panel.textContent).toContain('Limpieza de colegios');
  });
});
