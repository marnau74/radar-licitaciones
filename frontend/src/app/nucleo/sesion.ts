import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { OAuthService } from 'angular-oauth2-oidc';
import { CONFIGURACION } from './configuracion';

export interface UsuarioDeSesion {
  nombre: string;
  correo: string | null;
  administrador: boolean;
}

/**
 * La sesión del usuario con Keycloak: flujo de código con PKCE (el estándar para una SPA, sin secretos en el
 * navegador) y renovación del token con el refresh token. La búsqueda no la necesita: solo las alertas y los avisos.
 */
@Injectable({ providedIn: 'root' })
export class Sesion {
  private readonly oauth = inject(OAuthService);
  private readonly router = inject(Router);
  private readonly configuracion = inject(CONFIGURACION);

  private readonly claims = signal<Record<string, unknown> | null>(null);
  private readonly disponible = signal(true);

  /** Si Keycloak respondió al arrancar; si no, se puede buscar pero no iniciar sesión. */
  readonly servicioDisponible = this.disponible.asReadonly();
  readonly iniciada = computed(() => this.claims() !== null);
  readonly usuario = computed<UsuarioDeSesion | null>(() => {
    const claims = this.claims();
    if (!claims) {
      return null;
    }
    const roles = Array.isArray(claims['roles']) ? (claims['roles'] as string[]) : [];
    return {
      nombre: String(claims['name'] ?? claims['preferred_username'] ?? claims['email'] ?? ''),
      correo: typeof claims['email'] === 'string' ? claims['email'] : null,
      administrador: roles.includes('admin'),
    };
  });

  /** Se llama una vez al arrancar: completa el inicio de sesión si se vuelve de Keycloak. */
  async iniciar(): Promise<void> {
    this.oauth.configure({
      issuer: this.configuracion.oidc.emisor,
      clientId: this.configuracion.oidc.cliente,
      redirectUri: `${window.location.origin}/`,
      postLogoutRedirectUri: `${window.location.origin}/`,
      responseType: 'code',
      scope: 'openid profile email',
      // HTTP solo se admite en localhost (desarrollo).
      requireHttps: 'remoteOnly',
      showDebugInformation: false,
      clearHashAfterLogin: true,
    });
    try {
      await this.oauth.loadDiscoveryDocumentAndTryLogin();
    } catch {
      this.disponible.set(false);
      return;
    }
    if (this.oauth.hasValidAccessToken()) {
      this.oauth.setupAutomaticSilentRefresh();
      this.actualizar();
      // Se vuelve a la página desde la que se pidió entrar. Esto corre antes de la primera navegación del router:
      // basta con dejar esa dirección en la barra y el router irá a ella (navegar ahora lo pisaría la inicial).
      const destino = destinoSeguro(this.oauth.state);
      if (destino) {
        window.history.replaceState(window.history.state, '', destino);
      }
    }
    quitarParametrosDeVuelta();
    this.oauth.events.subscribe((evento) => {
      if (
        [
          'token_received',
          'token_refreshed',
          'logout',
          'token_expires',
          'session_terminated',
        ].includes(evento.type)
      ) {
        this.actualizar();
      }
      if (evento.type === 'token_refresh_error' || evento.type === 'session_terminated') {
        this.oauth.logOut(true);
        this.actualizar();
      }
    });
  }

  /** Lleva a Keycloak; al volver, se sigue en `destino`. */
  entrar(destino: string = this.router.url): void {
    // La librería ya codifica el estado: aquí va tal cual.
    this.oauth.initCodeFlow(destino);
  }

  salir(): void {
    this.oauth.logOut();
    this.claims.set(null);
  }

  token(): string | null {
    return this.oauth.hasValidAccessToken() ? this.oauth.getAccessToken() : null;
  }

  private actualizar(): void {
    if (!this.oauth.hasValidAccessToken()) {
      this.claims.set(null);
      return;
    }
    // Los roles vienen en el token de acceso (claim «roles»), no en el de identidad.
    const acceso = decodificar(this.oauth.getAccessToken());
    this.claims.set({ ...(this.oauth.getIdentityClaims() ?? {}), roles: acceso['roles'] ?? [] });
  }
}

/** Solo direcciones de esta misma web («/algo», nunca «//otro.sitio» ni «https://...»): no es un redirector abierto. */
export function destinoSeguro(estado: string | undefined | null): string | null {
  if (!estado) {
    return null;
  }
  let destino = estado;
  // El estado puede volver codificado más de una vez (la librería y la URL lo codifican).
  for (let i = 0; i < 3 && /%[0-9A-Fa-f]{2}/.test(destino); i++) {
    try {
      destino = decodeURIComponent(destino);
    } catch {
      return null;
    }
  }
  const propia = destino.startsWith('/') && !destino.startsWith('//') && !destino.startsWith('/\\');
  return propia ? destino : null;
}

/** Keycloak añade «iss» y «session_state» a la vuelta (RFC 9207): no pintan nada en la barra de direcciones. */
function quitarParametrosDeVuelta(): void {
  const url = new URL(window.location.href);
  let cambiada = false;
  for (const parametro of ['iss', 'session_state', 'code', 'state']) {
    if (url.searchParams.has(parametro)) {
      url.searchParams.delete(parametro);
      cambiada = true;
    }
  }
  if (cambiada) {
    window.history.replaceState(window.history.state, '', url.pathname + url.search + url.hash);
  }
}

function decodificar(jwt: string): Record<string, unknown> {
  try {
    const cuerpo = jwt.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    const texto = new TextDecoder().decode(Uint8Array.from(atob(cuerpo), (c) => c.charCodeAt(0)));
    return JSON.parse(texto) as Record<string, unknown>;
  } catch {
    return {};
  }
}
