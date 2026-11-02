package com.sigaa.repository;

import com.sigaa.model.IpRestriction;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface IpRestrictionRepository extends JpaRepository<IpRestriction, Long> {
    List<IpRestriction> findByTipoAndActivoTrue(String tipo);
    List<IpRestriction> findByTipoOrderByDireccionIpAsc(String tipo);
    boolean existsByTipoAndDireccionIp(String tipo, String direccionIp);
}
