/** Tipos de las respuestas de la API (ver /api/docs). Las fechas llegan como texto ISO 8601. */

export interface Pagina<T> {
  elementos: T[];
  pagina: number;
  tamano: number;
  total: number;
  paginas: number;
}

export interface Codigo {
  codigo: string;
  nombre: string;
}

export interface LicitacionResumen {
  id: number;
  expediente: string;
  titulo: string;
  estado: string;
  tipoContrato: string | null;
  procedimiento: string | null;
  organoId: number;
  organo: string;
  importeSinIva: number | null;
  plazoPresentacion: string | null;
  fechaPublicacion: string | null;
  nuts: string | null;
  lugar: string | null;
  cpv: string[];
  numLotes: number;
  financiacionUe: boolean;
  anulada: boolean;
}

export interface DetalleDeLicitacion {
  id: number;
  expediente: string;
  titulo: string;
  url: string | null;
  estado: Codigo;
  tipoContrato: Codigo | null;
  subtipoContrato: string | null;
  procedimiento: Codigo | null;
  organo: {
    id: number;
    nombre: string;
    nif: string | null;
    dir3: string | null;
    tipo: Codigo | null;
    ciudad: string | null;
    codigoPostal: string | null;
    web: string | null;
    perfilContratante: string | null;
    jerarquia: string[];
  };
  importeSinIva: number | null;
  importeConIva: number | null;
  valorEstimado: number | null;
  cpv: Codigo[];
  nuts: Codigo | null;
  lugar: string | null;
  plazoPresentacion: string | null;
  fechaPublicacion: string | null;
  duracion: string | null;
  financiacionUe: boolean;
  anulada: boolean;
  lotes: {
    numero: string;
    objeto: string | null;
    importeSinIva: number | null;
    cpv: Codigo[];
    nuts: Codigo | null;
  }[];
  resultados: {
    lote: string | null;
    resultado: Codigo | null;
    fechaAdjudicacion: string | null;
    ofertasRecibidas: number | null;
    adjudicatario: string | null;
    adjudicatarioNif: string | null;
    importeSinIva: number | null;
    importeConIva: number | null;
    pyme: boolean | null;
  }[];
  documentos: {
    tipo: 'PLIEGO_ADMINISTRATIVO' | 'PLIEGO_TECNICO' | 'OTRO';
    nombre: string | null;
    url: string;
  }[];
  actualizadaEn: string;
  vistaPorPrimeraVez: string;
}

export interface Catalogos {
  estados: Codigo[];
  tiposContrato: Codigo[];
  procedimientos: Codigo[];
  resultados: Codigo[];
  tiposOrgano: Codigo[];
  comunidades: Codigo[];
  provincias: Codigo[];
}

export interface Grupo {
  codigo: Codigo;
  licitaciones: number;
  importe: number;
}

export interface Estadisticas {
  calculadasEn: string;
  total: number;
  enPlazo: number;
  importeEnPlazo: number;
  publicadasUltimos30Dias: number;
  enPlazoPorTipo: Grupo[];
  enPlazoPorComunidad: Grupo[];
  publicadasPorMes: { mes: string; licitaciones: number; importe: number }[];
}

export interface ResumenDeIngesta {
  id: number;
  origen: string;
  descripcion: string;
  inicio: string;
  fin: string;
  estado: string;
  ficheros: number;
  leidas: number;
  nuevas: number;
  actualizadas: number;
  sinCambios: number;
  anuladas: number;
  ilegibles: number;
  error: string | null;
}

export interface EstadoDeIngesta {
  enCurso: boolean;
  licitaciones: number;
  ultimas: ResumenDeIngesta[];
}

export interface OrganoResumen {
  id: number;
  nombre: string;
  ciudad: string | null;
  licitaciones: number;
}

/** Lo que se envía al crear o modificar una alerta. */
export interface DatosDeAlerta {
  nombre: string;
  texto: string | null;
  tiposContrato: string[];
  procedimientos: string[];
  nuts: string[];
  cpv: string[];
  importeMinimo: number | null;
  importeMaximo: number | null;
  soloFondosUe: boolean;
  porCorreo: boolean;
  activa: boolean;
}

export interface Alerta extends DatosDeAlerta {
  id: number;
  correo: string;
  creadaEn: string;
  actualizadaEn: string;
}

export interface Aviso {
  id: number;
  alertaId: number;
  alerta: string;
  creadoEn: string;
  leido: boolean;
  licitacion: LicitacionResumen;
}

/** Un error de la API (RFC 9457). */
export interface Problema {
  title?: string;
  detail?: string;
  status?: number;
  errores?: { campo: string; mensaje: string }[];
}
