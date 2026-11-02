package com.sigaa.repository;
import com.sigaa.model.Ubicacion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface UbicacionRepository extends JpaRepository<Ubicacion, Long> {
    List<Ubicacion> findAllByOrderByNombreAsc();
}
