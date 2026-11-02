package com.sigaa.repository;

import com.sigaa.model.AllowedOrigin;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AllowedOriginRepository extends JpaRepository<AllowedOrigin, Long> {
    List<AllowedOrigin> findByActivoTrue();
    boolean existsByOrigen(String origen);
}
