package com.sigaa;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Checks construction and accessors of the concrete models and DTOs.
 * This smoke test complements, but does not replace, JSON/JPA integration tests.
 */
class ModelAndDtoContractTest {

    @TestFactory
    Stream<DynamicTest> everyModelAndDtoCanBeConstructedAndAccessed() throws Exception {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        CachingMetadataReaderFactory metadata = new CachingMetadataReaderFactory(resolver);
        Set<String> classNames = new HashSet<>();
        for (String pattern : List.of("classpath*:com/sigaa/model/**/*.class", "classpath*:com/sigaa/dto/**/*.class")) {
            for (var resource : resolver.getResources(pattern)) {
                String name = metadata.getMetadataReader(resource).getClassMetadata().getClassName();
                if (!name.contains("$") || name.matches(".*\\$[A-Za-z][A-Za-z0-9_]*$")) classNames.add(name);
            }
        }

        assertFalse(classNames.isEmpty(), "No se encontraron modelos ni DTO para comprobar");
        return classNames.stream().sorted().map(name -> DynamicTest.dynamicTest(name, () -> exercise(name)));
    }

    private void exercise(String className) throws Exception {
        Class<?> type = Class.forName(className);
        if (type.isInterface() || Modifier.isAbstract(type.getModifiers()) || type.isAnnotation()) return;
        if (type.isEnum()) {
            Object[] constants = type.getEnumConstants();
            assertNotNull(constants);
            if (constants.length > 0) {
                assertEquals(constants[0], type.getMethod("valueOf", String.class)
                        .invoke(null, ((Enum<?>) constants[0]).name()));
            }
            return;
        }

        Object instance = instantiate(type);
        assertNotNull(instance, "No se pudo validar " + className);

        if (type.isRecord()) {
            for (var component : type.getRecordComponents()) {
                assertEquals(sample(component.getType()), component.getAccessor().invoke(instance),
                        className + "." + component.getName());
            }
        }

        for (Method method : type.getMethods()) {
            if (method.isBridge() || method.isSynthetic() || Modifier.isStatic(method.getModifiers())) continue;
            if (method.getName().startsWith("set") && method.getParameterCount() == 1) {
                Object value = sample(method.getParameterTypes()[0]);
                assertDoesNotThrow(() -> method.invoke(instance, value), method.toString());
            }
        }
        for (Method method : type.getMethods()) {
            if (method.isBridge() || method.isSynthetic() || Modifier.isStatic(method.getModifiers())) continue;
            boolean accessor = method.getParameterCount() == 0
                    && (method.getName().startsWith("get") || method.getName().startsWith("is"));
            if (accessor) assertDoesNotThrow(() -> method.invoke(instance), method.toString());
        }

        assertDoesNotThrow(instance::hashCode);
        assertDoesNotThrow(instance::toString);
        assertDoesNotThrow(() -> instance.equals(instance));
        assertDoesNotThrow(() -> instance.equals(null));
    }

    private Object instantiate(Class<?> type) {
        AssertionError failure = new AssertionError("No se pudo construir " + type.getName());
        Constructor<?>[] constructors = type.getDeclaredConstructors();
        if (type.isRecord()) {
            // Los constructores de compatibilidad pueden asignar valores por defecto
            // a componentes nuevos; el contrato completo usa el canónico.
            Class<?>[] components = java.util.Arrays.stream(type.getRecordComponents())
                    .map(java.lang.reflect.RecordComponent::getType).toArray(Class<?>[]::new);
            try {
                constructors = new Constructor<?>[]{type.getDeclaredConstructor(components)};
            } catch (NoSuchMethodException exception) {
                throw new AssertionError("Falta el constructor canónico de " + type.getName(), exception);
            }
        }
        java.util.Arrays.sort(constructors, java.util.Comparator.comparingInt(Constructor::getParameterCount));
        for (Constructor<?> constructor : constructors) {
            try {
                constructor.setAccessible(true);
                Object[] arguments = java.util.Arrays.stream(constructor.getParameterTypes())
                        .map(this::sample)
                        .toArray();
                return constructor.newInstance(arguments);
            } catch (ReflectiveOperationException | RuntimeException exception) {
                Throwable cause = exception instanceof InvocationTargetException invocation
                        ? invocation.getTargetException() : exception;
                failure.addSuppressed(new AssertionError(constructor + ": " + cause, cause));
            }
        }
        throw failure;
    }

