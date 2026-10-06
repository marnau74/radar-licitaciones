package es.radarlicitaciones.compartido;

import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Todos los errores de la API salen en formato RFC 9457 ({@code application/problem+json}), con los campos no válidos en
 * {@code errores} cuando los hay. Nunca se devuelve la traza ni el mensaje de una excepción inesperada.
 */
@RestControllerAdvice
class ManejadorDeErrores extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ManejadorDeErrores.class);

    @ExceptionHandler(RecursoNoEncontrado.class)
    ProblemDetail noEncontrado(RecursoNoEncontrado e) {
        return problema(HttpStatus.NOT_FOUND, "No encontrado", e.getMessage());
    }

    @ExceptionHandler(PeticionNoValida.class)
    ProblemDetail noValida(PeticionNoValida e) {
        return problema(HttpStatus.BAD_REQUEST, "Petición no válida", e.getMessage());
    }

    @ExceptionHandler(Conflicto.class)
    ProblemDetail conflicto(Conflicto e) {
        return problema(HttpStatus.CONFLICT, "Conflicto", e.getMessage());
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ProblemDetail edicionSimultanea() {
        return problema(
                HttpStatus.CONFLICT,
                "Conflicto",
                "Se ha modificado a la vez desde otro sitio. Vuelve a cargar y repite el cambio.");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail inesperado(Exception e) throws Exception {
        if (e instanceof AccessDeniedException || e instanceof AuthenticationException) {
            throw e; // Las contesta Spring Security (401 y 403).
        }
        log.error("Error no controlado", e);
        return problema(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno", "Ha ocurrido un error inesperado.");
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var errores = ex.getBindingResult().getFieldErrors().stream()
                .map(ManejadorDeErrores::error)
                .toList();
        return ResponseEntity.badRequest().body(conErrores(errores));
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var errores = ex.getParameterValidationResults().stream()
                .flatMap(r -> r.getResolvableErrors().stream()
                        .map(e -> e instanceof FieldError campo
                                ? error(campo)
                                : Map.of(
                                        "campo",
                                        r.getMethodParameter().getParameterName() == null
                                                ? ""
                                                : r.getMethodParameter().getParameterName(),
                                        "mensaje",
                                        String.valueOf(e.getDefaultMessage()))))
                .toList();
        return ResponseEntity.badRequest().body(conErrores(errores));
    }

    private static Map<String, String> error(FieldError campo) {
        return Map.of("campo", campo.getField(), "mensaje", String.valueOf(campo.getDefaultMessage()));
    }

    private static ProblemDetail conErrores(List<Map<String, String>> errores) {
        var problema = problema(HttpStatus.BAD_REQUEST, "Petición no válida", "Hay campos con valores no válidos.");
        problema.setProperty("errores", errores);
        return problema;
    }

    private static ProblemDetail problema(HttpStatus estado, String titulo, String detalle) {
        var problema = ProblemDetail.forStatusAndDetail(estado, detalle);
        problema.setTitle(titulo);
        return problema;
    }
}
