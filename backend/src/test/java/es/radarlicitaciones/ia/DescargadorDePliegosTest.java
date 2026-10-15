package es.radarlicitaciones.ia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.ErrorResponseException;

/** Solo se descargan pliegos de la Plataforma, por HTTPS: las direcciones vienen de fuera. */
class DescargadorDePliegosTest {

    private final DescargadorDePliegos descargador = new DescargadorDePliegos(new PropiedadesDeIa(
            new PropiedadesDeIa.Resumenes(PropiedadesDeIa.ProveedorDeResumenes.NINGUNO, 10),
            new PropiedadesDeIa.Embeddings(PropiedadesDeIa.ProveedorDeEmbeddings.NINGUNO, 1024, 32),
            null,
            null,
            new PropiedadesDeIa.Pliegos(
                    List.of("contrataciondelestado.es", "contrataciondelsectorpublico.gob.es"),
                    25,
                    120,
                    Duration.ofSeconds(5))));

    @ParameterizedTest
    @ValueSource(
            strings = {
                "https://contrataciondelestado.es/FileSystem/servlet/GetDocumentByIdServlet?DocumentIdParam=abc",
                "https://www.contrataciondelsectorpublico.gob.es/doc.pdf"
            })
    void aceptaLaPlataformaPorHttps(String url) {
        assertThat(descargador.permitida(url).getHost()).contains("contrata");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "http://contrataciondelestado.es/doc.pdf", // sin HTTPS
                "https://contrataciondelestado.es.atacante.example/doc.pdf", // parece, pero no es
                "https://atacante.example/?u=contrataciondelestado.es",
                "https://usuario@contrataciondelestado.es/doc.pdf", // credenciales en la URL
                "https://localhost/doc.pdf",
                "https://169.254.169.254/latest/meta-data/", // metadatos de la nube
                "file:///etc/passwd",
                "no es una url"
            })
    void rechazaCualquierOtraDireccion(String url) {
        assertThatThrownBy(() -> descargador.permitida(url)).isInstanceOf(ErrorResponseException.class);
    }

    @Test
    void laListaDeDominiosNoAceptaSufijosSueltos() {
        // «estado.es» no es un subdominio de «contrataciondelestado.es»
        assertThatThrownBy(() -> descargador.permitida("https://estado.es/doc.pdf"))
                .isInstanceOf(ErrorResponseException.class);
    }
}
