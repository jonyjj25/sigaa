package com.sigaa.service;

import com.sigaa.dto.NotaCreateRequest;
import com.sigaa.dto.NotaUpdateRequest;
import com.sigaa.model.Nota;
import com.sigaa.model.NotaNumero;
import com.sigaa.model.Usuario;
import com.sigaa.repository.NotaNumeroRepository;
import com.sigaa.repository.NotaRepository;
import com.sigaa.repository.UsuarioRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = "app.reports.scheduler-delay-ms=3600000")
@ActiveProfiles("test")
class NotaAsignacionIntegrationTest {
    @Autowired NotaService service;
    @Autowired NotaNumeroRepository numeros;
    @Autowired NotaRepository notas;
    @Autowired UsuarioRepository usuarios;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;
    private final List<Long> notaIds = new ArrayList<>();
    private final List<Long> usuarioIds = new ArrayList<>();
    private Usuario creador;
    private Usuario seguimiento;
    private Usuario nuevoSeguimiento;
    private NotaNumero numero;

    @BeforeEach void prepararDatosPropios() {
        creador = crearUsuario("Elaborador"); seguimiento = crearUsuario("Responsable");
        nuevoSeguimiento = crearUsuario("Nuevo responsable");
        int anio = LocalDate.now(ZoneId.of("America/Panama")).getYear();
        List<NotaNumero> inventario = numeros.findAllByOrderByNumeroAsc();
        int secuencia = 999;
        while (secuencia > 0) {
            int candidato = secuencia;
            boolean existe = inventario.stream().anyMatch(item ->
                (item.getAnio() == anio && java.util.Objects.equals(item.getConsecutivo(), candidato))
                    || NotaNumeroService.formatear(candidato, anio).equals(item.getNumero()));
            if (!existe) break;
            secuencia--;
        }
        assertThat(secuencia).as("Consecutivo de prueba sin alterar inventario existente").isPositive();
        numero = new NotaNumero(); numero.setAnio(anio); numero.setConsecutivo(secuencia);
        numero.setNumero(NotaNumeroService.formatear(secuencia, anio));
        // Representa una reserva válida seleccionada anteriormente: puede conservarse aun si hay libres menores.
        numero.setEstado(NotaNumeroService.RESERVADO); numero.setReservadoPor(creador);
        numero.setReservadoDesde(LocalDateTime.now()); numero.setReservadoHasta(LocalDateTime.now().plusMinutes(10));
        numero = numeros.saveAndFlush(numero);
    }

    @AfterEach void limpiarSoloDatosCreadosPorLaPrueba() {
        for (Long id : notaIds) {
            jdbc.update("DELETE FROM audit_logs WHERE entidad = 'nota' AND entidad_id = ?", id);
            jdbc.update("DELETE FROM notas WHERE id = ?", id);
        }
        if (numero != null && numero.getId() != null) jdbc.update("DELETE FROM nota_numeros WHERE id = ?", numero.getId());
        for (Long id : usuarioIds) {
            jdbc.update("DELETE FROM notificaciones WHERE usuario_id = ?", id);
            jdbc.update("DELETE FROM usuarios WHERE id = ?", id);
        }
    }

