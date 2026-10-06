package es.radarlicitaciones.alertas;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface AlertaRepositorio extends JpaRepository<Alerta, Long> {

    List<Alerta> findByUsuarioIdOrderByCreadaEnDesc(String usuarioId);

    Optional<Alerta> findByIdAndUsuarioId(Long id, String usuarioId);

    long countByUsuarioId(String usuarioId);

    List<Alerta> findByActivaTrueOrderById();
}
