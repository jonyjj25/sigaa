package com.sigaa;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class NotasSchemaContractTest {
    @Test
    void esquemaCanonicoContieneElModeloCompletoDeNotas() throws Exception {
        try (var input = getClass().getResourceAsStream("/db/schema.sql")) {
            assertThat(input).isNotNull();
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8).toUpperCase();
            assertThat(sql).contains("CREATE TABLE PUBLIC.NOTAS", "CREATE TABLE PUBLIC.NOTA_PDFS",
                    "CREATE TABLE PUBLIC.NOTA_RELACIONES", "CREATE TABLE PUBLIC.NOTA_HISTORIAL");
            assertThat(sql).contains("FECHA_OFICIAL", "DESTINATARIO_NOMBRE", "INSTITUCION_ID", "CONTENIDO",
                    "SENSITIVA", "ANULACION_MOTIVO", "ANULADA_POR_ID", "ELIMINACION_MOTIVO",
                    "ELIMINADO_POR_ID", "FIRMANTE_NOMBRE", "FIRMANTE_INSTITUCION",
                    "OFICIALIZADA_POR_ID", "DEPARTAMENTO_ID", "DEPARTAMENTO_NOMBRE");
            assertThat(sql).doesNotContain("FLYWAY_SCHEMA_HISTORY", "DB/MIGRATION");
        }
    }
}
