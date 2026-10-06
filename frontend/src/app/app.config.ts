import { registerLocaleData } from '@angular/common';
import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import localeEs from '@angular/common/locales/es';
import {
  ApplicationConfig,
  LOCALE_ID,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideRouter, withComponentInputBinding, withInMemoryScrolling } from '@angular/router';
import { provideOAuthClient } from 'angular-oauth2-oidc';
import { rutas } from './app.routes';
import { CONFIGURACION, Configuracion } from './nucleo/configuracion';
import { interceptorDeSesion } from './nucleo/interceptor-sesion';
import { Sesion } from './nucleo/sesion';

registerLocaleData(localeEs);

export function configuracionDeLaApp(configuracion: Configuracion): ApplicationConfig {
  return {
    providers: [
      provideBrowserGlobalErrorListeners(),
      { provide: CONFIGURACION, useValue: configuracion },
      { provide: LOCALE_ID, useValue: 'es' },
      provideRouter(
        rutas,
        withComponentInputBinding(),
        withInMemoryScrolling({ scrollPositionRestoration: 'enabled', anchorScrolling: 'enabled' }),
      ),
      provideHttpClient(withFetch(), withInterceptors([interceptorDeSesion])),
      provideOAuthClient(),
      provideAppInitializer(() => inject(Sesion).iniciar()),
    ],
  };
}
