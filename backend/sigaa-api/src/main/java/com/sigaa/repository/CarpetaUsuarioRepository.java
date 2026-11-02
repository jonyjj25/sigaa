package com.sigaa.repository;
import com.sigaa.model.CarpetaUsuario;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface CarpetaUsuarioRepository extends JpaRepository<CarpetaUsuario, Long> {
    List<CarpetaUsuario> findByCarpetaId(Long carpetaId);
    List<CarpetaUsuario> findByUsuarioId(Long usuarioId);
    boolean existsByCarpetaIdAndUsuarioId(Long carpetaId, Long usuarioId);
}
