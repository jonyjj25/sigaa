package com.sigaa;

import com.sigaa.model.SecurityPolicy;
import com.sigaa.repository.SecurityPolicyRepository;
import com.sigaa.service.SecurityPolicyService;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@WithMockUser(roles = "administrador")
class SecurityPolicyValidationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired SecurityPolicyRepository policies;
    @Autowired SecurityPolicyService service;
    @Autowired PlatformTransactionManager transactionManager;

    @AfterEach void rollbackAndRefresh() {
        if (TestTransaction.isActive()) { TestTransaction.flagForRollback(); TestTransaction.end(); }
        service.refreshCache();
    }

    @ParameterizedTest
    @CsvSource({
            "Longitud mínima de contraseña,0", "Longitud mínima de contraseña,-1", "Longitud mínima de contraseña,73", "Longitud mínima de contraseña,129",
            "Longitud máxima de contraseña,0", "Longitud máxima de contraseña,-1", "Longitud máxima de contraseña,129",
            "Historial de contraseñas,-1", "Historial de contraseñas,101",
            "Intentos fallidos máximos,0", "Intentos fallidos máximos,-1", "Intentos fallidos máximos,31",
            "Tiempo de bloqueo (min),0", "Tiempo de bloqueo (min),-1", "Tiempo de bloqueo (min),1441",
            "Expiración de contraseña (días),-1", "Expiración de contraseña (días),366",
            "Tamaño máximo de archivos (MB),-1", "Tamaño máximo de archivos (MB),11",
            "Intentos fallidos máximos,1.5", "Intentos fallidos máximos,abc",
            "Tiempo de bloqueo (min),9223372036854775808", "Historial de contraseñas,2147483648",
            "Autenticación de dos factores,yes", "Restricción por horario,1", "Requiere mayúsculas,si",
            "Política de bloqueo,INDEFINIDO", "Horario inicio acceso,24:00", "Horario fin acceso,12:60",
            "Horario inicio acceso,8:00", "Horario fin acceso,08:00:00", "Horario inicio acceso,no-es-hora",
            "Tipos de archivos permitidos,*", "Tipos de archivos permitidos,.pdf",
            "Tipos de archivos permitidos,../pdf", "Tipos de archivos permitidos,exe"
    })
    void rejectsInvalidValuesWithFieldMessageAndWithoutChangingDatabaseOrEffectiveCache(String name, String value) throws Exception {
        invalid(name, value, name);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"valor\":null}", "{\"valor\":\"\"}", "{\"valor\":\"   \"}"})
    void requiresANonBlankValue(String body) throws Exception {
        var policy = named("Intentos fallidos máximos");
        String original = policy.getValor();
        mvc.perform(put("/api/security/policies/{id}", policy.getId()).contentType("application/json").content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.valor").isNotEmpty());
        assertThat(named(policy.getNombre()).getValor()).isEqualTo(original);
    }

    @Test void rejectsValuesLongerThanTheStorageColumnBeforeWriting() throws Exception {
        mvc.perform(put("/api/security/policies/{id}", named("Tipos de archivos permitidos").getId())
                        .contentType("application/json").content("{\"valor\":\"" + "x".repeat(256) + "\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.valor").value("valor no puede superar 255 caracteres"));
    }

    @ParameterizedTest
    @CsvSource({"Longitud mínima de contraseña,72", "Longitud máxima de contraseña,128",
            "Longitud máxima de contraseña,8", "Historial de contraseñas,0", "Historial de contraseñas,100",
            "Intentos fallidos máximos,1", "Intentos fallidos máximos,30",
            "Tiempo de bloqueo (min),1", "Tiempo de bloqueo (min),1440",
            "Expiración de contraseña (días),0", "Expiración de contraseña (días),365",
            "Tamaño máximo de archivos (MB),0", "Tamaño máximo de archivos (MB),10",
            "Horario inicio acceso,00:00", "Horario fin acceso,23:59",
            "Autenticación de dos factores,true", "Autenticación de dos factores,false"})
    void acceptsValidBoundariesAndExistingZeroDisableSemantics(String name, String value) throws Exception {
        valid(name, value, value);
    }

    @Test void validatesPasswordLengthInBothDirectionsUsingDatabaseInsteadOfStaleCache() throws Exception {
        var maximum = named("Longitud máxima de contraseña");
        maximum.setValor("16"); policies.saveAndFlush(maximum); // Caché aún conserva 128.
        invalid("Longitud mínima de contraseña", "17", "Longitud mínima de contraseña no puede superar");
    }

    @Test void rejectsMaximumBelowTheExistingMinimum() throws Exception {
        invalid("Longitud máxima de contraseña", "7", "Longitud mínima de contraseña no puede superar");
    }

    @Test void rejectsPasswordSettingsThatCannotSatisfyEnabledCharacterRequirements() throws Exception {
        valid("Longitud mínima de contraseña", "1", "1");
        invalid("Longitud máxima de contraseña", "3", "requisitos de caracteres activos");
    }

    @Test void enablingACharacterRequirementAlsoChecksTheMaximum() throws Exception {
        for (String name : List.of("Requiere mayúsculas", "Requiere minúsculas", "Requiere números", "Requiere caracteres especiales")) {
            var policy = named(name); policy.setValor("false"); policies.saveAndFlush(policy);
        }
        valid("Longitud mínima de contraseña", "1", "1");
        valid("Longitud máxima de contraseña", "1", "1");
        valid("Requiere mayúsculas", "true", "true");
        invalid("Requiere números", "true", "requisitos de caracteres activos");
    }

    @Test void cannotDisableExpirationWhilePeriodicPasswordChangeIsEnabled() throws Exception {
        valid("Requiere cambio de contraseña", "true", "true");
        invalid("Expiración de contraseña (días)", "0", "Expiración de contraseña (días)");
    }

    @Test void cannotEnablePeriodicPasswordChangeWithoutExpiration() throws Exception {
        valid("Expiración de contraseña (días)", "0", "0");
        invalid("Requiere cambio de contraseña", "true", "Expiración de contraseña (días)");
    }

    @Test void validatesEqualHoursWhenEnablingScheduleButPreservesOvernightSchedules() throws Exception {
        valid("Horario inicio acceso", "22:00", "22:00");
        invalid("Restricción por horario", "true", "deben ser distintos");
    }

    @Test void changingAnEnabledScheduleCannotMakeBothHoursEqual() throws Exception {
        valid("Restricción por horario", "true", "true");
        invalid("Horario inicio acceso", "22:00", "deben ser distintos");
    }

    @Test void overnightScheduleRemainsValid() throws Exception {
        valid("Horario inicio acceso", "22:00", "22:00");
        valid("Horario fin acceso", "06:00", "06:00");
        valid("Restricción por horario", "true", "true");
        assertThat(service.getAccessStartTime()).isAfter(service.getAccessEndTime());
    }

    @Test void normalizesValidBooleansEnumsAndExtensionListsForExistingConsumers() throws Exception {
        valid("Autenticación de dos factores", " TRUE ", "true");
        valid("Política de bloqueo", "temporal", "TEMPORAL");
        valid("Tipos de archivos permitidos", " PDF, docx ,pdf, WEBP ", "pdf,docx,webp");
    }

    @ParameterizedTest
    @ValueSource(strings = {"pdf,", "pdf,,docx", "pdf;docx", "application/pdf"})
    void rejectsMalformedExtensionLists(String value) throws Exception {
        invalid("Tipos de archivos permitidos", value, "Tipos de archivos permitidos");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Longitud de contraseña mínima", "longitud minima de contrasena", "Tiempo bloqueo (min)", "intentos fallidos maximos"})
    void legacyAliasesAndCaseVariantsDoNotBypassValidation(String alias) throws Exception {
        var policy = new SecurityPolicy(); policy.setNombre(alias); policy.setValor("5"); policy.setCategoria("Legado");
        policy = policies.saveAndFlush(policy);
        mvc.perform(put("/api/security/policies/{id}", policy.getId()).contentType("application/json").content("{\"valor\":\"0\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").isNotEmpty());
        assertThat(policies.findById(policy.getId()).orElseThrow().getValor()).isEqualTo("5");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void committedValidChangePersistsAndConcurrentRelatedUpdatesCannotProduceAnInvalidRange() throws Exception {
        var tx = new TransactionTemplate(transactionManager);
        var min = named("Longitud mínima de contraseña"); var max = named("Longitud máxima de contraseña");
        String oldMin = min.getValor(), oldMax = max.getValor();
        try (var executor = Executors.newFixedThreadPool(2)) {
            valid("Longitud mínima de contraseña", "8", "8");
            valid("Longitud máxima de contraseña", "128", "128");
            var firstLocked = new CountDownLatch(1); var secondStarted = new CountDownLatch(1);
            var first = executor.submit(() -> tx.execute(status -> {
                service.updatePolicy(min.getId(), "60"); firstLocked.countDown();
                try { assertThat(secondStarted.await(10, TimeUnit.SECONDS)).isTrue(); }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException(ex); }
                return true;
            }));
            var second = executor.submit(() -> {
                assertThat(firstLocked.await(10, TimeUnit.SECONDS)).isTrue(); secondStarted.countDown();
                assertThatIllegalArgumentException().isThrownBy(() -> service.updatePolicy(max.getId(), "50"))
                        .withMessageContaining("Longitud mínima de contraseña no puede superar");
                return true;
            });
            assertThat(first.get(20, TimeUnit.SECONDS)).isTrue();
            assertThat(second.get(20, TimeUnit.SECONDS)).isTrue();
            tx.executeWithoutResult(status -> {
                assertThat(named(min.getNombre()).getValor()).isEqualTo("60");
                assertThat(named(max.getNombre()).getValor()).isEqualTo("128");
            });
        } finally {
            tx.executeWithoutResult(status -> {
                var originalMin = named(min.getNombre()); originalMin.setValor(oldMin);
                var originalMax = named(max.getNombre()); originalMax.setValor(oldMax);
                policies.saveAllAndFlush(List.of(originalMin, originalMax));
            });
        }
    }

    private void invalid(String name, String value, String message) throws Exception {
        var policy = named(name); String original = policy.getValor();
        String effective = service.getValue(name, null);
        mvc.perform(put("/api/security/policies/{id}", policy.getId()).contentType("application/json")
                        .content("{\"valor\":\"" + value + "\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString(message)));
        assertThat(named(name).getValor()).isEqualTo(original);
        assertThat(service.getValue(name, null)).isEqualTo(effective);
    }

    private void valid(String name, String value, String stored) throws Exception {
        mvc.perform(put("/api/security/policies/{id}", named(name).getId()).contentType("application/json")
                        .content("{\"valor\":\"" + value + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.valor").value(stored));
        assertThat(named(name).getValor()).isEqualTo(stored);
    }

    private SecurityPolicy named(String name) { return policies.findByNombre(name).getFirst(); }
}
