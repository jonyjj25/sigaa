package com.sigaa.repository;

import com.sigaa.model.NotaRelacion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotaRelacionRepository extends JpaRepository<NotaRelacion, Long> {
    @Query("select relacion from NotaRelacion relacion "
        + "where relacion.notaOrigen.id = :notaId or relacion.notaDestino.id = :notaId "
        + "order by relacion.createdAt asc")
    List<NotaRelacion> findAllForNota(@Param("notaId") Long notaId);

    boolean existsByNotaOrigenIdAndNotaDestinoIdAndTipo(Long origenId, Long destinoId, String tipo);

    @Query("select relacion from NotaRelacion relacion where relacion.id = :id "
        + "and (relacion.notaOrigen.id = :notaId or relacion.notaDestino.id = :notaId)")
    Optional<NotaRelacion> findByIdForNota(@Param("id") Long id, @Param("notaId") Long notaId);
}
