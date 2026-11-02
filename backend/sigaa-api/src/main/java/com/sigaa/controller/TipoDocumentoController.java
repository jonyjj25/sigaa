package com.sigaa.controller;

import com.sigaa.dto.TipoDocumentoRequest;
import com.sigaa.dto.TipoDocumentoResponse;
import com.sigaa.service.TipoDocumentoService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/catalogos")
public class TipoDocumentoController {

    private final TipoDocumentoService service;

    public TipoDocumentoController(TipoDocumentoService service) {
        this.service = service;
    }

    @GetMapping("/{tipo}")
    public List<TipoDocumentoResponse> listar(@PathVariable String tipo) {
        if ("tipos-documento".equals(tipo)) {
            return service.listarActivos();
        }
        return List.of();
    }

    @GetMapping("/{tipo}/todos")
    @PreAuthorize("hasRole('administrador')")
    public List<TipoDocumentoResponse> listarTodos(@PathVariable String tipo) {
        if ("tipos-documento".equals(tipo)) {
            return service.listarTodos();
        }
        return List.of();
    }

    @GetMapping("/{tipo}/buscar")
    @PreAuthorize("hasRole('administrador')")
    public List<TipoDocumentoResponse> buscar(@PathVariable String tipo, @RequestParam String q) {
        if ("tipos-documento".equals(tipo)) {
            return service.buscar(q);
        }
        return List.of();
    }

    @GetMapping("/{tipo}/{id}")
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<TipoDocumentoResponse> obtenerPorId(@PathVariable String tipo, @PathVariable Long id) {
        if ("tipos-documento".equals(tipo)) {
            return ResponseEntity.ok(service.obtenerPorId(id));
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping("/{tipo}")
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<TipoDocumentoResponse> crear(@PathVariable String tipo,
                                                       @Valid @RequestBody TipoDocumentoRequest request) {
        if ("tipos-documento".equals(tipo)) {
            return ResponseEntity.status(201).body(service.crear(request));
        }
        return ResponseEntity.badRequest().build();
    }

    @PutMapping("/{tipo}/{id}")
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<TipoDocumentoResponse> actualizar(@PathVariable String tipo,
                                                            @PathVariable Long id,
                                                            @Valid @RequestBody TipoDocumentoRequest request) {
        if ("tipos-documento".equals(tipo)) {
            return ResponseEntity.ok(service.actualizar(id, request));
        }
        return ResponseEntity.badRequest().build();
    }

    @PatchMapping("/{tipo}/{id}/toggle")
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<TipoDocumentoResponse> toggleActivo(@PathVariable String tipo, @PathVariable Long id) {
        if ("tipos-documento".equals(tipo)) {
            return ResponseEntity.ok(service.toggleActivo(id));
        }
        return ResponseEntity.badRequest().build();
    }

    @DeleteMapping("/{tipo}/{id}")
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<Void> eliminar(@PathVariable String tipo, @PathVariable Long id) {
        if ("tipos-documento".equals(tipo)) {
            service.eliminar(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.badRequest().build();
    }
}
