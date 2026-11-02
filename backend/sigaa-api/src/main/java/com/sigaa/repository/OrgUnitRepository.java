package com.sigaa.repository;

import com.sigaa.model.OrgUnit;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface OrgUnitRepository extends JpaRepository<OrgUnit, Long> {
    List<OrgUnit> findByPadreIdOrderByOrdenAscNombreAsc(Long padreId);
    List<OrgUnit> findByNivelOrderByOrdenAscNombreAsc(int nivel);
    List<OrgUnit> findByTipoOrderByOrdenAscNombreAsc(String tipo);
    List<OrgUnit> findByActivoTrueOrderByOrdenAscNombreAsc();
    List<OrgUnit> findByPadreIsNullOrderByOrdenAscNombreAsc();
    java.util.Optional<OrgUnit> findFirstByNombreIgnoreCaseAndActivoTrue(String nombre);
    List<OrgUnit> findByNombreContainingIgnoreCaseOrSiglasContainingIgnoreCaseOrCodigoContainingIgnoreCase(String nombre, String siglas, String codigo);
    List<OrgUnit> findByResponsableContainingIgnoreCaseOrderByNombreAsc(String responsable);
    boolean existsByCodigo(String codigo);
    boolean existsByCodigoAndIdNot(String codigo, Long id);
    long countByActivoTrue();
    long countByPadreId(Long padreId);
}
