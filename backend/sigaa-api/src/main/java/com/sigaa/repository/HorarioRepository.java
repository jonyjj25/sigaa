package com.sigaa.repository;
import com.sigaa.model.Horario;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface HorarioRepository extends JpaRepository<Horario, Long> {
    List<Horario> findByUsuarioIdOrderByDiaAsc(Long usuarioId);
    void deleteByUsuarioId(Long usuarioId);
}
