import { ChangeDetectionStrategy, Component, computed, inject, input, output } from '@angular/core';
import { ServicioDeCatalogos } from '../nucleo/catalogos';
import { Codigo } from '../nucleo/modelo';

let siguienteId = 0;

/** Elige comunidades autónomas o provincias (códigos NUTS). Cada una elegida aparece como ficha que se puede quitar. */
@Component({
  selector: 'rl-selector-lugar',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="campo">
      <label [for]="id">{{ etiqueta() }}</label>
      @if (seleccionados().length) {
        <ul class="fichas" aria-label="Lugares elegidos">
          @for (codigo of seleccionados(); track codigo) {
            <li>
              <span class="ficha">
                {{ catalogos.nombre('nuts', codigo) }}
                <button
                  type="button"
                  (click)="quitar(codigo)"
                  [attr.aria-label]="'Quitar ' + catalogos.nombre('nuts', codigo)"
                >
                  ×
                </button>
              </span>
            </li>
          }
        </ul>
      }
      <select [id]="id" (change)="anadir($any($event.target))">
        <option value="">Añadir comunidad o provincia…</option>
        @for (comunidad of grupos(); track comunidad.comunidad.codigo) {
          <optgroup [label]="comunidad.comunidad.nombre">
            <option [value]="comunidad.comunidad.codigo">
              {{ comunidad.comunidad.nombre }} (toda)
            </option>
            @for (provincia of comunidad.provincias; track provincia.codigo) {
              <option [value]="provincia.codigo">{{ provincia.nombre }}</option>
            }
          </optgroup>
        }
      </select>
    </div>
  `,
  styles: `
    .fichas {
      display: flex;
      flex-wrap: wrap;
      gap: 0.35rem;
      margin: 0;
      padding: 0;
      list-style: none;
    }
    .ficha {
      display: inline-flex;
      align-items: center;
      gap: 0.3rem;
      padding: 0.15rem 0.2rem 0.15rem 0.6rem;
      border-radius: 999px;
      background: var(--acento-suave);
      font-size: 0.85rem;
    }
    .ficha button {
      border: 0;
      background: none;
      color: inherit;
      font-size: 1.1rem;
      line-height: 1;
      width: 24px;
      height: 24px;
      border-radius: 999px;
      cursor: pointer;
    }
  `,
})
export class SelectorLugar {
  readonly seleccionados = input<string[]>([]);
  readonly etiqueta = input('Lugar de ejecución');
  readonly cambio = output<string[]>();

  protected readonly id = `lugar-${siguienteId++}`;
  protected readonly catalogos = inject(ServicioDeCatalogos);

  /** Cada comunidad (NUTS 2) con sus provincias (NUTS 3), que empiezan por su código. */
  protected readonly grupos = computed(() => {
    const datos = this.catalogos.catalogos.hasValue() ? this.catalogos.catalogos.value() : null;
    if (!datos) {
      return [];
    }
    return datos.comunidades.map((comunidad: Codigo) => ({
      comunidad,
      provincias: datos.provincias.filter((p) => p.codigo.startsWith(comunidad.codigo)),
    }));
  });

  protected anadir(select: HTMLSelectElement): void {
    const codigo = select.value;
    select.value = '';
    if (codigo && !this.seleccionados().includes(codigo)) {
      this.cambio.emit([...this.seleccionados(), codigo]);
    }
  }

  protected quitar(codigo: string): void {
    this.cambio.emit(this.seleccionados().filter((c) => c !== codigo));
  }
}
