import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Sesion } from './sesion';

/**
 * Las páginas privadas: si no hay sesión, se va a Keycloak y se vuelve a la página pedida. Si Keycloak no responde, se
 * queda en la portada (la cabecera avisa de que el inicio de sesión no está disponible).
 */
export const guardaDeSesion: CanActivateFn = (_ruta, estado) => {
  const sesion = inject(Sesion);
  if (sesion.iniciada()) {
    return true;
  }
  if (!sesion.servicioDisponible()) {
    return inject(Router).parseUrl('/');
  }
  sesion.entrar(estado.url);
  return false;
};
