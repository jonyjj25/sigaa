package com.sigaa.repository;

import com.sigaa.model.TipoDocumento;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TipoDocumentoRepository extends JpaRepository<TipoDocumento, Long> {

    List<TipoDocumento> findAllByOrderByNombreAsc();

    List<TipoDocumento> findByActivoTrueOrderByNombreAsc();

    List<TipoDocumento> findByActivoFalseOrderByNombreAsc();

    List<TipoDocumento> findByNombreContainingIgnoreCaseOrCodigoContainingIgnoreCaseOrDescripcionContainingIgnoreCase(
        String nombre, String codigo, String descripcion);

    boolean existsByCodigo(String codigo);

    boolean existsByCodigoAndIdNot(String codigo, Long id);

    boolean existsByNombre(String nombre);

    boolean existsByNombreAndIdNot(String nombre, Long id);

    long countByActivoTrue();
}
