package com.sigaa;

import com.sigaa.dto.OrganizationRequest;
import com.sigaa.model.InventarioActivo;
import com.sigaa.model.Organization;
import com.sigaa.model.OrganizationDepartment;
import com.sigaa.model.UserPreferences;
import com.sigaa.model.Usuario;
import com.sigaa.repository.OrganizationDepartmentRepository;
import com.sigaa.repository.OrganizationRepository;
import com.sigaa.repository.UsuarioRepository;
import com.sigaa.service.OrganizationService;
import com.sigaa.service.UsuarioDetailsService;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UnusedMetadataCleanupIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entities;
    @Autowired UsuarioRepository usuarios;
    @Autowired OrganizationRepository organizaciones;
    @Autowired OrganizationDepartmentRepository departamentos;
    @Autowired OrganizationService organizationService;
    @Autowired UsuarioDetailsService details;
    @Autowired PasswordEncoder passwords;

    @Test void migratedSchemaPreservesSecurityProfilePreferencesAndInventory() {
        assertThat(columns("usuarios")).doesNotContain("firma_digital_ruta", "qr_institucional")
            .contains("bloqueado_hasta", "segundo_nombre", "telefono", "direccion", "departamento",
                "codigo_interno", "observaciones", "token_version");

        String key = UUID.randomUUID().toString();
        Usuario usuario = new Usuario();
        usuario.setNombre("Ana"); usuario.setSegundoNombre("María"); usuario.setApellido("Prueba");
        usuario.setUsername("cleanup." + key); usuario.setCorreo(key + "@example.test");
        usuario.setPassword(passwords.encode("ClaveDePrueba123!"));
        usuario.setTelefono("555-0100"); usuario.setDireccion("Dirección de prueba");
        usuario.setDepartamento("Departamento de prueba"); usuario.setCodigoInterno(key);
        usuario.setObservaciones("Información de perfil que debe conservarse");
        usuario.setBloqueadoHasta(LocalDateTime.now().plusMinutes(5));
        usuario = usuarios.saveAndFlush(usuario);

        UserPreferences preferencias = new UserPreferences();
        preferencias.setUsuario(usuario); preferencias.setLanguage("pt");
        entities.persist(preferencias);
        InventarioActivo activo = new InventarioActivo();
        activo.setCodigo("CLEANUP-" + key); activo.setNombre("Activo de prueba");
        activo.setProveedor("Proveedor conservado");
        entities.persist(activo); entities.flush(); entities.clear();

        Usuario persisted = usuarios.findById(usuario.getId()).orElseThrow();
        assertThat(persisted.getSegundoNombre()).isEqualTo("María");
        assertThat(persisted.getTelefono()).isEqualTo("555-0100");
        assertThat(persisted.getDireccion()).isEqualTo("Dirección de prueba");
        assertThat(persisted.getDepartamento()).isEqualTo("Departamento de prueba");
        assertThat(persisted.getCodigoInterno()).isEqualTo(key);
        assertThat(persisted.getObservaciones()).isEqualTo("Información de perfil que debe conservarse");
        assertThat(persisted.getBloqueadoHasta()).isNotNull();
        assertThat(passwords.matches("ClaveDePrueba123!", details.loadUserByUsername(persisted.getCorreo()).getPassword())).isTrue();
        persisted.setBloqueado(true); usuarios.saveAndFlush(persisted);
        assertThat(details.loadUserByUsername(persisted.getCorreo()).isAccountNonLocked()).isFalse();
        assertThat(jdbc.queryForObject("select language from user_preferences where usuario_id=?", String.class, usuario.getId())).isEqualTo("pt");
        assertThat(columns("inventario_activos")).doesNotContain("valor");
        assertThat(jdbc.queryForObject("select proveedor from inventario_activos where id=?", String.class, activo.getId())).isEqualTo("Proveedor conservado");
    }

    @Test void institutionCatalogAndDepartmentReferencesContinueWorking() {
        assertThat(columns("organizations")).containsExactlyInAnyOrder("id", "nombre", "siglas",
            "codigo_institucional", "tipo_organizacion", "ruc", "director", "director_cargo",
            "estado", "activo", "created_at", "updated_at");
        String key = UUID.randomUUID().toString();
        OrganizationRequest request = new OrganizationRequest();
        request.setNombre("Institución de prueba " + key); request.setSiglas("PRUEBA");
        request.setCodigoInstitucional(key); request.setRuc(key.substring(0, 20)); request.setTipoOrganizacion("Institucion");
        request.setDirector("Directora de prueba"); request.setDirectorCargo("Directora General");
        request.setActivo(true); request.setEstado("ACTIVO");
        var response = organizationService.crear(request);
        Organization institucion = organizaciones.findById(response.getId()).orElseThrow();
        OrganizationDepartment departamento = new OrganizationDepartment();
        departamento.setOrganization(institucion); departamento.setNombre("Departamento " + key);
        departamento.setDirector("Responsable de prueba"); departamento.setDirectorCargo("Jefatura");
        departamento.setActivo(true); departamento = departamentos.saveAndFlush(departamento);
        entities.clear();

        assertThat(organizationService.obtenerPorId(response.getId()).getDirector()).isEqualTo("Directora de prueba");
        assertThat(organizationService.listarDepartamentos(response.getId())).anySatisfy(item -> {
            assertThat(item.nombre()).isEqualTo("Departamento " + key);
            assertThat(item.director()).isEqualTo("Responsable de prueba");
        });
        assertThat(departamentos.findByIdAndOrganizationId(departamento.getId(), response.getId())).isPresent();
    }

    private List<String> columns(String table) {
        return jdbc.queryForList("select column_name from information_schema.columns where table_schema='public' and table_name=?",
            String.class, table);
    }
}
