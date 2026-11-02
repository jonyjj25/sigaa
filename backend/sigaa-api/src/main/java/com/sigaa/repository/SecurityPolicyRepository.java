package com.sigaa.repository;

import com.sigaa.model.SecurityPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

import java.util.List;

public interface SecurityPolicyRepository extends JpaRepository<SecurityPolicy, Long> {
    // Serializa las escasas escrituras administrativas para validar parámetros relacionados.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from SecurityPolicy p order by p.id")
    List<SecurityPolicy> findAllForUpdate();

    List<SecurityPolicy> findAllByOrderByCategoriaAsc();
    List<SecurityPolicy> findAllByOrderByCategoriaAscNombreAsc();
    List<SecurityPolicy> findByCategoria(String categoria);
    List<SecurityPolicy> findByNombre(String nombre);
    boolean existsByNombre(String nombre);
}