    @Test void avisoDeCreacionSoloEsVisibleParaOtraSesionTrasCommit() {
        Nota creada = new TransactionTemplate(transactionManager).execute(status -> {
            Nota nota = crear();
            assertThat(contarAvisos(seguimiento)).isEqualTo(1);
            assertThat(contarDesdeOtraConexion(seguimiento)).isZero();
            return nota;
        });
        assertThat(contarDesdeOtraConexion(seguimiento)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT mensaje FROM notificaciones WHERE usuario_id = ?", String.class,
            seguimiento.getId())).contains(numero.getNumero(), "registro " + creada.getId(), "Asignación de prueba");
    }

    @Test void rollbackDeCreacionRevierteNotaAvisoYOcupacionDelNumero() {
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            crear();
            assertThat(contarAvisos(seguimiento)).isEqualTo(1);
            throw new IllegalStateException("Operación posterior fallida");
        })).hasMessageContaining("posterior fallida");
        assertThat(contarAvisos(seguimiento)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notas WHERE numero_id = ?", Integer.class, numero.getId())).isZero();
        assertThat(numeros.findById(numero.getId()).orElseThrow().getEstado()).isEqualTo(NotaNumeroService.RESERVADO);
    }

    @Test void guardarMismoResponsableNoDuplicaYCambiarloNotificaSoloAlNuevo() {
        Nota creada = crear();
        service.actualizar(creada.getId(), actualizacion(seguimiento), creador);
        service.actualizar(creada.getId(), actualizacion(seguimiento), creador);
        assertThat(contarAvisos(seguimiento)).isEqualTo(1);
        service.actualizar(creada.getId(), actualizacion(nuevoSeguimiento), creador);
        service.actualizar(creada.getId(), actualizacion(nuevoSeguimiento), creador);
        assertThat(contarAvisos(nuevoSeguimiento)).isEqualTo(1);
        assertThat(contarAvisos(seguimiento)).isEqualTo(1);
    }

    @Test void rollbackDeReasignacionNoDejaAvisoNiCambiaResponsable() {
        Nota creada = crear();
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            service.actualizar(creada.getId(), actualizacion(nuevoSeguimiento), creador);
            throw new IllegalStateException("Reasignación rechazada");
        })).hasMessageContaining("rechazada");
        assertThat(contarAvisos(nuevoSeguimiento)).isZero();
        assertThat(jdbc.queryForObject("SELECT seguimiento_usuario_id FROM notas WHERE id = ?", Long.class,
            creada.getId())).isEqualTo(seguimiento.getId());
    }

    @Test void guardadosConcurrentesConMismoNuevoResponsableGeneranUnSoloAviso() throws Exception {
        Nota creada = crear();
        CountDownLatch listos = new CountDownLatch(2); CountDownLatch iniciar = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var tarea = (java.util.concurrent.Callable<Void>) () -> {
                listos.countDown(); assertThat(iniciar.await(10, TimeUnit.SECONDS)).isTrue();
                service.actualizar(creada.getId(), actualizacion(nuevoSeguimiento), creador);
                return null;
            };
            var primera = pool.submit(tarea); var segunda = pool.submit(tarea);
            assertThat(listos.await(10, TimeUnit.SECONDS)).isTrue(); iniciar.countDown();
            primera.get(20, TimeUnit.SECONDS); segunda.get(20, TimeUnit.SECONDS);
        }
        assertThat(contarAvisos(nuevoSeguimiento)).isEqualTo(1);
    }

    private Usuario crearUsuario(String nombre) {
        String identificador = "nota-aviso-" + UUID.randomUUID();
        Usuario usuario = new Usuario(); usuario.setNombre(nombre); usuario.setApellido("Prueba");
        usuario.setCorreo(identificador + "@example.test"); usuario.setUsername(identificador); usuario.setPassword("test-only");
        usuario = usuarios.saveAndFlush(usuario); usuarioIds.add(usuario.getId()); return usuario;
    }

    private Nota crear() {
        NotaCreateRequest request = new NotaCreateRequest(); request.setNumeroId(numero.getId());
        request.setAsunto("Asignación de prueba"); request.setDestino("Destino");
        request.setSeguimientoUsuarioId(seguimiento.getId());
        Nota creada = service.crear(request, null, creador, false); notaIds.add(creada.getId()); return creada;
    }

    private NotaUpdateRequest actualizacion(Usuario responsable) {
        NotaUpdateRequest request = new NotaUpdateRequest(); request.setAsunto("Asignación de prueba");
        request.setDestino("Destino"); request.setSeguimientoUsuarioId(responsable.getId()); return request;
    }

    private int contarAvisos(Usuario destinatario) {
        return jdbc.queryForObject("SELECT count(*) FROM notificaciones WHERE usuario_id = ?", Integer.class, destinatario.getId());
    }

    private int contarDesdeOtraConexion(Usuario destinatario) {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement("SELECT count(*) FROM notificaciones WHERE usuario_id = ?")) {
            statement.setLong(1, destinatario.getId());
            try (var result = statement.executeQuery()) { result.next(); return result.getInt(1); }
        } catch (java.sql.SQLException exception) {
            throw new IllegalStateException("No se pudo comprobar visibilidad tras commit", exception);
        }
    }
}
