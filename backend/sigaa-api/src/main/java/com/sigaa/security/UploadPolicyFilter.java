package com.sigaa.security;

import com.sigaa.service.SecurityPolicyService;
import com.sigaa.util.FileContentValidator;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Applies the file-upload policies at request time. Servlet multipart limits are
 * still a hard upper bound; these checks make reductions in Configuracion take
 * effect immediately without restarting the application.
 */
@Component
public class UploadPolicyFilter extends OncePerRequestFilter {
    private final SecurityPolicyService policyService;

    public UploadPolicyFilter(SecurityPolicyService policyService) {
        this.policyService = policyService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String contentType = request.getContentType();
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith(MediaType.MULTIPART_FORM_DATA_VALUE)) {
            filterChain.doFilter(request, response);
            return;
        }

        long maxRequestBytes = policyService.getMaxRequestSizeBytes();
        if (request.getContentLengthLong() > maxRequestBytes) {
            reject(response, "La solicitud excede el límite de carga configurado.");
            return;
        }

        long maxFileBytes = policyService.getMaxFileSizeBytes();
        Set<String> extensions = Arrays.stream(policyService.getAllowedFileExtensions().split(","))
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .filter(value -> !value.isBlank())
                .collect(Collectors.toSet());
        try {
            for (Part part : request.getParts()) {
                String filename = part.getSubmittedFileName();
                if (filename == null || filename.isBlank()) continue;
                // Angular serializes the task payload as a JSON Blob.  Browsers
                // submit that part with filename="blob", even though it is form
                // metadata rather than an uploaded file.  Applying the file
                // extension policy to it rejects otherwise valid task updates.
                if (isJsonMetadataPart(part)) continue;
                if (part.getSize() > maxFileBytes) {
                    reject(response, "Uno de los archivos excede el tamaño máximo permitido.");
                    return;
                }
                String extension = extensionOf(filename);
                if (!extensions.contains(extension)) {
                    reject(response, "El tipo de archivo ." + extension + " no está permitido.");
                    return;
                }
                try (var input = part.getInputStream()) {
                    FileContentValidator.validate(filename, input, extensions);
                } catch (IOException exception) {
                    reject(response, "El contenido de uno de los archivos no es válido para su extensión.");
                    return;
                }
            }
        } catch (IllegalStateException exception) {
            reject(response, "La solicitud excede el límite de carga permitido.");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isJsonMetadataPart(Part part) {
        if (!"datos".equals(part.getName())) return false;
        String contentType = part.getContentType();
        if (contentType == null) return false;
        try {
            return MediaType.APPLICATION_JSON.isCompatibleWith(MediaType.parseMediaType(contentType));
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private String extensionOf(String filename) {
        String safe = filename.replace('\\', '/');
        int dot = safe.lastIndexOf('.');
        return dot > safe.lastIndexOf('/') && dot < safe.length() - 1
                ? safe.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }

    private void reject(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}
