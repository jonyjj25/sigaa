package com.sigaa.controller;

import com.sigaa.repository.AuditLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class AuditLogControllerIntegrationTest {
    @Autowired
    private AuditLogRepository repository;

    @Test
    void paginatedEndpointReturnsExistingAuditRecordsWithoutOptionalFilters() {
        AuditLogController controller = new AuditLogController(repository);

        var result = controller.listarPaginado(0, 12, null, null, null, null, null);

        assertThat(result.totalElements()).isEqualTo(repository.count());
        assertThat(result.content()).hasSizeLessThanOrEqualTo(12);
        if (repository.count() > 0) assertThat(result.content()).isNotEmpty();
    }

    @Test
    void paginatedEndpointAppliesExactActionFilter() {
        var latest = repository.findTop10ByOrderByFechaDesc();
        if (latest.isEmpty()) return;
        String action = latest.getFirst().getAccion();
        AuditLogController controller = new AuditLogController(repository);

        var result = controller.listarPaginado(0, 25, null, action, null, null, null);

        assertThat(result.content()).isNotEmpty();
        assertThat(result.content()).allMatch(log -> action.equals(log.getAccion()));
    }
}
