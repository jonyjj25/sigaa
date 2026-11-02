package com.sigaa;

import com.sigaa.controller.MemoController;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import static org.assertj.core.api.Assertions.assertThat;

class MemosModuleContractTest {
    @Test
    void esquemaCanonicoConservaMemosConConsecutivosReservasYTrazabilidad() throws Exception {
        try (var input = getClass().getResourceAsStream("/db/schema.sql")) {
            assertThat(input).isNotNull();
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8).toUpperCase();
            assertThat(sql).contains("CREATE TABLE PUBLIC.MEMO_NUMEROS", "CREATE TABLE PUBLIC.MEMOS",
                    "CREATE TABLE PUBLIC.MEMO_HISTORIAL");
            assertThat(sql).contains("RESERVADO_HASTA", "ANULACION_MOTIVO", "DAR SEGUIMIENTO");
        }
    }

    @Test
    void esquemaCanonicoMantieneCorrespondenciaComoModuloDeAcceso() throws Exception {
        try (var input = getClass().getResourceAsStream("/db/schema.sql")) {
            assertThat(input).isNotNull();
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8).toUpperCase();
            assertThat(sql).contains("'CORRESPONDENCIA'", "'/CORRESPONDENCIA'", "'NOTAS'", "'MEMOS'");
        }
    }

    @Test
    void operacionesExigenPermisosEspecificos() {
        assertAuthority("crear", "Memos:Crear");
        assertAuthority("reservar", "Memos:Reservar número");
        assertAuthority("seguimiento", "Memos:Dar seguimiento");
        assertAuthority("completar", "Memos:Completar");
        assertAuthority("anular", "Memos:Anular");
        for (String nombre : new String[] {"vistaPrevia", "descargarPdf", "descargarWord"}) {
            assertAuthority(nombre, "Memos:Ver");
            Method metodo = Arrays.stream(MemoController.class.getDeclaredMethods())
                    .filter(item -> item.getName().equals(nombre)).findFirst().orElseThrow();
            assertThat(metodo.getAnnotation(PreAuthorize.class).value())
                    .contains("@correspondenciaAccessService.memo(#id)");
        }
    }

    private void assertAuthority(String methodName, String authority) {
        Method method = Arrays.stream(MemoController.class.getDeclaredMethods())
                .filter(item -> item.getName().equals(methodName)).findFirst().orElseThrow();
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).contains(authority);
    }
}