    @Test
    void constructionFailureNamesTheClassAndOriginalCause() {
        AssertionError failure = assertThrows(AssertionError.class, () -> instantiate(InvalidContract.class));
        assertTrue(failure.getMessage().contains(InvalidContract.class.getName()));
        assertEquals(1, failure.getSuppressed().length);
        assertTrue(failure.getSuppressed()[0].getMessage().contains("constructor rechazado"));
        assertTrue(failure.getSuppressed()[0].getCause() instanceof IllegalArgumentException);
    }

    @Test
    void anotherSupportedConstructorCanStillValidateTheClass() {
        assertTrue(instantiate(AlternativeContract.class) instanceof AlternativeContract);
    }

    @Test
    void recordAccessorsAreActuallyChecked() throws Exception {
        AssertionError failure = assertThrows(AssertionError.class, () -> exercise(InvalidRecord.class.getName()));
        assertTrue(failure.getMessage().contains("value"));
    }

    @Test
    void recordsWithCompatibilityConstructorsValidateEveryComponent() throws Exception {
        assertDoesNotThrow(() -> exercise(CompatibilityRecord.class.getName()));
        CompatibilityRecord instance = (CompatibilityRecord) instantiate(CompatibilityRecord.class);
        assertEquals("contract-test", instance.value());
        assertTrue(instance.explicit());
    }

    private static class InvalidContract {
        @SuppressWarnings("unused") // Invocado por instantiate() mediante reflexión.
        InvalidContract() { throw new IllegalArgumentException("constructor rechazado"); }
    }

    private static class AlternativeContract {
        @SuppressWarnings("unused") // Invocado por instantiate() mediante reflexión.
        AlternativeContract() { throw new IllegalArgumentException("requiere un valor"); }
        @SuppressWarnings("unused") // Invocado por instantiate() mediante reflexión.
        AlternativeContract(String value) { assertNotNull(value); }
    }

    private record InvalidRecord(String value) {
        public String value() { return "valor incorrecto"; }
    }

    private record CompatibilityRecord(String value, boolean explicit) {
        @SuppressWarnings("unused") // Constructor alternativo del caso de prueba reflectivo.
        CompatibilityRecord(String value) { this(value, false); }
    }

    private Object sample(Class<?> type) {
        if (!type.isPrimitive() && !type.isEnum() && type.getPackageName().startsWith("com.sigaa")) return null;
        if (type == String.class) return "contract-test";
        if (type == boolean.class || type == Boolean.class) return true;
        if (type == byte.class || type == Byte.class) return (byte) 1;
        if (type == short.class || type == Short.class) return (short) 1;
        if (type == int.class || type == Integer.class) return 1;
        if (type == long.class || type == Long.class) return 1L;
        if (type == float.class || type == Float.class) return 1F;
        if (type == double.class || type == Double.class) return 1D;
        if (type == char.class || type == Character.class) return 'A';
        if (type == BigDecimal.class) return BigDecimal.ONE;
        if (type == LocalDate.class) return LocalDate.of(2026, 8, 24);
        if (type == LocalDateTime.class) return LocalDateTime.of(2026, 8, 24, 10, 0);
        if (type == LocalTime.class) return LocalTime.of(10, 0);
        if (type == Instant.class) return Instant.parse("2026-08-24T15:00:00Z");
        if (type == UUID.class) return UUID.fromString("00000000-0000-0000-0000-000000000001");
        if (type == URI.class) return URI.create("https://sigaa.invalid");
        if (type == Optional.class) return Optional.empty();
        if (List.class.isAssignableFrom(type)) return new ArrayList<>();
        if (Set.class.isAssignableFrom(type)) return new HashSet<>();
        if (Map.class.isAssignableFrom(type)) return new HashMap<>();
        if (type.isEnum()) {
            Object[] constants = type.getEnumConstants();
            return constants.length == 0 ? null : constants[0];
        }
        if (type.isArray()) return java.lang.reflect.Array.newInstance(type.componentType(), 0);
        return null;
    }
}
