import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'rl-no-encontrada',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="contenedor">
      <div class="panel aviso-vacio">
        <h1>Esta página no existe</h1>
        <p>Puede que el enlace esté mal escrito o que la página ya no esté.</p>
        <a class="boton principal" routerLink="/">Ir a la búsqueda</a>
      </div>
    </div>
  `,
})
export class NoEncontrada {}
