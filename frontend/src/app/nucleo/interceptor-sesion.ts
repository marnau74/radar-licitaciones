import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { CONFIGURACION } from './configuracion';
import { Sesion } from './sesion';

/** Añade el token a las peticiones a la API, y solo a ellas: nunca se manda a otro servidor. */
export const interceptorDeSesion: HttpInterceptorFn = (peticion, siguiente) => {
  const api = inject(CONFIGURACION).api;
  const token = inject(Sesion).token();
  const esDeLaApi = peticion.url === api || peticion.url.startsWith(`${api}/`);
  if (!token || !esDeLaApi) {
    return siguiente(peticion);
  }
  return siguiente(peticion.clone({ setHeaders: { Authorization: `Bearer ${token}` } }));
};
