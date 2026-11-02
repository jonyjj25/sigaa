package com.sigaa.repository;
import com.sigaa.model.DocumentoSolicitudAprobacion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
public interface DocumentoSolicitudAprobacionRepository extends JpaRepository<DocumentoSolicitudAprobacion, Long> {
    @EntityGraph(attributePaths = {"solicitante", "aprobador", "version"})
    Optional<DocumentoSolicitudAprobacion> findFirstByDocumentoIdAndEstadoOrderByFechaSolicitudDesc(Long documentoId, String estado);
    @EntityGraph(attributePaths = {"solicitante", "aprobador", "version"})
    List<DocumentoSolicitudAprobacion> findByDocumentoIdInAndEstadoOrderByFechaSolicitudDesc(List<Long> documentoIds, String estado);
    boolean existsByDocumentoIdAndAprobadorIdAndEstado(Long documentoId, Long aprobadorId, String estado);
    @EntityGraph(attributePaths = {"solicitante", "aprobador", "respondidoPor", "version"})
    List<DocumentoSolicitudAprobacion> findByDocumentoIdOrderByFechaSolicitudDescIdDesc(Long documentoId);
    @org.springframework.data.jpa.repository.Query("SELECT s.documento.id FROM DocumentoSolicitudAprobacion s WHERE s.aprobador.id = :aprobadorId AND s.estado = 'PENDIENTE'")
    List<Long> findDocumentoIdsPendientesPorAprobador(@org.springframework.data.repository.query.Param("aprobadorId") Long aprobadorId);
}
