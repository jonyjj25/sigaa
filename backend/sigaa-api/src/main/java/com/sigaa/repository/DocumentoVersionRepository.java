package com.sigaa.repository;

import com.sigaa.model.DocumentoVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface DocumentoVersionRepository extends JpaRepository<DocumentoVersion, Long> {
    List<DocumentoVersion> findByDocumentoIdOrderByFechaDesc(Long documentoId);
    List<DocumentoVersion> findByDocumentoIdOrderByIdDesc(Long documentoId);
    Optional<DocumentoVersion> findByIdAndDocumentoId(Long id, Long documentoId);
    @org.springframework.data.jpa.repository.Query("select coalesce(max(v.numero), 0) from DocumentoVersion v where v.documentoId = :documentoId")
    int ultimoNumero(@org.springframework.data.repository.query.Param("documentoId") Long documentoId);
}
