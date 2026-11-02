package com.sigaa.controller;

import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import static org.assertj.core.api.Assertions.assertThat;

class NotaSecurityContractTest {
    @Test
    void operacionesNuevasExigenPermisosEspecificosEnBackend() {
        assertAuthority("reservarNumero", "Notas:Reservar número");
        assertAuthority("renovarNumero", "Notas:Reservar número");
        assertAuthority("adjuntarPdf", "Notas:Adjuntar PDF");
        assertAuthority("crearRelacion", "Notas:Relacionar");
        assertAuthority("eliminarRelacion", "Notas:Relacionar");
        assertAuthority("descargarPdf", "Notas:Descargar");
        assertAuthority("descargarVersion", "Notas:Descargar");
        assertAuthority("anular", "Notas:Anular");
        assertAuthority("eliminarPdf", "Notas:Eliminar PDF");
        assertAuthority("oficializar", "Notas:Firmar");
        assertAuthority("descargarWord", "Notas:Descargar");
        assertAuthority("descargarDocumentoPdf", "Notas:Descargar");
        assertAuthority("vistaPrevia", "Notas:Ver");
        assertAuthority("listado", "Notas:Ver");
        assertAuthority("seguimiento", "Notas:Dar seguimiento");
        assertAuthority("seguimiento", "Notas:Cambiar seguimiento");
        assertAuthority("seguimiento", "Notas:Firmar");
    }

    private void assertAuthority(String methodName, String authority) {
        Method method = Arrays.stream(NotaController.class.getDeclaredMethods())
            .filter(item -> item.getName().equals(methodName)).findFirst().orElseThrow();
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
        assertThat(annotation).as(methodName + " debe estar protegido").isNotNull();
        assertThat(annotation.value()).contains(authority);
    }
}
