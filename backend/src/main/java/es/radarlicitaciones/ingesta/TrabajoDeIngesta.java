package es.radarlicitaciones.ingesta;

import es.radarlicitaciones.ingesta.codice.ElementoDelFeed;
import es.radarlicitaciones.licitaciones.RegistroDeLicitaciones;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * El job de Spring Batch: un único paso por lotes que lee la fuente y escribe en el módulo de licitaciones.
 *
 * <p>Lotes de 500 elementos, lo que trae una página del feed: cada lote es una transacción con media docena de
 * sentencias SQL.
 */
@Configuration(proxyBeanMethods = false)
class TrabajoDeIngesta {

    static final String JOB = "ingestaLicitaciones";
    static final String PASO = "leerYGuardar";
    static final String PARAMETRO_ORIGEN = "origen";
    static final String PARAMETRO_VALOR = "valor";
    static final String PARAMETRO_EJECUCION = "ejecucion";
    static final String PARAMETRO_LANZADA = "lanzadaEn";
    static final int TAMANO_LOTE = 500;

    @Bean
    Job ingestaLicitaciones(JobRepository repositorio, Step leerYGuardar, FinDeIngesta fin) {
        return new JobBuilder(JOB, repositorio)
                .start(leerYGuardar)
                .listener(fin)
                .build();
    }

    @Bean
    Step leerYGuardar(
            JobRepository repositorio,
            PlatformTransactionManager transacciones,
            LectorDeFuente lectorDeFuente,
            EscritorDeElementos escritorDeElementos) {
        return new StepBuilder(PASO, repositorio)
                .<ElementoDelFeed, ElementoDelFeed>chunk(TAMANO_LOTE)
                .transactionManager(transacciones)
                .reader(lectorDeFuente)
                .writer(escritorDeElementos)
                .listener(escritorDeElementos)
                .build();
    }

    @Bean
    @StepScope
    LectorDeFuente lectorDeFuente(
            FabricaDeFuentes fabrica,
            @Value("#{jobParameters['" + PARAMETRO_ORIGEN + "']}") String origen,
            @Value("#{jobParameters['" + PARAMETRO_VALOR + "']}") String valor) {
        return new LectorDeFuente(fabrica.crear(origen, valor));
    }

    @Bean
    @StepScope
    EscritorDeElementos escritorDeElementos(RegistroDeLicitaciones registro) {
        return new EscritorDeElementos(registro);
    }
}
