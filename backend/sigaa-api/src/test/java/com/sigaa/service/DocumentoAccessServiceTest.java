package com.sigaa.service;

import com.sigaa.model.Carpeta;
import com.sigaa.model.Documento;
import com.sigaa.model.DocumentoClasificacion;
import com.sigaa.model.DocumentoUsuarioAcceso;
import com.sigaa.model.Role;
import com.sigaa.model.Unidad;
import com.sigaa.model.Usuario;
import com.sigaa.model.UsuarioUnidad;
import com.sigaa.repository.CarpetaRepository;
import com.sigaa.repository.CarpetaUsuarioRepository;
import com.sigaa.repository.DocumentoSolicitudAprobacionRepository;
import com.sigaa.repository.DocumentoUsuarioAccesoRepository;
import com.sigaa.repository.UsuarioUnidadRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DocumentoAccessServiceTest {
    private DocumentoUsuarioAccesoRepository accesos;
    private DocumentoSolicitudAprobacionRepository aprobaciones;
    private UsuarioUnidadRepository membresias;
    private CarpetaUsuarioRepository usuariosCarpeta;
    private CarpetaRepository carpetas;
    private DocumentoAccessService service;

    @BeforeEach
    void setUp() {
        accesos = mock(DocumentoUsuarioAccesoRepository.class);
        aprobaciones = mock(DocumentoSolicitudAprobacionRepository.class);
        membresias = mock(UsuarioUnidadRepository.class);
        usuariosCarpeta = mock(CarpetaUsuarioRepository.class);
        carpetas = mock(CarpetaRepository.class);
        service = new DocumentoAccessService(carpetas, usuariosCarpeta,
                accesos, aprobaciones, membresias);
    }

    @Test
    void confidentialFolderMembershipAndOwnershipNeverOverrideDocumentAccess() {
        Usuario recipient = user(20L);
        Documento documento = document(100L, 10L, DocumentoClasificacion.CONFIDENCIAL); documento.setCarpetaId(50L);
        Carpeta folder = new Carpeta(); folder.setTipo("COMPARTIDA"); folder.setPropietario(recipient);
        when(carpetas.findById(50L)).thenReturn(Optional.of(folder));
        when(usuariosCarpeta.existsByCarpetaIdAndUsuarioId(50L, 20L)).thenReturn(true);
        assertThat(service.canView(documento, recipient)).isFalse();
        assertThat(service.canModify(documento, recipient)).isFalse();
        assertThat(service.canManageAccess(documento, recipient)).isFalse();
    }

    @Test
    void anExplicitShareCannotBypassUnitClassification() {
        Usuario recipient = user(20L); Documento documento = document(100L, 10L, DocumentoClasificacion.USO_INTERNO);
        documento.setArea("Unidad ficticia");
        DocumentoUsuarioAcceso access = new DocumentoUsuarioAcceso(); access.setDocumento(documento); access.setUsuario(recipient); access.setPermiso("EDICION");
        when(accesos.findByDocumentoIdAndUsuarioId(100L, 20L)).thenReturn(Optional.of(access));
        assertThat(service.canView(documento, recipient)).isFalse(); assertThat(service.canModify(documento, recipient)).isFalse();
    }

    @Test
    void directorRetainsExistingGlobalResourcePrivileges() {
        Usuario director = user(20L); director.setRoles(Set.of(new Role("Director", "")));
        Documento documento = document(100L, 10L, DocumentoClasificacion.DATOS_SENSIBLES);
        assertThat(service.canView(documento, director)).isTrue(); assertThat(service.canModify(documento, director)).isTrue();
        assertThat(service.canManageAccess(documento, director)).isTrue();
    }

    @Test
    void confidentialDocumentIsLimitedToItsOwnerWithoutExplicitShare() {
        Usuario owner = user(10L);
        Usuario unrelated = user(20L);
        Documento documento = document(100L, owner.getId(), DocumentoClasificacion.CONFIDENCIAL);

        assertThat(service.canView(documento, owner)).isTrue();
        assertThat(service.canModify(documento, owner)).isTrue();
        assertThat(service.canView(documento, unrelated)).isFalse();
        assertThat(service.canModify(documento, unrelated)).isFalse();
    }

    @Test
    void explicitShareGrantsOnlyTheConfiguredCapability() {
        Usuario recipient = user(20L);
        Documento documento = document(100L, 10L, DocumentoClasificacion.DATOS_SENSIBLES);
        DocumentoUsuarioAcceso access = new DocumentoUsuarioAcceso();
        access.setUsuario(recipient);
        access.setDocumento(documento);
        access.setPermiso("LECTURA");
        when(accesos.findByDocumentoIdAndUsuarioId(100L, 20L)).thenReturn(Optional.of(access));

        assertThat(service.canView(documento, recipient)).isTrue();
        assertThat(service.canModify(documento, recipient)).isFalse();

        access.setPermiso("EDICION");
        assertThat(service.canModify(documento, recipient)).isTrue();
        assertThat(service.canDelete(documento, recipient)).isFalse();
    }

    @Test
    void onlyOwnerOrLegacyCustodianCanDeleteDocument() {
        Usuario owner = user(10L);
        Usuario unrelated = user(20L);
        Usuario superadmin = user(1L);
        superadmin.setRoles(Set.of(new Role("Superadmin", "")));
        Documento assigned = document(100L, owner.getId(), DocumentoClasificacion.USO_INTERNO);
        Documento legacy = document(101L, null, DocumentoClasificacion.CONFIDENCIAL);

        assertThat(service.canDelete(assigned, owner)).isTrue();
        assertThat(service.canDelete(assigned, unrelated)).isFalse();
        assertThat(service.canDelete(assigned, superadmin)).isFalse();
        assertThat(service.canDelete(legacy, superadmin)).isTrue();
    }

    @Test
    void legacySharedFlagDoesNotGrantAccessWithoutAnExplicitRecipient() {
        Usuario unrelated = user(20L);
        Documento documento = document(100L, 10L, DocumentoClasificacion.USO_INTERNO);
        documento.setCompartido(true);

        assertThat(service.canView(documento, unrelated)).isFalse();
    }

    @Test
    void restrictedDocumentIsVisibleToMembersOfItsUnitButNotEditableByDefault() {
        Usuario member = user(20L);
        Documento documento = document(100L, 10L, DocumentoClasificacion.RESTRINGIDO);
        documento.setArea("Unidad Jurídica");
        documento.setPermiso("solo_lectura");
        Unidad unidad = new Unidad();
        unidad.setNombre("Unidad Jurídica");
        UsuarioUnidad membership = new UsuarioUnidad();
        membership.setUsuario(member);
        membership.setUnidad(unidad);
        membership.setRol(new Role("Miembro", ""));
        when(membresias.findByUsuarioId(member.getId())).thenReturn(List.of(membership));

        assertThat(service.canView(documento, member)).isTrue();
        assertThat(service.canModify(documento, member)).isFalse();
    }

    @Test
    void superadminRetainsCustodialAccessToUnassignedLegacyDocuments() {
        Usuario superadmin = user(1L);
        superadmin.setRoles(Set.of(new Role("Superadmin", "")));
        Documento documento = document(100L, null, DocumentoClasificacion.CONFIDENCIAL);

        assertThat(service.canView(documento, superadmin)).isTrue();
        assertThat(service.canManageAccess(documento, superadmin)).isTrue();
    }

    @Test
    void superadminCanAdministerAnotherUsersPersonalFolder() {
        Usuario jonathan = user(10L);
        Usuario superadmin = user(1L);
        superadmin.setRoles(Set.of(new Role("Superadmin", "")));
        Carpeta carpeta = new Carpeta();
        carpeta.setId(7L);
        carpeta.setNombre("yony");
        carpeta.setTipo("PERSONAL");
        carpeta.setPropietario(jonathan);

        assertThat(service.canAccessFolder(carpeta, jonathan)).isTrue();
        assertThat(service.canAccessFolder(carpeta, superadmin)).isTrue();
        assertThat(service.canDeleteFolder(carpeta, jonathan)).isTrue();
        assertThat(service.canDeleteFolder(carpeta, superadmin)).isTrue();
    }

    @Test
    void sharedFolderCanOnlyBeDeletedByItsOwner() {
        Usuario owner = user(10L);
        Usuario member = user(11L);
        Carpeta carpeta = new Carpeta();
        carpeta.setId(8L);
        carpeta.setTipo("COMPARTIDA");
        carpeta.setPropietario(owner);

        assertThat(service.canDeleteFolder(carpeta, owner)).isTrue();
        assertThat(service.canDeleteFolder(carpeta, member)).isFalse();
        Usuario superadmin = user(1L);
        superadmin.setRoles(Set.of(new Role("Superadmin", "")));
        assertThat(service.canDeleteFolder(carpeta, superadmin)).isFalse();
    }

    @Test
    void blockedOrInactiveUsersCannotDeleteEvenTheirOwnFolders() {
        Usuario owner = user(10L);
        Carpeta carpeta = new Carpeta(); carpeta.setTipo("COMPARTIDA"); carpeta.setPropietario(owner);
        owner.setBloqueado(true);
        assertThat(service.canDeleteFolder(carpeta, owner)).isFalse();
        owner.setBloqueado(false); owner.setActivo(false);
        assertThat(service.canDeleteFolder(carpeta, owner)).isFalse();
    }

    @Test
    void superadminHasTheExplicitFullAccessRequiredByInstitutionalPolicy() {
        Usuario superadmin = user(1L);
        superadmin.setRoles(Set.of(new Role("Superadmin", "")));
        Documento documento = document(100L, 10L, DocumentoClasificacion.CONFIDENCIAL);

        assertThat(service.canView(documento, superadmin)).isTrue();
        assertThat(service.canModify(documento, superadmin)).isTrue();
        assertThat(service.canManageAccess(documento, superadmin)).isTrue();
    }

    @Test
    void listadoConservaPermisosDeComparticionYNoReutilizaAccesosRevocados() {
        Usuario usuario = user(20L);
        Documento privado = document(100L, 10L, DocumentoClasificacion.CONFIDENCIAL);
        DocumentoUsuarioAcceso acceso = new DocumentoUsuarioAcceso(); acceso.setDocumento(privado); acceso.setUsuario(usuario); acceso.setPermiso("EDICION");
        when(accesos.findByUsuarioId(20L)).thenReturn(List.of(acceso));
        assertThat(service.evaluarListado(List.of(privado), usuario).get(100L)).isEqualTo(new DocumentoAccessService.Acceso(true, true, false));
        when(accesos.findByUsuarioId(20L)).thenReturn(List.of());
        assertThat(service.evaluarListado(List.of(privado), usuario).get(100L)).isEqualTo(new DocumentoAccessService.Acceso(false, false, false));
    }

    @Test
    void listadoNoConcedeAccesoCuandoLaComparticionHaExpirado() {
        Usuario usuario = user(20L); Documento privado = document(100L, 10L, DocumentoClasificacion.DATOS_SENSIBLES);
        DocumentoUsuarioAcceso acceso = new DocumentoUsuarioAcceso(); acceso.setDocumento(privado); acceso.setUsuario(usuario);
        acceso.setPermiso("EDICION"); acceso.setExpiraEn(java.time.LocalDateTime.now().minusMinutes(1));
        when(accesos.findByUsuarioId(20L)).thenReturn(List.of(acceso));
        assertThat(service.evaluarListado(List.of(privado), usuario).get(100L)).isEqualTo(new DocumentoAccessService.Acceso(false, false, false));
    }

    private Usuario user(Long id) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setActivo(true);
        usuario.setBloqueado(false);
        usuario.setCorreo("cuenta-" + id + "@institucion.gob.pa");
        usuario.setNombre("Cuenta");
        usuario.setApellido(String.valueOf(id));
        return usuario;
    }

    private Documento document(Long id, Long ownerId, DocumentoClasificacion classification) {
        Documento documento = new Documento();
        documento.setId(id);
        documento.setPropietarioUsuarioId(ownerId);
        documento.setClasificacion(classification);
        documento.setCompartido(false);
        return documento;
    }
}
