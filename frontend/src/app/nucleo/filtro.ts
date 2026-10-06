import { HttpParams } from '@angular/common/http';
import { Params } from '@angular/router';

/**
 * El filtro de búsqueda. Vive en la URL (se puede compartir, guardar y volver atrás) y de ahí se pasa a la API. Estas
 * funciones son la única traducción entre los tres: URL, formulario y petición.
 */
export interface Filtro {
  q: string;
  estado: string[];
  tipo: string[];
  procedimiento: string[];
  nuts: string[];
  cpv: string[];
  importeMin: number | null;
  importeMax: number | null;
  plazoDesde: string | null;
  plazoHasta: string | null;
  organo: number | null;
  /** Solo las que aún admiten ofertas: el estado «publicada» no basta, la Plataforma no lo cambia al cerrar el plazo. */
  abiertas: boolean;
  fondosUe: boolean;
  orden: Orden;
  pagina: number;
}

export type Orden = 'relevancia' | 'publicacion' | 'plazo' | 'importe';

export const ORDENES: { valor: Orden; nombre: string }[] = [
  { valor: 'relevancia', nombre: 'Más relevantes' },
  { valor: 'publicacion', nombre: 'Más recientes' },
  { valor: 'plazo', nombre: 'Cierran antes' },
  { valor: 'importe', nombre: 'Mayor importe' },
];

/** Sin nada en la URL se enseñan las publicadas con el plazo abierto: es lo que se busca casi siempre. */
export const FILTRO_INICIAL: Filtro = {
  q: '',
  estado: ['PUB'],
  tipo: [],
  procedimiento: [],
  nuts: [],
  cpv: [],
  importeMin: null,
  importeMax: null,
  plazoDesde: null,
  plazoHasta: null,
  organo: null,
  abiertas: true,
  fondosUe: false,
  orden: 'relevancia',
  pagina: 1,
};

export const TAMANO_PAGINA = 20;

const FECHA = /^\d{4}-\d{2}-\d{2}$/;

function lista(valor: unknown, patron: RegExp): string[] {
  const valores = Array.isArray(valor) ? valor : typeof valor === 'string' ? valor.split(',') : [];
  return [...new Set(valores.map((v) => String(v).trim()).filter((v) => patron.test(v)))];
}

function numero(valor: unknown): number | null {
  if (typeof valor !== 'string' || valor.trim() === '') {
    return null;
  }
  const n = Number(valor);
  return Number.isFinite(n) && n >= 0 ? n : null;
}

/** Lee el filtro de la URL. Lo que no es válido se ignora en vez de romper la página. */
export function filtroDesdeUrl(params: Params): Filtro {
  // «estado» presente aunque vacío («estado=») significa «todos los estados»; ausente, el filtro inicial.
  const estado =
    'estado' in params ? lista(params['estado'], /^[A-Z]{2,5}$/) : FILTRO_INICIAL.estado;
  const orden = ORDENES.some((o) => o.valor === params['orden'])
    ? (params['orden'] as Orden)
    : 'relevancia';
  const pagina = Math.trunc(numero(params['pagina']) ?? 1);
  const organo = numero(params['organo']);
  return {
    q: typeof params['q'] === 'string' ? params['q'].slice(0, 200) : '',
    estado,
    tipo: lista(params['tipo'], /^\d{1,3}$/),
    procedimiento: lista(params['procedimiento'], /^\d{1,3}$/),
    nuts: lista(params['nuts'], /^[A-Z]{2}[0-9A-Z]{0,3}$/),
    cpv: lista(params['cpv'], /^\d{2,8}$/),
    importeMin: numero(params['importeMin']),
    importeMax: numero(params['importeMax']),
    plazoDesde: FECHA.test(params['plazoDesde'] ?? '') ? params['plazoDesde'] : null,
    plazoHasta: FECHA.test(params['plazoHasta'] ?? '') ? params['plazoHasta'] : null,
    organo: organo && Number.isInteger(organo) ? organo : null,
    abiertas: params['abiertas'] !== 'false',
    fondosUe: params['fondosUe'] === 'true',
    orden,
    pagina: pagina >= 1 && pagina <= 500 ? pagina : 1,
  };
}

