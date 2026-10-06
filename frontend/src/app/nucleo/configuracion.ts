import { InjectionToken } from '@angular/core';

/**
 * Configuración que se lee al arrancar de `config.json`: la misma imagen de la web sirve para cualquier entorno, solo
 * cambia ese fichero (en producción lo genera el contenedor a partir de variables de entorno).
 */
export interface Configuracion {
  /** Prefijo de la API: `/api` (mismo origen que la web). */
  api: string;
  oidc: {
    /** URL del realm de Keycloak. */
    emisor: string;
    cliente: string;
  };
}

export const CONFIGURACION = new InjectionToken<Configuracion>('configuracion');

export async function cargarConfiguracion(url = 'config.json'): Promise<Configuracion> {
  const respuesta = await fetch(url, { cache: 'no-cache' });
  if (!respuesta.ok) {
    throw new Error(`No se ha podido leer ${url} (HTTP ${respuesta.status})`);
  }
  const configuracion = (await respuesta.json()) as Configuracion;
  if (!configuracion.api || !configuracion.oidc?.emisor || !configuracion.oidc?.cliente) {
    throw new Error(`${url} no tiene api, oidc.emisor y oidc.cliente`);
  }
  return configuracion;
}
