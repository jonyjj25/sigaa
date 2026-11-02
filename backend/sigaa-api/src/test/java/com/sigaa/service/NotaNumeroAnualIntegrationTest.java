package com.sigaa.service;

import com.sigaa.model.Usuario;
import com.sigaa.repository.UsuarioRepository;
import com.sigaa.repository.RoleRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = "app.reports.scheduler-delay-ms=3600000")
@ActiveProfiles("test")
class NotaNumeroAnualIntegrationTest {
    @Autowired NotaNumeroService numeros;
    @Autowired JdbcTemplate jdbc;
    @Autowired UsuarioRepository usuarios;
    @Autowired RoleRepository roles;
    private final List<Integer> years = new ArrayList<>();

    @AfterEach void limpiarSoloInventarioCreadoPorLaPrueba() {
        years.forEach(year -> jdbc.update("DELETE FROM nota_numeros WHERE anio = ?", year));
    }

    @Test void primeraNotaIncrementoYReinicioSonIndependientesPorAnio() {
        int first = nuevoAnio();
        numeros.asegurarVentana(first);
        assertThat(inventario(first)).containsExactly(
            "OAT-001-" + first, "OAT-002-" + first, "OAT-003-" + first, "OAT-004-" + first, "OAT-005-" + first);
        jdbc.update("UPDATE nota_numeros SET estado = 'OCUPADO' WHERE anio = ? AND consecutivo = 1", first);
        numeros.asegurarVentana(first);
        assertThat(inventario(first)).hasSize(6).endsWith("OAT-006-" + first);
        int second = nuevoAnio();
        numeros.asegurarVentana(second);
        assertThat(inventario(second)).hasSize(5).startsWith("OAT-001-" + second);
        assertThat(inventario(first)).hasSize(6);
    }

    @Test void dosSesionesCreanElPrimerInventarioSinDuplicarConsecutivos() throws Exception {
        int year = nuevoAnio();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var task = (java.util.concurrent.Callable<Void>) () -> {
                ready.countDown(); assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                numeros.asegurarVentana(year); return null;
            };
            var one = pool.submit(task); var two = pool.submit(task);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); start.countDown();
            one.get(20, TimeUnit.SECONDS); two.get(20, TimeUnit.SECONDS);
        }
        assertThat(inventario(year)).hasSize(5).doesNotHaveDuplicates();
        assertThat(inventario(year).getFirst()).isEqualTo("OAT-001-" + year);
    }

    @Test @Transactional
    void rolDirectorPermiteUnCargoProfesionalYExcluyeUsuariosInactivosOBloqueados() {
        var role = roles.findByNombre("Director").orElseThrow();
        Usuario director = new Usuario(); String id = UUID.randomUUID().toString();
        director.setNombre("Ana"); director.setApellido("Pérez"); director.setCargo("Comisionada");
        director.setCorreo("nota-rol-" + id + "@example.test"); director.setUsername("nota-rol-" + id);
        director.setPassword("test-only"); director.getRoles().add(role);
        usuarios.saveAndFlush(director);
        assertThat(usuarios.findActiveDirectors()).extracting(Usuario::getId).contains(director.getId());
        assertThat(director.getCargo()).isEqualTo("Comisionada");
        director.setBloqueado(true); usuarios.saveAndFlush(director);
        assertThat(usuarios.findActiveDirectors()).extracting(Usuario::getId).doesNotContain(director.getId());
        director.setBloqueado(false); director.setActivo(false); usuarios.saveAndFlush(director);
        assertThat(usuarios.findActiveDirectors()).extracting(Usuario::getId).doesNotContain(director.getId());
    }

    private List<String> inventario(int year) {
        return jdbc.queryForList("SELECT numero FROM nota_numeros WHERE anio = ? ORDER BY consecutivo", String.class, year);
    }
    private int nuevoAnio() {
        int year = 8000 + Math.floorMod(UUID.randomUUID().hashCode(), 1000);
        while (years.contains(year) || jdbc.queryForObject("SELECT count(*) FROM nota_numeros WHERE anio = ?", Integer.class, year) != 0) year++;
        years.add(year); return year;
    }
}
