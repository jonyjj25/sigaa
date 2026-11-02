package com.sigaa;

import com.sigaa.repository.ActaRepository;
import com.sigaa.repository.ReunionRepository;
import com.sigaa.repository.TareaRepository;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.data.jpa.repository.Query;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Ejecuta el SQL de los repositorios en un esquema aislado de la base de pruebas. */
class CodigoConcurrenciaTest {
    private String url;
    private String schema;

    @BeforeEach void preparar() throws Exception {
        url = System.getProperty("sigaa.test.codigo.url");
        if (url == null && !Boolean.getBoolean("sigaa.security.validation")) url = System.getenv("TEST_DB_URL");
        assumeTrue(url != null && url.startsWith("jdbc:postgresql:"), "Requiere PostgreSQL de pruebas");
        schema = "test_codigos_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = conexion(); Statement sql = connection.createStatement()) {
            sql.execute("CREATE SCHEMA " + schema);
            for (String table : List.of("actas", "reuniones", "tareas")) {
                sql.execute("CREATE TABLE " + schema + "." + table + " (id BIGSERIAL PRIMARY KEY, codigo VARCHAR(80) UNIQUE NOT NULL)");
            }
        }
    }

    @AfterEach void limpiar() throws Exception {
        if (schema != null) try (Connection connection = conexion(); Statement sql = connection.createStatement()) {
            sql.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }

    @Test void actasConcurrentesConservanFormatoYConsecutivoHistorico() throws Exception {
        probar(ActaRepository.class, "actas", "ACT-2026-", 3, "ultimoNumeroCodigo", "ACT-2025-050");
    }
    @Test void reunionesConcurrentesNoRepitenCodigo() throws Exception {
        probar(ReunionRepository.class, "reuniones", "RUN-", 4, "findMaxCodigoNumber", "RUN-0050");
    }
    @Test void tareasConcurrentesIgnoranCodigosDeActas() throws Exception {
        probar(TareaRepository.class, "tareas", "TAR-2026-", 3, "ultimoNumeroCodigo", "TAR-2026-050");
    }

    private void probar(Class<?> repository, String table, String prefix, int digits, String numberMethod, String seed) throws Exception {
        try (Connection connection = conexion(); Statement sql = connection.createStatement()) {
            sql.execute("SET search_path TO " + schema);
            sql.execute("INSERT INTO " + table + " (codigo) VALUES ('" + seed + "')");
            if (table.equals("tareas")) sql.execute("INSERT INTO tareas (codigo) VALUES ('TAR-ACTA-ABC123')");
        }
        String lock = repository.getMethod("bloquearGeneracionCodigo").getAnnotation(Query.class).value();
        String number = (repository == TareaRepository.class ? repository.getMethod(numberMethod, String.class) : repository.getMethod(numberMethod))
            .getAnnotation(Query.class).value();
        // La consulta de reuniones existente es JPQL; su equivalente SQL conserva la misma expresión.
        number = number.replace("FROM Reunion r", "FROM reuniones r").replace(" AS long", " AS BIGINT");
        final String select = number.replace(":prefijo", "?");
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch ready = new CountDownLatch(8), start = new CountDownLatch(1);
        List<Future<String>> requests = new ArrayList<>();
        try {
            for (int i = 0; i < 8; i++) requests.add(pool.submit(() -> {
                try (Connection connection = conexion(); Statement sql = connection.createStatement()) {
                    sql.execute("SET search_path TO " + schema);
                    connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
                    connection.setAutoCommit(false);
                    ready.countDown();
                    if (!start.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("Inicio concurrente vencido");
                    sql.execute(lock);
                    long last;
                    try (PreparedStatement query = connection.prepareStatement(select)) {
                        int parameterCount = (int) select.chars().filter(character -> character == '?').count();
                        for (int p = 1; p <= parameterCount; p++) query.setString(p, prefix);
                        try (ResultSet result = query.executeQuery()) { result.next(); last = result.getLong(1); }
                    }
                    String code = prefix + String.format(Locale.ROOT, "%0" + digits + "d", last + 1);
                    try (PreparedStatement insert = connection.prepareStatement("INSERT INTO " + table + " (codigo) VALUES (?)")) {
                        insert.setString(1, code); insert.executeUpdate();
                    }
                    connection.commit(); return code;
                }
            }));
            assertThat(ready.await(15, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<String> codes = new ArrayList<>();
            for (Future<String> request : requests) codes.add(request.get(20, TimeUnit.SECONDS));
            assertThat(new HashSet<>(codes)).hasSize(8);
            assertThat(codes).contains(prefix + String.format(Locale.ROOT, "%0" + digits + "d", 51),
                prefix + String.format(Locale.ROOT, "%0" + digits + "d", 58));
        } finally { start.countDown(); pool.shutdownNow(); }
    }

    private Connection conexion() throws SQLException {
        return DriverManager.getConnection(url, System.getProperty("sigaa.test.codigo.user", System.getenv().getOrDefault("DB_USERNAME", "postgres")),
            System.getProperty("sigaa.test.codigo.password", System.getenv().getOrDefault("DB_PASSWORD", "")));
    }
}
