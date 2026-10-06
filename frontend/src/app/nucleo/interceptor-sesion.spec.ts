import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { CONFIGURACION } from './configuracion';
import { interceptorDeSesion } from './interceptor-sesion';
import { Sesion } from './sesion';

describe('interceptorDeSesion', () => {
  let token: string | null;
  let http: HttpClient;
  let servidor: HttpTestingController;

  beforeEach(() => {
    token = 'token-de-prueba';
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([interceptorDeSesion])),
        provideHttpClientTesting(),
        { provide: CONFIGURACION, useValue: { api: '/api', oidc: { emisor: 'x', cliente: 'y' } } },
        { provide: Sesion, useValue: { token: () => token } },
      ],
    });
    http = TestBed.inject(HttpClient);
    servidor = TestBed.inject(HttpTestingController);
  });

  afterEach(() => servidor.verify());

  it('añade el token a las peticiones a la API', () => {
    http.get('/api/v1/alertas').subscribe();
    expect(servidor.expectOne('/api/v1/alertas').request.headers.get('Authorization')).toBe(
      'Bearer token-de-prueba',
    );
  });

  it('nunca lo manda a otro servidor ni a rutas que solo empiezan igual', () => {
    http.get('https://otro.example/api/v1/alertas').subscribe();
    http.get('/apiextra/datos').subscribe();
    expect(
      servidor
        .expectOne('https://otro.example/api/v1/alertas')
        .request.headers.has('Authorization'),
    ).toBe(false);
    expect(servidor.expectOne('/apiextra/datos').request.headers.has('Authorization')).toBe(false);
  });

  it('sin sesión no añade nada', () => {
    token = null;
    http.get('/api/v1/licitaciones').subscribe();
    expect(servidor.expectOne('/api/v1/licitaciones').request.headers.has('Authorization')).toBe(
      false,
    );
  });
});
