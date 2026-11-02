package com.sigaa.controller;

import com.sigaa.config.GlobalExceptionHandler;
import com.sigaa.dto.InventarioCreateRequest;
import com.sigaa.dto.InventarioUpdateRequest;
import com.sigaa.service.InventarioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** MVC aislado: no carga la aplicación, configuración ni bases de datos. */
class InventarioControllerTest {
    private InventarioService service;
    private MockMvc mvc;
    private static final String FICHA = "\"nombre\":\"Silla de prueba\",\"categoria\":\"Mobiliario\","
            + "\"ubicacion\":\"Oficina de prueba\",\"estado\":\"en buen estado\"";

    @BeforeEach
    void setup() {
        service = mock(InventarioService.class);
        var validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(new InventarioController(service))
                .setValidator(validator).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test void missingRequiredFieldsReturnFieldErrorsBeforeSaving() throws Exception {
        mvc.perform(post("/api/inventario").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.nombre").exists())
                .andExpect(jsonPath("$.fields.categoria").exists()).andExpect(jsonPath("$.fields.ubicacion").exists())
                .andExpect(jsonPath("$.fields.estado").exists());
        verifyNoInteractions(service);
    }

    @Test void officeAssetDoesNotRequireSupplierOrAssignment() throws Exception {
        mvc.perform(post("/api/inventario").contentType(MediaType.APPLICATION_JSON).content("{" + FICHA + "}"))
                .andExpect(status().isCreated());
        verify(service).crear(any(InventarioCreateRequest.class));
    }

    @Test void invalidStateIsRejected() throws Exception {
        mvc.perform(post("/api/inventario").contentType(MediaType.APPLICATION_JSON)
                .content("{" + FICHA.replace("en buen estado", "vendido") + "}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.estado").exists());
        verifyNoInteractions(service);
    }

    @Test void invalidDateReturns400InsteadOfInternalServerError() throws Exception {
        mvc.perform(post("/api/inventario").contentType(MediaType.APPLICATION_JSON)
                .content("{" + FICHA + ",\"fechaAdquisicion\":\"2026-02-30\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test void futureDateIsRejected() throws Exception {
        mvc.perform(post("/api/inventario").contentType(MediaType.APPLICATION_JSON)
                .content("{" + FICHA + ",\"fechaAdquisicion\":\"2999-01-01\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.fechaAdquisicion").exists());
        verifyNoInteractions(service);
    }

    @Test void oversizeUnitIsRejected() throws Exception {
        mvc.perform(post("/api/inventario").contentType(MediaType.APPLICATION_JSON)
                .content("{" + FICHA + ",\"unidad\":\"" + "a".repeat(201) + "\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.unidad").exists());
        verifyNoInteractions(service);
    }

    @Test void editRequiresVersionAndCompleteRequiredFields() throws Exception {
        mvc.perform(put("/api/inventario/1").contentType(MediaType.APPLICATION_JSON).content("{" + FICHA + "}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.lockVersion").exists());
        mvc.perform(put("/api/inventario/1").contentType(MediaType.APPLICATION_JSON).content("{\"lockVersion\":0}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.nombre").exists());
        verifyNoInteractions(service);
    }

    @Test void validEditReachesService() throws Exception {
        mvc.perform(put("/api/inventario/1").contentType(MediaType.APPLICATION_JSON)
                .content("{" + FICHA + ",\"lockVersion\":0,\"tipoAsignacion\":\"UNIDAD\",\"asignadoUnidadId\":7}"))
                .andExpect(status().isOk());
        verify(service).actualizar(eq(1L), any(InventarioUpdateRequest.class));
    }

    @Test void inconsistentOrInvalidAssignmentIsRejectedByValidation() throws Exception {
        for (String assignment : new String[]{
                "\"tipoAsignacion\":\"PERSONA\"",
                "\"tipoAsignacion\":\"UNIDAD\",\"asignadoUnidadId\":-1",
                "\"tipoAsignacion\":\"SIN_ASIGNAR\",\"asignadoUsuarioId\":8",
                "\"tipoAsignacion\":\"PERSONA\",\"asignadoUsuarioId\":8,\"asignadoUnidadId\":7"}) {
            mvc.perform(post("/api/inventario").contentType(MediaType.APPLICATION_JSON)
                    .content("{" + FICHA + "," + assignment + "}"))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }

    @Test void editCannotSilentlyRemoveAssignmentByOmittingItsType() throws Exception {
        mvc.perform(put("/api/inventario/1").contentType(MediaType.APPLICATION_JSON)
                .content("{" + FICHA + ",\"lockVersion\":0}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.tipoAsignacion").exists());
        verifyNoInteractions(service);
    }

    @Test void invalidSerialAndCodeAreRejected() throws Exception {
        mvc.perform(post("/api/inventario").contentType(MediaType.APPLICATION_JSON)
                .content("{" + FICHA + ",\"serie\":\"SN\\n123\",\"codigo\":\"cualquier-codigo\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.serie").exists())
                .andExpect(jsonPath("$.fields.codigo").exists());
        verifyNoInteractions(service);
    }
}
