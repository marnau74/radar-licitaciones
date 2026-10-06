package es.radarlicitaciones.alertas;

import es.radarlicitaciones.compartido.Pagina;
import es.radarlicitaciones.licitaciones.BuscadorDeLicitaciones;
import es.radarlicitaciones.licitaciones.LicitacionResumen;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Los avisos del usuario de la sesión: lo que sus alertas han encontrado. */
@RestController
@RequestMapping("/api/v1/avisos")
@Tag(name = "Avisos", description = "Licitaciones encontradas por las alertas")
@SecurityRequirement(name = "keycloak")
@Transactional(readOnly = true)
class AvisosController {

    private final JdbcClient jdbc;
    private final BuscadorDeLicitaciones buscador;
    private final Clock reloj;

    AvisosController(JdbcClient jdbc, BuscadorDeLicitaciones buscador, Clock reloj) {
        this.jdbc = jdbc;
        this.buscador = buscador;
        this.reloj = reloj;
    }

    record Aviso(
            long id,
            long alertaId,
            String alerta,
            OffsetDateTime creadoEn,
            boolean leido,
            LicitacionResumen licitacion) {}

    record NoLeidos(long total) {}

    private record Fila(
            long id, long alertaId, String alerta, OffsetDateTime creadoEn, boolean leido, long licitacionId) {}

    @GetMapping
    @Operation(summary = "Mis avisos, los más recientes primero")
    Pagina<Aviso> mios(
            @AuthenticationPrincipal Jwt token,
            @RequestParam(defaultValue = "1") @Min(1) @Max(1000) int pagina,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamano,
            @RequestParam(defaultValue = "false") boolean soloNoLeidos) {
        var filas = jdbc.sql("""
                        SELECT v.id, v.alerta_id, a.nombre AS alerta, v.creado_en, v.leido_en IS NOT NULL AS leido,
                               v.licitacion_id
                        FROM aviso v JOIN alerta a ON a.id = v.alerta_id
                        WHERE a.usuario_id = :usuario AND (NOT :soloNoLeidos OR v.leido_en IS NULL)
                        ORDER BY v.creado_en DESC, v.id DESC
                        LIMIT :limite OFFSET :desplazamiento
                        """)
                .param("usuario", token.getSubject())
                .param("soloNoLeidos", soloNoLeidos)
                .param("limite", tamano)
                .param("desplazamiento", (pagina - 1) * tamano)
                .query(Fila.class)
                .list();
        var total = jdbc.sql("""
                        SELECT count(*) FROM aviso v JOIN alerta a ON a.id = v.alerta_id
                        WHERE a.usuario_id = :usuario AND (NOT :soloNoLeidos OR v.leido_en IS NULL)
                        """)
                .param("usuario", token.getSubject())
                .param("soloNoLeidos", soloNoLeidos)
                .query(Long.class)
                .single();
        var licitaciones = buscador
                .porIds(filas.stream().map(Fila::licitacionId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(LicitacionResumen::id, Function.identity()));
        List<Aviso> avisos = filas.stream()
                .filter(f -> licitaciones.containsKey(f.licitacionId()))
                .map(f -> new Aviso(
                        f.id(), f.alertaId(), f.alerta(), f.creadoEn(), f.leido(), licitaciones.get(f.licitacionId())))
                .toList();
        return new Pagina<>(avisos, pagina, tamano, total);
    }

    @GetMapping("/no-leidos")
    @Operation(summary = "Cuántos avisos tengo sin leer")
    NoLeidos noLeidos(@AuthenticationPrincipal Jwt token) {
        return new NoLeidos(
                jdbc.sql("""
                        SELECT count(*) FROM aviso v JOIN alerta a ON a.id = v.alerta_id
                        WHERE a.usuario_id = ? AND v.leido_en IS NULL
                        """).param(token.getSubject()).query(Long.class).single());
    }

    @PostMapping("/leidos")
    @Operation(summary = "Marca todos mis avisos como leídos")
    @Transactional
    ResponseEntity<Void> marcarLeidos(@AuthenticationPrincipal Jwt token) {
        jdbc.sql("""
                        UPDATE aviso v SET leido_en = ? FROM alerta a
                        WHERE a.id = v.alerta_id AND a.usuario_id = ? AND v.leido_en IS NULL
                        """).params(OffsetDateTime.now(reloj), token.getSubject()).update();
        return ResponseEntity.noContent().build();
    }
}
