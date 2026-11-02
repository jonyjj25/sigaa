package com.sigaa.repository;

import com.sigaa.model.AllowedHttpMethod;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AllowedHttpMethodRepository extends JpaRepository<AllowedHttpMethod, Long> {
    List<AllowedHttpMethod> findByActivoTrue();
}
