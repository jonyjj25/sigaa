package com.sigaa.repository;

import com.sigaa.model.Parametro;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ParametroRepository extends JpaRepository<Parametro, Long> {

    List<Parametro> findAllByOrderByGrupoAscOrdenAscClaveAsc();

    Optional<Parametro> findByClave(String clave);

    Optional<Parametro> findByCodigo(String codigo);

    boolean existsByClave(String clave);

    boolean existsByClaveAndIdNot(String clave, Long id);

    boolean existsByCodigo(String codigo);

    boolean existsByCodigoAndIdNot(String codigo, Long id);

    List<Parametro> findByGrupoOrderByOrdenAscClaveAsc(String grupo);

    List<Parametro> findByCategoriaOrderByOrdenAsc(String categoria);

    List<Parametro> findByEstadoOrderByOrdenAsc(String estado);

    List<Parametro> findByActivoTrueOrderByGrupoAscOrdenAsc();

    List<Parametro> findByVisibleTrueAndActivoTrueOrderByGrupoAscOrdenAsc();

    List<Parametro> findByGrupoAndActivoTrueOrderByOrdenAscClaveAsc(String grupo);

    List<Parametro> findByModuloIdOrderByOrdenAsc(Long moduloId);

    List<Parametro> findByTipoOrderByOrdenAsc(String tipo);

    List<Parametro> findByRequiereReinicioTrueAndActivoTrue();

    List<Parametro> findByNombreContainingIgnoreCaseOrClaveContainingIgnoreCaseOrCodigoContainingIgnoreCase(
        String nombre, String clave, String codigo);

    @Query("SELECT COALESCE(MAX(p.orden), 0) FROM Parametro p")
    int findMaxOrden();

    @Query("SELECT DISTINCT p.grupo FROM Parametro p ORDER BY p.grupo")
    List<String> findDistinctGrupos();

    @Query("SELECT DISTINCT p.categoria FROM Parametro p WHERE p.categoria IS NOT NULL ORDER BY p.categoria")
    List<String> findDistinctCategorias();

    @Query("SELECT DISTINCT p.tipo FROM Parametro p ORDER BY p.tipo")
    List<String> findDistinctTipos();

    long countByActivoTrue();
}
