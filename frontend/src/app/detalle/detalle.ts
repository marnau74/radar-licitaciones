import { HttpErrorResponse, httpResource } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, effect, inject, input } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { RouterLink } from '@angular/router';
import { mensajeDeError } from '../nucleo/api';
import { CONFIGURACION } from '../nucleo/configuracion';
import { EurosPipe, FechaPipe, etiquetaDeEstado, plazoRestante } from '../nucleo/formato';
import { DetalleDeLicitacion } from '../nucleo/modelo';

const TIPOS_DE_DOCUMENTO = {
  PLIEGO_ADMINISTRATIVO: 'Pliego de cláusulas administrativas',
  PLIEGO_TECNICO: 'Pliego de prescripciones técnicas',
  OTRO: 'Otro documento',
} as const;

/** La ficha de una licitación. */
@Component({
  selector: 'rl-detalle',
  imports: [RouterLink, EurosPipe, FechaPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './detalle.html',
  styleUrl: './detalle.css',
})
export class Detalle {
  /** Del parámetro de la ruta. */
  readonly id = input.required<string>();

  private readonly api = inject(CONFIGURACION).api;
  protected readonly tiposDeDocumento = TIPOS_DE_DOCUMENTO;

  protected readonly licitacion = httpResource<DetalleDeLicitacion>(() =>
    /^\d{1,18}$/.test(this.id()) ? `${this.api}/v1/licitaciones/${this.id()}` : undefined,
  );
  protected readonly noExiste = computed(() => {
    const error = this.licitacion.error();
    return (
      !/^\d{1,18}$/.test(this.id()) || (error instanceof HttpErrorResponse && error.status === 404)
    );
  });
  protected readonly error = computed(() =>
    this.licitacion.error() && !this.noExiste() ? mensajeDeError(this.licitacion.error()) : null,
  );
  protected readonly restante = computed(() =>
    this.licitacion.hasValue() ? plazoRestante(this.licitacion.value().plazoPresentacion) : '',
  );
  protected readonly etiqueta = computed(() => {
    if (!this.licitacion.hasValue()) {
      return { texto: '', clase: '' };
    }
    const l = this.licitacion.value();
    return etiquetaDeEstado(l.estado.codigo, l.estado.nombre, l.plazoPresentacion, l.anulada);
  });
  /** Los resultados, con el objeto del lote al que se refieren. */
  protected readonly resultados = computed(() => {
    if (!this.licitacion.hasValue()) {
      return [];
    }
    const l = this.licitacion.value();
    const lotes = new Map(l.lotes.map((lote) => [lote.numero, lote.objeto]));
    return l.resultados.map((r) => ({
      ...r,
      objetoDelLote: r.lote ? (lotes.get(r.lote) ?? null) : null,
    }));
  });

  constructor() {
    const titulo = inject(Title);
    effect(() => {
      if (this.licitacion.hasValue()) {
        titulo.setTitle(`${this.licitacion.value().titulo} · Radar de licitaciones`);
      }
    });
  }
}
