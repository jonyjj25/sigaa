package com.sigaa.repository;

import com.sigaa.model.OrganizationDepartment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationDepartmentRepository extends JpaRepository<OrganizationDepartment, Long> {
    List<OrganizationDepartment> findByOrganizationIdOrderByNombreAsc(Long organizationId);
    List<OrganizationDepartment> findByOrganizationIdAndActivoTrueOrderByNombreAsc(Long organizationId);
    Optional<OrganizationDepartment> findByIdAndOrganizationId(Long id, Long organizationId);
    boolean existsByOrganizationIdAndNombreIgnoreCase(Long organizationId, String nombre);
    boolean existsByOrganizationIdAndNombreIgnoreCaseAndIdNot(Long organizationId, String nombre, Long id);
}
