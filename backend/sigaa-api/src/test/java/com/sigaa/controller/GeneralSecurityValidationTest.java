package com.sigaa.controller;

import com.sigaa.model.Reunion;
import com.sigaa.model.Usuario;
import com.sigaa.repository.ReunionRepository;
import com.sigaa.repository.UsuarioRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class GeneralSecurityValidationTest {
    @Autowired MockMvc mvc;
    @Autowired UsuarioRepository users;
    @Autowired ReunionRepository meetings;
    Usuario participant;
    Usuario outsider;
    Reunion meeting;
    private static final String VALID = "{\"titulo\":\"Fixture A08\",\"fecha\":\"2026-10-13\",\"horaInicio\":\"09:00\",\"horaFin\":\"10:00\"}";

    @BeforeEach void fixtures() {
        participant = fictionalUser(); outsider = fictionalUser();
        meeting = new Reunion(); meeting.setCodigo("QA-" + UUID.randomUUID().toString().substring(0, 12));
        meeting.setTitulo("Reunión ficticia"); meeting.setFecha(LocalDate.of(2026, 10, 13));
        meeting.setHoraInicio(LocalTime.of(9, 0)); meeting.setHoraFin(LocalTime.of(10, 0));
        meeting.setParticipantesUsuarios(Set.of(participant)); meeting = meetings.saveAndFlush(meeting);
    }

    @Test void meetingReadRequiresResourceAccessEvenWithGeneralPermission() throws Exception {
        mvc.perform(get("/api/reuniones/{id}", meeting.getId()).with(user(participant.getCorreo()).authorities(() -> "Reuniones:Ver")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(meeting.getId()));
        mvc.perform(get("/api/reuniones/{id}", meeting.getId()).with(user(outsider.getCorreo()).authorities(() -> "Reuniones:Ver")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/reuniones/codigo/{code}", meeting.getCodigo()).with(user(outsider.getCorreo()).authorities(() -> "Reuniones:Ver")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/reuniones/{id}", meeting.getId()).with(user(participant.getCorreo())))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/reuniones/{id}", meeting.getId())).andExpect(status().isUnauthorized());
    }

    @Test void foreignMeetingCannotBeModifiedDeletedDuplicatedOrHaveItsStateChanged() throws Exception {
        mvc.perform(put("/api/reuniones/{id}", meeting.getId()).with(user(outsider.getCorreo()).authorities(() -> "Reuniones:Editar"))
                .contentType("application/json").content(VALID)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/reuniones/{id}", meeting.getId()).with(user(outsider.getCorreo()).authorities(() -> "Reuniones:Eliminar")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/reuniones/{id}/duplicar", meeting.getId()).with(user(outsider.getCorreo()).authorities(() -> "Reuniones:Duplicar")))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/reuniones/{id}/estado", meeting.getId()).with(user(outsider.getCorreo()).authorities(() -> "Reuniones:Cancelar"))
                .contentType("application/json").content("{\"estado\":\"cancelada\"}")).andExpect(status().isForbidden());
    }

    @Test void invalidInputHasPublic4xxResponsesInsteadOf500OrSqlDetails() throws Exception {
        mvc.perform(get("/api/reuniones/no-es-id").with(user(participant.getCorreo()).authorities(() -> "Reuniones:Ver")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").doesNotExist());
        mvc.perform(get("/api/reuniones/999999999").with(user(participant.getCorreo()).authorities(() -> "Reuniones:Ver")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.trace").doesNotExist());
        for (String json : new String[]{"{}", "{incompleto", VALID.replace("2026-10-13", "2026-99-99"),
                VALID.replace("Fixture A08", "x".repeat(301)), VALID.replace("\"10:00\"", "\"10:00\",\"estado\":\"INVALIDO\"")}) {
            mvc.perform(post("/api/reuniones").with(user(participant.getCorreo()).authorities(() -> "Reuniones:Crear"))
                    .contentType("application/json").content(json)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.trace").doesNotExist()).andExpect(jsonPath("$.exception").doesNotExist());
        }
    }

    @Test void unauthorizedCrossOriginCookieRequestsAreRejected() throws Exception {
        mvc.perform(options("/api/auth/me").header("Origin", "https://foreign.invalid")
                .header("Access-Control-Request-Method", "GET")).andExpect(status().isForbidden());
    }

    private Usuario fictionalUser() {
        String unique = UUID.randomUUID().toString();
        Usuario value = new Usuario(); value.setNombre("Ficticio"); value.setApellido("Validación");
        value.setUsername("qa-" + unique); value.setCorreo(unique + "@example.test");
        value.setPassword("TEST_ONLY_NOT_A_REAL_CREDENTIAL"); return users.saveAndFlush(value);
    }
}
