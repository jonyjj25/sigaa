package com.sigaa.service;

import com.sigaa.model.Nota;
import com.sigaa.model.NotaNumero;
import com.sigaa.model.Usuario;
import com.sigaa.repository.NotaNumeroRepository;
import com.sigaa.repository.NotaRepository;
import com.sigaa.repository.UsuarioRepository;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "app.reports.scheduler-delay-ms=3600000")
@ActiveProfiles("test")
@Transactional
class NotaListadoIntegrationTest {
    @Autowired NotaService servicio;
    @Autowired NotaRepository notas;
    @Autowired NotaNumeroRepository numeros;
    @Autowired UsuarioRepository usuarios;
    @Autowired JdbcTemplate jdbc;
    Usuario creador;
    Usuario responsable;
    Usuario externo;
    int anio;
    Nota primera;
    Nota segunda;
    Nota oculta;

    @BeforeEach void prepararDatosAislados() {
        anio = 8000 + Math.floorMod(UUID.randomUUID().hashCode(), 900);
        while (jdbc.queryForObject("select count(*) from nota_numeros where anio in (?, ?)", Integer.class, anio, anio + 1) != 0) anio++;
        creador = usuario("Autor", "Notas");
        responsable = usuario("Ana", "Seguimiento");
        responsable.setSegundoNombre("María"); usuarios.saveAndFlush(responsable);
        externo = usuario("Persona", "Externa");
        primera = nota(1, anio, LocalDate.of(anio, 2, 3), "BORRADOR", "Destinataria Primera", creador, responsable, false);
        segunda = nota(2, anio, LocalDate.of(anio, 3, 4), "EN_SEGUIMIENTO", "Destinataria Segunda", creador, responsable, false);
        oculta = nota(3, anio, LocalDate.of(anio, 3, 4), "BORRADOR", "Información reservada", externo, externo, true);
        nota(1, anio + 1, LocalDate.of(anio + 1, 1, 1), "CERRADA", "Otro año", creador, responsable, false);
    }

    @Test void combinaNumeroCanonicoDestinatarioEstadoAnioFechasYResponsable() {
        var resultado = servicio.listado("", "OAT-001-" + anio, "primera", "BORRADOR",
            LocalDate.of(anio, 1, 1), LocalDate.of(anio, 12, 31), primera.getFechaOficial(), primera.getFechaOficial(),
            "maría seguimiento", 0, 25, creador.getId(), false);
        assertThat(resultado.notas()).extracting(Nota::getId).containsExactly(primera.getId());
        assertThat(resultado.total()).isEqualTo(1);
        assertThat(resultado.resumen()).containsOnlyKeys("BORRADOR").containsEntry("BORRADOR", 1L);
    }

    @Test void paginacionYResumenSoloCuentanCorrespondenciaAutorizada() {
        var primeraPagina = listado("", "", creador, false, 0, 1);
        var segundaPagina = listado("", "", creador, false, 1, 1);
        assertThat(primeraPagina.total()).isEqualTo(2);
        assertThat(primeraPagina.totalPages()).isEqualTo(2);
        assertThat(primeraPagina.notas()).hasSize(1);
        assertThat(segundaPagina.notas()).hasSize(1);
        assertThat(primeraPagina.notas().getFirst().getId()).isNotEqualTo(segundaPagina.notas().getFirst().getId());
        assertThat(primeraPagina.resumen()).containsEntry("BORRADOR", 1L).containsEntry("EN_SEGUIMIENTO", 1L);
        assertThat(listado("información reservada", "", creador, false, 0, 25).total()).isZero();
        assertThat(listado("", "", externo, true, 0, 25).total()).isEqualTo(3);
        var fueraDeRango = listado("", "", creador, false, 9, 1);
        assertThat(fueraDeRango.page()).isEqualTo(1);
        assertThat(fueraDeRango.notas()).hasSize(1);
    }

