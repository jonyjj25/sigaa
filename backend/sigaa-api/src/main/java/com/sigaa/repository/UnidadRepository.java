package com.sigaa.repository;

import com.sigaa.model.Unidad;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface UnidadRepository extends JpaRepository<Unidad, Long> {
    Optional<Unidad> findByNombre(String nombre);
    List<Unidad> findByActivoTrue();
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from Unidad u where u.id = :id")
    Optional<Unidad> findForMembershipUpdate(@Param("id") Long id);
}
