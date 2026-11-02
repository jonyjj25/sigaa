package com.sigaa.util;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileContentValidatorTest {
    @Test
    void aceptaUnPdfConFirmaValida() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "reporte.pdf", "application/pdf",
                "%PDF-1.7\ncontenido".getBytes(StandardCharsets.US_ASCII));

        assertThat(FileContentValidator.validate(file, Set.of("pdf"))).isEqualTo("pdf");
    }

    @Test
    void rechazaUnEjecutableDisfrazadoDePdf() {
        MockMultipartFile file = new MockMultipartFile("file", "reporte.pdf", "application/pdf",
                new byte[]{0x4d, 0x5a, 0x00, 0x00});

        assertThatThrownBy(() -> FileContentValidator.validate(file, Set.of("pdf")))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("no coincide");
    }
}
