package com.sigaa.security;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Reproductor del hallazgo pendiente: filtro/logout reales e identidad/token válidos simulados. */
class LogoutReplayDiagnosticTest extends RequiredPasswordSecurityTest {
    @Test void aCapturedValidTokenMustNotAuthorizeAfterLogout() throws Exception {
        transactions.committed.setCambioContrasenaRequerido(false);
        transactions.committed.setContrasenaTemporalExpiraEn(null);
        var mvc = (MockMvc) ReflectionTestUtils.getField(this, "mvc");
        mvc.perform(get("/api/documentos").header("Authorization", "Bearer session-before"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/auth/logout").cookie(new Cookie("sigaa_access", "session-before")))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("Max-Age=0")));
        mvc.perform(get("/api/documentos").header("Authorization", "Bearer session-before"))
                .andExpect(status().isUnauthorized());
    }
}
