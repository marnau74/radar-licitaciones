import {
  FILTRO_INICIAL,
  consultaDesdeFiltro,
  criteriosActivos,
  filtroDesdeUrl,
  urlDesdeFiltro,
} from './filtro';

describe('filtro', () => {
  it('sin nada en la URL busca lo que está en plazo', () => {
    expect(filtroDesdeUrl({})).toEqual(FILTRO_INICIAL);
    expect(urlDesdeFiltro(FILTRO_INICIAL)).toEqual({});
  });

  it('lee y escribe la URL sin perder nada', () => {
    const params = {
      q: 'limpieza colegios',
      estado: 'PUB,EV',
      tipo: '2',
      procedimiento: '1',
      nuts: 'ES51,ES300',
      cpv: '90911',
      importeMin: '10000',
      importeMax: '500000',
      plazoDesde: '2026-10-01',
      plazoHasta: '2026-10-31',
      organo: '42',
      abiertas: 'false',
      fondosUe: 'true',
      orden: 'plazo',
      pagina: '3',
    };
    const filtro = filtroDesdeUrl(params);
    expect(filtro.estado).toEqual(['PUB', 'EV']);
    expect(filtro.nuts).toEqual(['ES51', 'ES300']);
    expect(filtro.importeMin).toBe(10000);
    expect(filtro.organo).toBe(42);
    expect(filtro.fondosUe).toBe(true);
    expect(urlDesdeFiltro(filtro)).toEqual(params);
  });

  it('un estado vacío en la URL son todos los estados', () => {
    const filtro = filtroDesdeUrl({ estado: '' });
    expect(filtro.estado).toEqual([]);
    expect(urlDesdeFiltro(filtro)).toEqual({ estado: '' });
    expect(consultaDesdeFiltro(filtro).has('estado')).toBe(false);
  });

  it('ignora lo que no es válido en vez de romper la página', () => {
    const filtro = filtroDesdeUrl({
      estado: 'PUB,<script>',
      cpv: '72,abc,123456789',
      nuts: 'ES51,es51',
      importeMin: '-5',
      importeMax: 'mucho',
      plazoDesde: 'ayer',
      organo: '3.5',
      orden: 'aleatorio',
      pagina: '99999',
    });
    expect(filtro.estado).toEqual(['PUB']);
    expect(filtro.cpv).toEqual(['72']);
    expect(filtro.nuts).toEqual(['ES51']);
    expect(filtro.importeMin).toBeNull();
    expect(filtro.importeMax).toBeNull();
    expect(filtro.plazoDesde).toBeNull();
    expect(filtro.organo).toBeNull();
    expect(filtro.orden).toBe('relevancia');
    expect(filtro.pagina).toBe(1);
  });

  it('pide a la API la página, el orden y los criterios', () => {
    const consulta = consultaDesdeFiltro({
      ...FILTRO_INICIAL,
      q: '  software ',
      cpv: ['72', '48'],
      pagina: 2,
    });
    expect(consulta.get('q')).toBe('software');
    expect(consulta.get('estado')).toBe('PUB');
    expect(consulta.get('cpv')).toBe('72,48');
    expect(consulta.get('pagina')).toBe('2');
    expect(consulta.get('tamano')).toBe('20');
    expect(consulta.get('orden')).toBe('relevancia');
    expect(consulta.get('abiertas')).toBe('true');
  });

  it('cuenta los criterios puestos además del texto', () => {
    expect(criteriosActivos(FILTRO_INICIAL)).toBe(0);
    expect(
      criteriosActivos({ ...FILTRO_INICIAL, q: 'algo', tipo: ['1', '2'], fondosUe: true }),
    ).toBe(3);
  });
});
