/**
 * IA aplicada a las licitaciones, desactivada por defecto:
 *
 * <ul>
 *   <li><b>Resumen de pliegos</b>: lee el PDF de un pliego y devuelve lo que importa para decidir si presentarse
 *       (objeto, solvencia, criterios de adjudicación, garantías y puntos de atención). Con Claude (API de Anthropic)
 *       o con un modelo local en Ollama. Cada resumen se guarda: un pliego se resume una sola vez.
 *   <li><b>Búsqueda por significado</b>: cada licitación se convierte en un vector (embeddings locales con Ollama) y
 *       se busca por cercanía con pgvector, así que «limpieza de colegios» encuentra también «servicio de higiene en
 *       centros educativos».
 * </ul>
 *
 * <p>Interfaz del módulo: {@link es.radarlicitaciones.ia.ResumenDePliego} y los endpoints de {@code IaController}.
 */
@ApplicationModule(displayName = "IA")
package es.radarlicitaciones.ia;

import org.springframework.modulith.ApplicationModule;
