package com.sigaa.repository;

import com.sigaa.model.DocumentoUsuarioAcceso;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface DocumentoUsuarioAccesoRepository extends JpaRepository<DocumentoUsuarioAcceso, Long> {
    Optional<DocumentoUsuarioAcceso> findByDocumentoIdAndUsuarioId(Long documentoId, Long usuarioId);
    List<DocumentoUsuarioAcceso> findByDocumentoIdOrderByCreadoEnAsc(Long documentoId);
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "documento")
    List<DocumentoUsuarioAcceso> findByUsuarioId(Long usuarioId);
    long countByDocumentoId(Long documentoId);
    void deleteByDocumentoIdAndUsuarioId(Long documentoId, Long usuarioId);
}
