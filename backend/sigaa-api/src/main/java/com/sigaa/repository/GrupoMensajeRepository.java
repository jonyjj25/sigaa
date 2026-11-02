package com.sigaa.repository;
import com.sigaa.model.GrupoMensaje; import org.springframework.data.domain.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface GrupoMensajeRepository extends JpaRepository<GrupoMensaje, Long> { Page<GrupoMensaje> findByGrupoIdAndEliminadoFalseOrderByCreadoEnDesc(Long grupoId, Pageable pageable); }
