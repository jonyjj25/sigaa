package com.sigaa.controller;

import com.sigaa.model.Memo;
import com.sigaa.model.MemoNumero;
import com.sigaa.model.Usuario;
import com.sigaa.repository.MemoNumeroRepository;
import com.sigaa.repository.MemoRepository;
import com.sigaa.repository.UsuarioRepository;
import com.sigaa.service.MemoWordService;
import com.sigaa.service.NotaRenderService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "app.reports.scheduler-delay-ms=3600000")
@AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class MemoDocumentoSecurityIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UsuarioRepository usuarios;
    @Autowired MemoNumeroRepository numeros;
    @Autowired MemoRepository memos;
    @MockitoBean MemoWordService word;
    @MockitoBean NotaRenderService renderer;
    private Long id;

    @BeforeEach void datosFicticios() {
        Usuario creador = usuario("memo-emisor@example.test"); usuario("memo-ajeno@example.test");
        MemoNumero numero = new MemoNumero(); numero.setNumero("PRUEBA-" + UUID.randomUUID()); numero.setEstado("OCUPADO");
        numero = numeros.saveAndFlush(numero);
        Memo memo = new Memo(); memo.setNumero(numero); memo.setAsunto("Memo ficticio"); memo.setDestino("Área ficticia");
        memo.setCreadoPor(creador); memo.setSeguimientoUsuario(creador); id = memos.saveAndFlush(memo).getId();
        when(word.generar(any())).thenReturn(new byte[]{1}); when(word.generarVistaPrevia(any())).thenReturn(new byte[]{1});
        when(renderer.pdf(any())).thenReturn("%PDF-test-only".getBytes());
        when(renderer.vistaPrevia(any(), anyString(), anyString())).thenReturn(new NotaRenderService.VistaPrevia(List.of("data:image/png;base64,TEST"),1,"MEMO-PRUEBA.DOCX"));
    }

    @Test @WithMockUser(username = "memo-emisor@example.test", authorities = "Memos:Ver")
    void permiteVerDescargarEImprimirElMemoAutorizado() throws Exception {
        mvc.perform(get("/api/memos/{id}/vista-previa", id)).andExpect(status().isOk()).andExpect(jsonPath("$.totalPaginas").value(1))
            .andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(get("/api/memos/{id}/documento.pdf", id)).andExpect(status().isOk()).andExpect(content().contentType("application/pdf"))
            .andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(get("/api/memos/{id}/word", id)).andExpect(status().isOk());
    }

    @Test @WithMockUser(username = "memo-ajeno@example.test", authorities = "Memos:Ver")
    void conocerElIdYElPermisoGeneralNoPermiteExportarOtroMemo() throws Exception { rechazar(403); }

    @Test @WithMockUser(username = "memo-emisor@example.test", authorities = "Otro:Ver")
    void serCreadorNoSustituyeElPermisoFuncional() throws Exception { rechazar(403); }

    @Test void noPermiteDescargasSinAutenticacion() throws Exception { rechazar(401); }

    private void rechazar(int estado) throws Exception {
        for (String accion : List.of("vista-previa", "documento.pdf", "word")) {
            mvc.perform(get("/api/memos/{id}/{accion}", id, accion)).andExpect(status().is(estado));
        }
        verifyNoInteractions(word, renderer);
    }
    private Usuario usuario(String correo) {
        Usuario item = new Usuario(); item.setNombre("Persona ficticia"); item.setApellido("Prueba"); item.setCorreo(correo);
        item.setUsername("memo-" + UUID.randomUUID()); item.setPassword("TEST_ONLY_NOT_REAL"); return usuarios.saveAndFlush(item);
    }
}
