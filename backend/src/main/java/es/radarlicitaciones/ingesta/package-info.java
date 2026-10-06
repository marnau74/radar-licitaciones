/**
 * Ingesta diaria de la Plataforma de Contratación con Spring Batch: lee el feed (o un paquete histórico), traduce
 * CODICE y entrega las versiones al módulo de licitaciones. Al terminar publica {@link
 * es.radarlicitaciones.ingesta.IngestaCompletada}, que el módulo de alertas escucha para avisar.
 */
@ApplicationModule(displayName = "Ingesta")
package es.radarlicitaciones.ingesta;

import org.springframework.modulith.ApplicationModule;
