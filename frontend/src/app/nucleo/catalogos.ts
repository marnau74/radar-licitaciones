import { httpResource } from '@angular/common/http';
import { Injectable, computed, inject } from '@angular/core';
import { CONFIGURACION } from './configuracion';
import { Catalogos, Codigo } from './modelo';

/**
 * Los catálogos de códigos (estados, tipos, procedimientos, comunidades, provincias), pedidos una vez y compartidos.
 * La API los sirve con caché de un día.
 */
@Injectable({ providedIn: 'root' })
export class ServicioDeCatalogos {
  private readonly api = inject(CONFIGURACION).api;

  readonly catalogos = httpResource<Catalogos>(() => `${this.api}/v1/catalogos`);

  private readonly nombres = computed(() => {
    const c = this.catalogos.hasValue() ? this.catalogos.value() : null;
    const mapa = (codigos: Codigo[] = []) => new Map(codigos.map((x) => [x.codigo, x.nombre]));
    return {
      estado: mapa(c?.estados),
      tipo: mapa(c?.tiposContrato),
      procedimiento: mapa(c?.procedimientos),
      nuts: mapa([...(c?.comunidades ?? []), ...(c?.provincias ?? [])]),
    };
  });

  /** El nombre de un código; si aún no han llegado los catálogos o no está, el propio código. */
  nombre(
    catalogo: 'estado' | 'tipo' | 'procedimiento' | 'nuts',
    codigo: string | null | undefined,
  ): string {
    if (!codigo) {
      return '';
    }
    return this.nombres()[catalogo].get(codigo) ?? codigo;
  }
}
