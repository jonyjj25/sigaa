package com.sigaa.security;

import com.sigaa.service.SecurityPolicyService;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockPart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UploadPolicyFilterTest {
    private UploadPolicyFilter filter;

    @BeforeEach
    void setUp() {
        SecurityPolicyService policies = mock(SecurityPolicyService.class);
        when(policies.getMaxRequestSizeBytes()).thenReturn(45L * 1024L * 1024L);
        when(policies.getMaxFileSizeBytes()).thenReturn(10L * 1024L * 1024L);
        when(policies.getAllowedFileExtensions()).thenReturn("pdf");
        filter = new UploadPolicyFilter(policies);
    }

    @Test
    void permitsAngularJsonBlobAndValidatesTheActualAttachment() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/tareas/19");
        request.setContentType(MediaType.MULTIPART_FORM_DATA_VALUE);
        MockPart metadata = new MockPart("datos", "blob", "{}".getBytes());
        metadata.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        MockPart attachment = new MockPart("archivos", "acta.pdf", "%PDF-1.7".getBytes());
        attachment.getHeaders().setContentType(MediaType.APPLICATION_PDF);
        request.addPart(metadata);
        request.addPart(attachment);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.doFilter(request, response, (req, res) -> chainCalled.set(true));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chainCalled).isTrue();
    }

    @Test
    void rejectsUnsupportedAttachments() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/tareas/19");
        request.setContentType(MediaType.MULTIPART_FORM_DATA_VALUE);
        MockPart attachment = new MockPart("archivos", "archivo.exe", new byte[] {0x4d, 0x5a});
        request.addPart(attachment);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> { });

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains(".exe");
    }
}