/** Los parámetros de URL de un filtro: solo lo que difiere del filtro inicial, para que la URL sea corta. */
export function urlDesdeFiltro(filtro: Filtro): Params {
  const params: Params = {};
  if (filtro.q.trim()) params['q'] = filtro.q.trim();
  if (filtro.estado.join() !== FILTRO_INICIAL.estado.join())
    params['estado'] = filtro.estado.join(',');
  if (filtro.tipo.length) params['tipo'] = filtro.tipo.join(',');
  if (filtro.procedimiento.length) params['procedimiento'] = filtro.procedimiento.join(',');
  if (filtro.nuts.length) params['nuts'] = filtro.nuts.join(',');
  if (filtro.cpv.length) params['cpv'] = filtro.cpv.join(',');
  if (filtro.importeMin !== null) params['importeMin'] = String(filtro.importeMin);
  if (filtro.importeMax !== null) params['importeMax'] = String(filtro.importeMax);
  if (filtro.plazoDesde) params['plazoDesde'] = filtro.plazoDesde;
  if (filtro.plazoHasta) params['plazoHasta'] = filtro.plazoHasta;
  if (filtro.organo !== null) params['organo'] = String(filtro.organo);
  if (!filtro.abiertas) params['abiertas'] = 'false';
  if (filtro.fondosUe) params['fondosUe'] = 'true';
  if (filtro.orden !== 'relevancia') params['orden'] = filtro.orden;
  if (filtro.pagina > 1) params['pagina'] = String(filtro.pagina);
  return params;
}

/** Los parámetros de la petición a /licitaciones. */
export function consultaDesdeFiltro(filtro: Filtro): HttpParams {
  let params = new HttpParams()
    .set('tamano', TAMANO_PAGINA)
    .set('pagina', filtro.pagina)
    .set('orden', filtro.orden);
  const texto = filtro.q.trim();
  if (texto) params = params.set('q', texto);
  for (const [clave, valores] of [
    ['estado', filtro.estado],
    ['tipo', filtro.tipo],
    ['procedimiento', filtro.procedimiento],
    ['nuts', filtro.nuts],
    ['cpv', filtro.cpv],
  ] as const) {
    if (valores.length) params = params.set(clave, valores.join(','));
  }
  if (filtro.importeMin !== null) params = params.set('importeMin', filtro.importeMin);
  if (filtro.importeMax !== null) params = params.set('importeMax', filtro.importeMax);
  if (filtro.plazoDesde) params = params.set('plazoDesde', filtro.plazoDesde);
  if (filtro.plazoHasta) params = params.set('plazoHasta', filtro.plazoHasta);
  if (filtro.organo !== null) params = params.set('organo', filtro.organo);
  if (filtro.abiertas) params = params.set('abiertas', true);
  if (filtro.fondosUe) params = params.set('fondosUe', true);
  return params;
}

/** Cuántos criterios hay puestos además del texto (para el botón de filtros en el móvil). */
export function criteriosActivos(filtro: Filtro): number {
  return (
    (filtro.estado.join() !== FILTRO_INICIAL.estado.join() ? 1 : 0) +
    filtro.tipo.length +
    filtro.procedimiento.length +
    filtro.nuts.length +
    filtro.cpv.length +
    (filtro.importeMin !== null ? 1 : 0) +
    (filtro.importeMax !== null ? 1 : 0) +
    (filtro.plazoDesde ? 1 : 0) +
    (filtro.plazoHasta ? 1 : 0) +
    (filtro.organo !== null ? 1 : 0) +
    (filtro.abiertas !== FILTRO_INICIAL.abiertas ? 1 : 0) +
    (filtro.fondosUe ? 1 : 0)
  );
}
