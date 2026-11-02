package com.sigaa.util;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.StringJoiner;

/** Diagnóstico breve sin mensajes de excepción, SQL, rutas ni valores procesados. */
public final class LogDiagnostics {
    private LogDiagnostics() { }

    public static String describe(Throwable failure) {
        StringJoiner diagnostic = new StringJoiner(" <- ");
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable current = failure;
        for (int depth = 0; current != null && depth < 6 && seen.add(current); depth++) {
            String location = "";
            for (StackTraceElement frame : current.getStackTrace()) {
                if (frame.getClassName().startsWith("com.sigaa.")) {
                    location = " at " + frame.getClassName() + "." + frame.getMethodName()
                            + ":" + frame.getLineNumber();
                    break;
                }
            }
            diagnostic.add(current.getClass().getName() + location);
            current = current.getCause();
        }
        if (current != null) diagnostic.add("causas adicionales omitidas");
        return diagnostic.toString();
    }
}
