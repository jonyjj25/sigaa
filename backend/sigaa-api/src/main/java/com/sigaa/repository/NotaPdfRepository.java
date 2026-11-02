package com.sigaa.repository;

import com.sigaa.model.NotaPdf;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotaPdfRepository extends JpaRepository<NotaPdf, Long> {
    List<NotaPdf> findByNotaIdAndEliminadoAtIsNullOrderByNumeroVersionDesc(Long notaId);
    Optional<NotaPdf> findFirstByNotaIdAndVigenteTrueAndEliminadoAtIsNullOrderByNumeroVersionDesc(Long notaId);
    Optional<NotaPdf> findFirstByNotaIdAndEliminadoAtIsNullOrderByNumeroVersionDesc(Long notaId);
    Optional<NotaPdf> findByIdAndNotaIdAndEliminadoAtIsNull(Long id, Long notaId);

    @Query("select coalesce(max(pdf.numeroVersion), 0) from NotaPdf pdf where pdf.nota.id = :notaId")
    int maxVersion(@Param("notaId") Long notaId);

    @Modifying(flushAutomatically = true)
    @Query("update NotaPdf pdf set pdf.vigente = false where pdf.nota.id = :notaId and pdf.vigente = true")
    int desmarcarVigente(@Param("notaId") Long notaId);
}
