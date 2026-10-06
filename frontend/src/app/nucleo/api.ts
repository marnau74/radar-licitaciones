import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { CONFIGURACION } from './configuracion';
import { Alerta, DatosDeAlerta, Problema } from './modelo';

/**
 * Las operaciones que cambian algo (alertas, avisos). Las lecturas se hacen con `httpResource` en cada página, que ya
 * gestiona la carga, el error y la cancelación al cambiar de filtro.
 */
@Injectable({ providedIn: 'root' })
export class Api {
  private readonly http = inject(HttpClient);
  readonly base = inject(CONFIGURACION).api;

  crearAlerta(datos: DatosDeAlerta): Promise<Alerta> {
    return firstValueFrom(this.http.post<Alerta>(`${this.base}/v1/alertas`, datos));
  }

  modificarAlerta(id: number, datos: DatosDeAlerta): Promise<Alerta> {
    return firstValueFrom(this.http.put<Alerta>(`${this.base}/v1/alertas/${id}`, datos));
  }

  borrarAlerta(id: number): Promise<unknown> {
    return firstValueFrom(this.http.delete(`${this.base}/v1/alertas/${id}`));
  }

  marcarAvisosLeidos(): Promise<unknown> {
    return firstValueFrom(this.http.post(`${this.base}/v1/avisos/leidos`, null));
  }

  /** Solo administradores: el feed (lo nuevo) o un paquete histórico (AAAA o AAAAMM). */
  lanzarIngesta(origen: 'feed' | 'paquete', periodo: string | null): Promise<unknown> {
    return firstValueFrom(this.http.post(`${this.base}/v1/admin/ingestas`, { origen, periodo }));
  }
}

/** Un mensaje para el usuario a partir de un error de la API. */
export function mensajeDeError(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 0) {
      return 'No se puede conectar con el servidor. Comprueba tu conexión y vuelve a intentarlo.';
    }
    const problema = error.error as Problema | null;
    if (problema?.errores?.length) {
      return problema.errores.map((e) => e.mensaje).join('. ');
    }
    if (problema?.detail) {
      return problema.detail;
    }
    if (error.status === 401) {
      return 'Tu sesión ha caducado: vuelve a entrar.';
    }
  }
  return 'Ha ocurrido un error inesperado. Vuelve a intentarlo dentro de un rato.';
}
