package es.radarlicitaciones.ia;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/**
 * Lo que importa de un pliego para decidir si presentarse. Es también el esquema de la salida estructurada que se pide
 * al modelo: las descripciones de los campos son instrucciones para él.
 */
public record ResumenDePliego(
        @JsonPropertyDescription("Qué se contrata, en una o dos frases claras.")
        String objeto,

        @JsonPropertyDescription("Presupuesto base de licitación y valor estimado, con y sin IVA si constan.")
        String presupuesto,

        @JsonPropertyDescription("Duración del contrato y prórrogas posibles.")
        String duracion,

        @JsonPropertyDescription("Requisitos de solvencia económica y financiera, uno por elemento, con cifras.")
        List<String> solvenciaEconomica,

        @JsonPropertyDescription(
                "Requisitos de solvencia técnica o profesional y clasificación exigida, uno por elemento.")
        List<String> solvenciaTecnica,

        @JsonPropertyDescription("Criterios de adjudicación con su peso, en el orden del pliego.")
        List<Criterio> criteriosDeAdjudicacion,

        @JsonPropertyDescription("Garantías provisional y definitiva, con importe o porcentaje.")
        List<String> garantias,

        @JsonPropertyDescription(
                "Lo que puede descalificar o encarecer una oferta: penalidades, subcontratación, subrogación de personal, plazos cortos, umbrales de baja temeraria...")
        List<String> puntosDeAtencion) {

    public ResumenDePliego {
        solvenciaEconomica = solvenciaEconomica == null ? List.of() : List.copyOf(solvenciaEconomica);
        solvenciaTecnica = solvenciaTecnica == null ? List.of() : List.copyOf(solvenciaTecnica);
        criteriosDeAdjudicacion = criteriosDeAdjudicacion == null ? List.of() : List.copyOf(criteriosDeAdjudicacion);
        garantias = garantias == null ? List.of() : List.copyOf(garantias);
        puntosDeAtencion = puntosDeAtencion == null ? List.of() : List.copyOf(puntosDeAtencion);
    }

    public record Criterio(
            @JsonPropertyDescription("El criterio, tal como lo llama el pliego.")
            String criterio,

            @JsonPropertyDescription("Su peso: puntos o porcentaje, tal como aparece.")
            String peso,

            @JsonPropertyDescription(
                    "true si se valora con fórmula (precio, plazos...); false si es un juicio de valor.")
            boolean automatico) {}
}
