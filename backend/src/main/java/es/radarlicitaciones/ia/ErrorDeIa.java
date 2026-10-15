package es.radarlicitaciones.ia;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** Errores de la IA con su respuesta HTTP (RFC 9457), que Spring convierte en {@code application/problem+json}. */
class ErrorDeIa extends ErrorResponseException {

    private static final long serialVersionUID = 1L;

    private ErrorDeIa(HttpStatus estado, String detalle, Throwable causa) {
        super(estado, ProblemDetail.forStatusAndDetail(estado, detalle), causa);
    }

    /** El servidor no tiene configurada esta función (503). */
    static ErrorDeIa desactivada(String funcion) {
        return new ErrorDeIa(HttpStatus.SERVICE_UNAVAILABLE, funcion + " no está activada en este servidor", null);
    }

    /** El usuario ha pedido hoy todos los resúmenes nuevos que puede (429). */
    static ErrorDeIa limiteDiario(int limite) {
        return new ErrorDeIa(
                HttpStatus.TOO_MANY_REQUESTS,
                "Has pedido hoy los " + limite + " resúmenes nuevos que se permiten por día. Los ya hechos se pueden"
                        + " seguir consultando.",
                null);
    }

    /** El pliego no se puede leer o no es un PDF (422). */
    static ErrorDeIa pliegoNoValido(String detalle) {
        return new ErrorDeIa(HttpStatus.UNPROCESSABLE_CONTENT, detalle, null);
    }

    /** Falla el servicio de fuera (la Plataforma, Anthropic, Ollama): se puede volver a intentar (502). */
    static ErrorDeIa servicioExterno(String detalle, Throwable causa) {
        return new ErrorDeIa(HttpStatus.BAD_GATEWAY, detalle, causa);
    }
}
