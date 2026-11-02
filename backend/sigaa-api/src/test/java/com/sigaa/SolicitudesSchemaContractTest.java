package com.sigaa;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class SolicitudesSchemaContractTest {
    @Test
    void esquemaCanonicoRetiraSoloLasTablasVaciasYConservaElHistorico() throws Exception {
        try (var input = getClass().getResourceAsStream("/db/schema.sql")) {
            assertThat(input).isNotNull();
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8).toUpperCase();
            assertThat(sql).doesNotContain("CREATE TABLE PUBLIC.SOLICITUD_ADJUNTOS");
            assertThat(sql).doesNotContain("CREATE TABLE PUBLIC.SOLICITUD_COMENTARIOS");
            assertThat(sql).contains("CREATE TABLE PUBLIC.SOLICITUD_TIPOS");
            assertThat(sql).contains("CREATE TABLE PUBLIC.SOLICITUD_ETAPAS");
            assertThat(sql).contains("CREATE TABLE PUBLIC.SOLICITUDES");
            assertThat(sql).contains("CREATE TABLE PUBLIC.SOLICITUD_APROBACIONES");
            assertThat(sql).contains("CREATE TABLE PUBLIC.SOLICITUD_HISTORIAL");
            assertThat(sql).doesNotContain("FLYWAY_SCHEMA_HISTORY", "DB/MIGRATION");
        }
    }
}
