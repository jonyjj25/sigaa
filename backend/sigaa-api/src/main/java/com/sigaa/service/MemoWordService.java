package com.sigaa.service;

import com.sigaa.model.Memo;
import com.sigaa.model.Usuario;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import org.springframework.stereotype.Service;

/** Reutiliza el motor Word de Notas y su mismo membrete institucional. */
@Service
public class MemoWordService {
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("d 'de' MMMM 'del' yyyy", Locale.forLanguageTag("es-PA"));
    private final NotaWordService word;

    public MemoWordService(NotaWordService word) { this.word = word; }

    public byte[] generar(Memo memo) {
        if (MemoService.ANULADO.equals(memo.getEstado())) throw new IllegalStateException("No se puede descargar un documento definitivo de un memo anulado.");
        return generarVistaPrevia(memo);
    }

    public byte[] generarVistaPrevia(Memo memo) {
        if (memo.getNumero() == null || memo.getCreatedAt() == null) throw new IllegalStateException("El memo no tiene número o fecha de registro.");
        var valores = new LinkedHashMap<String, String>();
        valores.put("FECHA_OFICIAL", "Panamá, " + FECHA.format(memo.getCreatedAt()));
        valores.put("NUMERO_MEMO", memo.getNumero().getNumero());
        valores.put("DESTINO_MEMO", memo.getDestino());
        valores.put("REMITENTE_MEMO", nombre(memo.getCreadoPor()));
        valores.put("ASUNTO_MEMO", memo.getAsunto());
        valores.put("CONTENIDO_NOTA", memo.getDescripcion());
        return word.generarDesdePlantilla("templates/correspondencia/plantilla-memo.docx", valores);
    }

    public static String nombreArchivo(Memo memo, String extension) {
        return NotaWordService.nombreArchivoDocumento("Memo", memo.getNumero().getNumero(), memo.getAsunto(), extension);
    }

    private String nombre(Usuario usuario) {
        if (usuario == null) return "";
        return String.join(" ", usuario.getNombre() == null ? "" : usuario.getNombre(),
            usuario.getSegundoNombre() == null ? "" : usuario.getSegundoNombre(), usuario.getApellido() == null ? "" : usuario.getApellido())
            .trim().replaceAll("\\s+", " ");
    }
}
