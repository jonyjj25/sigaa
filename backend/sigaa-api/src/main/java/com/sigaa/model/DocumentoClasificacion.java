package com.sigaa.model;

import java.util.Locale;

public enum DocumentoClasificacion {
    PUBLICO,
    USO_INTERNO,
    RESTRINGIDO,
    CONFIDENCIAL,
    DATOS_SENSIBLES;

    /** Correspondencia de acceso; no reescribe la clasificación histórica almacenada. */
    public DocumentoClasificacion politica() {
        return switch (this) {
            case USO_INTERNO -> RESTRINGIDO;
            case DATOS_SENSIBLES -> CONFIDENCIAL;
            default -> this;
        };
    }

    public static DocumentoClasificacion from(Object value) {
        if (value == null) throw new IllegalArgumentException("Selecciona una clasificación válida");
        String normalized = String.valueOf(value).trim().toUpperCase(Locale.ROOT)
                .replace('Á', 'A').replace('É', 'E').replace('Í', 'I')
                .replace('Ó', 'O').replace('Ú', 'U')
                .replace(' ', '_').replace('-', '_');
        return DocumentoClasificacion.valueOf(normalized);
    }
}
