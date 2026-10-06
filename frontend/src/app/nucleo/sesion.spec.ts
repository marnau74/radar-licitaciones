import { destinoSeguro } from './sesion';

describe('destinoSeguro', () => {
  it('acepta rutas de la propia web aunque vuelvan codificadas', () => {
    expect(destinoSeguro('/alertas')).toBe('/alertas');
    expect(destinoSeguro('%2Falertas')).toBe('/alertas');
    expect(destinoSeguro('%252Falertas%253Fq%253Dx')).toBe('/alertas?q=x');
  });

  it('rechaza cualquier otro destino', () => {
    expect(destinoSeguro(null)).toBeNull();
    expect(destinoSeguro('https://otro.example/')).toBeNull();
    expect(destinoSeguro('//otro.example/')).toBeNull();
    expect(destinoSeguro('%2F%2Fotro.example')).toBeNull();
    expect(destinoSeguro('/\\otro.example')).toBeNull();
    expect(destinoSeguro('%E0%A4%A')).toBeNull();
  });
});
