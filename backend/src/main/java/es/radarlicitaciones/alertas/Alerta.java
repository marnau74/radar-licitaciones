package es.radarlicitaciones.alertas;

import es.radarlicitaciones.licitaciones.FiltroDeBusqueda;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Una búsqueda guardada por un usuario. */
@Entity
@Table(name = "alerta")
class Alerta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private String usuarioId;

    @Column(nullable = false)
    private String correo;

    @Column(nullable = false)
    private String nombre;

    private String texto;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "tipos_contrato", nullable = false)
    private List<String> tiposContrato = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false)
    private List<String> procedimientos = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false)
    private List<String> nuts = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false)
    private List<String> cpv = new ArrayList<>();

    @Column(name = "importe_minimo")
    private BigDecimal importeMinimo;

    @Column(name = "importe_maximo")
    private BigDecimal importeMaximo;

    @Column(name = "solo_fondos_ue", nullable = false)
    private boolean soloFondosUe;

    @Column(name = "por_correo", nullable = false)
    private boolean porCorreo;

    @Column(nullable = false)
    private boolean activa;

    @Column(name = "creada_en", nullable = false, updatable = false)
    private OffsetDateTime creadaEn;

    @Column(name = "actualizada_en", nullable = false)
    private OffsetDateTime actualizadaEn;

    @Version
    private int version;

    protected Alerta() {}

    Alerta(String usuarioId, String correo, DatosDeAlerta datos, OffsetDateTime ahora) {
        this.usuarioId = usuarioId;
        this.creadaEn = ahora;
        actualizar(correo, datos, ahora);
    }

    final void actualizar(String correo, DatosDeAlerta datos, OffsetDateTime ahora) {
        this.correo = correo;
        this.nombre = datos.nombre().strip();
        this.texto = datos.texto() == null || datos.texto().isBlank()
                ? null
                : datos.texto().strip();
        this.tiposContrato = lista(datos.tiposContrato());
        this.procedimientos = lista(datos.procedimientos());
        this.nuts = lista(datos.nuts());
        this.cpv = lista(datos.cpv());
        this.importeMinimo = datos.importeMinimo();
        this.importeMaximo = datos.importeMaximo();
        this.soloFondosUe = Boolean.TRUE.equals(datos.soloFondosUe());
        this.porCorreo = datos.porCorreo() == null || datos.porCorreo();
        this.activa = datos.activa() == null || datos.activa();
        this.actualizadaEn = ahora;
    }

    private static List<String> lista(List<String> valores) {
        return valores == null
                ? new ArrayList<>()
                : new ArrayList<>(valores.stream().distinct().toList());
    }

    /** El filtro de búsqueda equivalente (sin estados: de una alerta solo se avisa de lo que está en plazo). */
    FiltroDeBusqueda filtro() {
        return new FiltroDeBusqueda(
                texto,
                List.of(),
                tiposContrato,
                procedimientos,
                nuts,
                cpv,
                importeMinimo,
                importeMaximo,
                null,
                null,
                null,
                null,
                null,
                true,
                soloFondosUe,
                false);
    }

    /**
     * No se avisa de licitaciones publicadas antes de crear la alerta, ni de hace más de una semana: una alerta nueva
     * no debe llegar con un correo de cientos de licitaciones antiguas.
     */
    LocalDate publicadasDesde(LocalDate hoy, ZoneId zona) {
        var creada = creadaEn.atZoneSameInstant(zona).toLocalDate();
        var haceUnaSemana = hoy.minusDays(7);
        return creada.isAfter(haceUnaSemana) ? creada : haceUnaSemana;
    }

    Long getId() {
        return id;
    }

    String getUsuarioId() {
        return usuarioId;
    }

    String getCorreo() {
        return correo;
    }

    String getNombre() {
        return nombre;
    }

    String getTexto() {
        return texto;
    }

    List<String> getTiposContrato() {
        return List.copyOf(tiposContrato);
    }

    List<String> getProcedimientos() {
        return List.copyOf(procedimientos);
    }

    List<String> getNuts() {
        return List.copyOf(nuts);
    }

    List<String> getCpv() {
        return List.copyOf(cpv);
    }

    BigDecimal getImporteMinimo() {
        return importeMinimo;
    }

    BigDecimal getImporteMaximo() {
        return importeMaximo;
    }

    boolean isSoloFondosUe() {
        return soloFondosUe;
    }

    boolean isPorCorreo() {
        return porCorreo;
    }

    boolean isActiva() {
        return activa;
    }

    OffsetDateTime getCreadaEn() {
        return creadaEn;
    }

    OffsetDateTime getActualizadaEn() {
        return actualizadaEn;
    }

    int getVersion() {
        return version;
    }
}