    @Test void accesoDeResponsableYFirmanteConservaLaReglaExistente() {
        assertThat(listado("", "", responsable, false, 0, 25).total()).isEqualTo(2);
        oculta.setFirmante(creador); notas.saveAndFlush(oculta);
        assertThat(listado("información reservada", "", creador, false, 0, 25).notas())
            .extracting(Nota::getId).containsExactly(oculta.getId());
    }

    @Test void busquedaGeneralEncuentraDestinatariosYNumerosHistoricosPresentadosCanonicos() {
        assertThat(listado("destinataria segunda", "", creador, false, 0, 25).notas())
            .extracting(Nota::getId).containsExactly(segunda.getId());
        assertThat(listado("OAT-001-" + anio, "", creador, false, 0, 25).notas())
            .extracting(Nota::getId).containsExactly(primera.getId());
        responsable.setSegundoNombre(null); usuarios.saveAndFlush(responsable);
        assertThat(listado("", "ana seguimiento", creador, false, 0, 25).total()).isEqualTo(2);
        assertThat(listado("autor notas", "", creador, false, 0, 25).total()).isEqualTo(2);
        primera.getNumero().setConsecutivo(null); primera.getNumero().setNumero("N-007");
        numeros.saveAndFlush(primera.getNumero());
        assertThat(listado("OAT-007-" + anio, "", creador, false, 0, 25).notas())
            .extracting(Nota::getId).containsExactly(primera.getId());
    }

    @Test void caracteresDeComodinNoAmplianLaBusqueda() {
        assertThat(listado("%", "", creador, false, 0, 25).total()).isZero();
        primera.setAsunto("Resultado 100% confirmado"); notas.saveAndFlush(primera);
        assertThat(listado("%", "", creador, false, 0, 25).notas()).extracting(Nota::getId).containsExactly(primera.getId());
        assertThat(listado("_", "", creador, false, 0, 25).total()).isZero();
    }

    private NotaService.Listado listado(String query, String responsableFiltro, Usuario actual, boolean admin, int page, int size) {
        return servicio.listado(query, "", "", "", LocalDate.of(anio, 1, 1), LocalDate.of(anio, 12, 31),
            null, null, responsableFiltro, page, size, actual.getId(), admin);
    }

    private Usuario usuario(String nombre, String apellido) {
        String key = UUID.randomUUID().toString();
        Usuario usuario = new Usuario(); usuario.setNombre(nombre); usuario.setApellido(apellido);
        usuario.setCorreo("nota-filtros-" + key + "@example.test"); usuario.setUsername("nota-filtros-" + key);
        usuario.setPassword("test-only"); return usuarios.saveAndFlush(usuario);
    }

    private Nota nota(int consecutivo, int year, LocalDate fecha, String estado, String destinatario,
                      Usuario autor, Usuario encargado, boolean sensitiva) {
        NotaNumero numero = new NotaNumero(); numero.setAnio(year); numero.setConsecutivo(consecutivo);
        // Los identificadores anteriores no se renumeran: el filtro también entiende su presentación canónica.
        numero.setNumero("hist-" + UUID.randomUUID()); numero.setEstado("OCUPADO"); numeros.saveAndFlush(numero);
        Nota nota = new Nota(); nota.setNumero(numero); nota.setAsunto("Asunto institucional"); nota.setDestino("Institución");
        nota.setFechaOficial(fecha); nota.setEstado(estado); nota.setDestinatarioNombre(destinatario);
        nota.setCreadoPor(autor); nota.setSeguimientoUsuario(encargado); nota.setSensitiva(sensitiva);
        if ("FIRMADA".equals(estado) || "CERRADA".equals(estado)) {
            nota.setFirmadaPor(autor); nota.setFirmadaAt(fecha.atStartOfDay());
        }
        if ("CERRADA".equals(estado)) {
            nota.setCerradaPor(autor); nota.setCerradaAt(fecha.atStartOfDay());
        }
        return notas.saveAndFlush(nota);
    }
}
