package com.sigaa.repository;

import com.sigaa.model.Carpeta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import java.util.List;
import java.util.Optional;

public interface CarpetaRepository extends JpaRepository<Carpeta, Long> {
    @Override
    @EntityGraph(attributePaths = "propietario")
    Optional<Carpeta> findById(Long id);

    @EntityGraph(attributePaths = "propietario")
    List<Carpeta> findAllByOrderByNombreAsc();
}
