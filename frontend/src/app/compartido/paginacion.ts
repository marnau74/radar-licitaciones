import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';

/** Anterior / siguiente con la página actual. Sin saltos a páginas lejanas: si hay muchas, mejor afinar el filtro. */
@Component({
  selector: 'rl-paginacion',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (paginas() > 1) {
      <nav class="paginacion" aria-label="Páginas de resultados">
        <button
          type="button"
          class="boton"
          [disabled]="pagina() <= 1"
          (click)="cambiar.emit(pagina() - 1)"
        >
          ← Anterior
        </button>
        <span class="suave" aria-current="page"
          >Página {{ pagina() }} de {{ paginasMostradas() }}</span
        >
        <button
          type="button"
          class="boton"
          [disabled]="pagina() >= paginasMostradas()"
          (click)="cambiar.emit(pagina() + 1)"
        >
          Siguiente →
        </button>
      </nav>
    }
  `,
  styles: `
    .paginacion {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 1rem;
      margin-top: 1.5rem;
      flex-wrap: wrap;
    }
  `,
})
export class Paginacion {
  readonly pagina = input.required<number>();
  readonly paginas = input.required<number>();
  /** La API solo deja recorrer los primeros resultados. */
  readonly maximo = input(500);
  readonly cambiar = output<number>();
  protected readonly paginasMostradas = computed(() => Math.min(this.paginas(), this.maximo()));
}
