import { Pipe, PipeTransform } from '@angular/core';

/** Formatos en español de España. Funciones puras (fáciles de probar) y sus pipes para las plantillas. */

const EUROS = new Intl.NumberFormat('es-ES', {
  style: 'currency',
  currency: 'EUR',
  maximumFractionDigits: 0,
});
const EUROS_CON_CENTIMOS = new Intl.NumberFormat('es-ES', { style: 'currency', currency: 'EUR' });
const EUROS_COMPACTO = new Intl.NumberFormat('es-ES', {
  style: 'currency',
  currency: 'EUR',
  notation: 'compact',
  maximumFractionDigits: 1,
});
const ENTERO = new Intl.NumberFormat('es-ES');
const ZONA = 'Europe/Madrid';
const FECHA = new Intl.DateTimeFormat('es-ES', {
  day: 'numeric',
  month: 'short',
  year: 'numeric',
  timeZone: ZONA,
});
const FECHA_HORA = new Intl.DateTimeFormat('es-ES', {
  day: 'numeric',
  month: 'short',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  timeZone: ZONA,
});
const MES = new Intl.DateTimeFormat('es-ES', { month: 'short', year: '2-digit', timeZone: 'UTC' });

export function euros(importe: number | null | undefined, centimos = false): string {
  if (importe === null || importe === undefined) {
    return '—';
  }
  return (centimos && !Number.isInteger(importe) ? EUROS_CON_CENTIMOS : EUROS).format(importe);
}

export function eurosCompacto(importe: number): string {
  return EUROS_COMPACTO.format(importe);
}

export function entero(n: number | null | undefined): string {
  return n === null || n === undefined ? '—' : ENTERO.format(n);
}

/** Una fecha (`2026-10-05`) o fecha y hora (`2026-10-05T23:59:00+02:00`), siempre en hora peninsular. */
export function fecha(valor: string | null | undefined, conHora = false): string {
  if (!valor) {
    return '—';
  }
  // Una fecha sin hora es un día: se interpreta a mediodía para que ninguna zona horaria la cambie de día.
  const momento = new Date(valor.length === 10 ? `${valor}T12:00:00Z` : valor);
  if (Number.isNaN(momento.getTime())) {
    return valor;
  }
  return (conHora ? FECHA_HORA : FECHA).format(momento);
}

/** `2026-09` → «sept 26». */
export function mes(valor: string): string {
  return MES.format(new Date(`${valor}-15T00:00:00Z`));
}

/** Cuánto falta para que cierre el plazo, en palabras: «cierra hoy», «quedan 3 días», «cerrado». */
export function plazoRestante(plazo: string | null | undefined, ahora: Date = new Date()): string {
  if (!plazo) {
    return 'Sin plazo publicado';
  }
  const fin = new Date(plazo);
  const ms = fin.getTime() - ahora.getTime();
  if (Number.isNaN(ms)) {
    return '';
  }
  if (ms <= 0) {
    return 'Plazo cerrado';
  }
  const horas = ms / 3_600_000;
  if (horas < 24) {
    return horas < 1 ? 'Cierra en menos de una hora' : `Cierra en ${Math.floor(horas)} h`;
  }
  const dias = Math.floor(horas / 24);
  return dias === 1 ? 'Queda 1 día' : `Quedan ${dias} días`;
}

/** Si el plazo cierra en menos de 3 días (para resaltarlo). */
export function plazoUrgente(plazo: string | null | undefined, ahora: Date = new Date()): boolean {
  if (!plazo) {
    return false;
  }
  const ms = new Date(plazo).getTime() - ahora.getTime();
  return ms > 0 && ms < 3 * 86_400_000;
}

/**
 * La etiqueta de estado que se enseña. «Publicada» se matiza con el plazo real: la Plataforma la mantiene publicada
 * hasta valorar las ofertas, aunque el plazo ya haya cerrado.
 */
export function etiquetaDeEstado(
  estado: string,
  nombre: string,
  plazo: string | null,
  anulada: boolean,
  ahora: Date = new Date(),
): { texto: string; clase: string } {
  if (anulada) {
    return { texto: 'Anulada', clase: 'anulada' };
  }
  if (estado === 'PUB' && plazo) {
    return new Date(plazo).getTime() > ahora.getTime()
      ? { texto: 'En plazo', clase: 'PUB' }
      : { texto: 'Plazo cerrado', clase: 'cerrado' };
  }
  return { texto: nombre, clase: estado };
}

@Pipe({ name: 'euros' })
export class EurosPipe implements PipeTransform {
  transform(importe: number | null | undefined, centimos = false): string {
    return euros(importe, centimos);
  }
}

@Pipe({ name: 'entero' })
export class EnteroPipe implements PipeTransform {
  transform(n: number | null | undefined): string {
    return entero(n);
  }
}

@Pipe({ name: 'fecha' })
export class FechaPipe implements PipeTransform {
  transform(valor: string | null | undefined, conHora = false): string {
    return fecha(valor, conHora);
  }
}
