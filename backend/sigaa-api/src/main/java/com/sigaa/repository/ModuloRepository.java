package com.sigaa.repository;

import com.sigaa.model.Modulo;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ModuloRepository extends JpaRepository<Modulo, Long> {

    List<Modulo> findAllByOrderByOrdenAsc();

    Optional<Modulo> findByCodigo(String codigo);

    boolean existsByCodigo(String codigo);

    boolean existsByCodigoAndIdNot(String codigo, Long id);

    List<Modulo> findByCategoriaOrderByOrdenAsc(String categoria);

    List<Modulo> findByPadreIsNullOrderByOrdenAsc();

    List<Modulo> findByPadreIdOrderByOrdenAsc(Long padreId);

    List<Modulo> findByActivoTrueOrderByOrdenAsc();

    List<Modulo> findByVisibleEnMenuTrueAndActivoTrueOrderByOrdenAsc();

    List<Modulo> findByVisibleEnDashboardTrueAndActivoTrueOrderByOrdenAsc();

    List<Modulo> findByNombreContainingIgnoreCaseOrCodigoContainingIgnoreCase(String nombre, String codigo);

    @Query("SELECT COALESCE(MAX(m.orden), 0) FROM Modulo m")
    int findMaxOrden();

    @Query("SELECT COALESCE(MAX(m.nivelJerarquico), 0) FROM Modulo m")
    int findMaxNivel();
}
