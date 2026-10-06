import { bootstrapApplication } from '@angular/platform-browser';
import { App } from './app/app';
import { configuracionDeLaApp } from './app/app.config';
import { cargarConfiguracion } from './app/nucleo/configuracion';

cargarConfiguracion()
  .then((configuracion) => bootstrapApplication(App, configuracionDeLaApp(configuracion)))
  .catch((error: unknown) => {
    console.error(error);
    const raiz = document.querySelector('rl-root');
    if (raiz) {
      raiz.innerHTML =
        '<p class="carga-inicial">No se ha podido cargar el Radar de licitaciones. Vuelve a intentarlo dentro de un rato.</p>';
    }
  });
