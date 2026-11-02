package com.sigaa.repository;

import com.sigaa.model.InventarioActivo;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventarioActivoRepository extends JpaRepository<InventarioActivo, Long> {
    List<InventarioActivo> findAllByOrderByCodigoAsc();
    boolean existsByCodigo(String codigo);

    // Bloqueo transaccional entre instancias; no crea objetos en la base.
    @Query(value = "SELECT 1 FROM pg_advisory_xact_lock(1397311305, 1)", nativeQuery = true)
    int bloquearGeneracionCodigo();

    @Query(value = """
            SELECT COALESCE(MAX(CAST(SUBSTRING(codigo FROM CHAR_LENGTH(:prefijo) + 1) AS BIGINT)), 0)
            FROM inventario_activos
            WHERE codigo LIKE CONCAT(:prefijo, '%')
              AND codigo ~ '^INV-[0-9]{4}-[0-9]+$'
            """, nativeQuery = true)
    long ultimoNumeroCodigo(@Param("prefijo") String prefijo);

    @Query("SELECT COALESCE(i.estado, 'sin estado'), COUNT(i) FROM InventarioActivo i GROUP BY i.estado")
    List<Object[]> countByEstadoAgrupado();
}
