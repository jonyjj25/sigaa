package com.sigaa.repository;

import com.sigaa.model.Organization;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface OrganizationRepository extends JpaRepository<Organization, Long> {
    Optional<Organization> findByActivoTrue();
    Optional<Organization> findFirstByActivoTrueOrderByIdAsc();
    List<Organization> findAllByActivoTrueOrderByNombreAsc();
    List<Organization> findByEstadoOrderByNombreAsc(String estado);
    List<Organization> findByTipoOrganizacionOrderByNombreAsc(String tipoOrganizacion);
    Optional<Organization> findByCodigoInstitucional(String codigo);
    Optional<Organization> findByRuc(String ruc);
    boolean existsByCodigoInstitucional(String codigo);
    boolean existsByCodigoInstitucionalAndIdNot(String codigo, Long id);
    boolean existsByRuc(String ruc);
    boolean existsByRucAndIdNot(String ruc, Long id);
    long countByActivoTrue();
}
