package com.sigaa.repository;
import com.sigaa.model.GrupoUsuario; import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface GrupoUsuarioRepository extends JpaRepository<GrupoUsuario,Long> { List<GrupoUsuario> findByGrupoIdOrderByFechaAsignacionAsc(Long grupoId); Optional<GrupoUsuario> findByGrupoIdAndUsuarioId(Long grupoId,Long usuarioId); }
