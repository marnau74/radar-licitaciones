package es.radarlicitaciones;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Los módulos solo se usan entre sí por su interfaz pública y sin ciclos. Si alguien usa una clase interna de otro
 * módulo, esta prueba falla. De paso genera los diagramas de módulos en {@code target/spring-modulith-docs}.
 */
class ModulosTest {

    private final ApplicationModules modulos = ApplicationModules.of(RadarApiApplication.class);

    @Test
    void losModulosRespetanSusLimites() {
        modulos.verify();
    }

    @Test
    void generaLaDocumentacionDeLosModulos() {
        new Documenter(modulos)
                .writeModulesAsPlantUml()
                .writeIndividualModulesAsPlantUml()
                .writeModuleCanvases();
    }
}
