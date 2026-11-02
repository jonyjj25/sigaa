package com.sigaa.repository;

import com.sigaa.model.AllowedHttpHeader;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AllowedHttpHeaderRepository extends JpaRepository<AllowedHttpHeader, Long> {
    List<AllowedHttpHeader> findByActivoTrue();
}
