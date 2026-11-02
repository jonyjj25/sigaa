package com.sigaa.repository;

import com.sigaa.model.NotaHistorial;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotaHistorialRepository extends JpaRepository<NotaHistorial, Long> {
    boolean existsByNotaIdAndAccion(Long notaId, String accion);
    List<NotaHistorial> findByNotaIdOrderByCreatedAtAscIdAsc(Long notaId);
    Optional<NotaHistorial> findFirstByNotaIdAndAccionInOrderByCreatedAtDescIdDesc(Long notaId, List<String> acciones);
}
