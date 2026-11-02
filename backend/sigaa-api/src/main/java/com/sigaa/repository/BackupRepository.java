package com.sigaa.repository;
import com.sigaa.model.Backup;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface BackupRepository extends JpaRepository<Backup, Long> {
    List<Backup> findAllByOrderByFechaDesc();
    Optional<Backup> findFirstByEstadoIgnoreCaseOrderByFechaDesc(String estado);
    Optional<Backup> findFirstByVerificacionEstadoIgnoreCaseOrderByVerificadoAtDesc(String estado);
}
