package com.sigaa.controller;

import com.sigaa.dto.ParametroRequest;
import com.sigaa.dto.ParametroResponse;
import com.sigaa.model.ParameterHistory;
import com.sigaa.service.ParametroService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
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
@RequestMapping("/api/parametros")
@PreAuthorize("hasRole('administrador')")
public class ParametroController {

    private final ParametroService parametroService;

    public ParametroController(ParametroService parametroService) {
        this.parametroService = parametroService;
    }

    @GetMapping
    public List<ParametroResponse> listar() {
        return parametroService.listarTodos();
    }

    @GetMapping("/activos")
    public List<ParametroResponse> listarActivos() {
        return parametroService.listarActivos();
    }

    @GetMapping("/visibles")
    public List<ParametroResponse> listarVisibles() {
        return parametroService.listarVisibles();
    }

    @GetMapping("/grupo/{grupo}")
    public List<ParametroResponse> listarPorGrupo(@PathVariable String grupo) {
        return parametroService.listarPorGrupo(grupo);
    }

    @GetMapping("/categoria/{categoria}")
    public List<ParametroResponse> listarPorCategoria(@PathVariable String categoria) {
        return parametroService.listarPorCategoria(categoria);
    }

    @GetMapping("/estado/{estado}")
    public List<ParametroResponse> listarPorEstado(@PathVariable String estado) {
        return parametroService.listarPorEstado(estado);
    }

    @GetMapping("/tipo/{tipo}")
    public List<ParametroResponse> listarPorTipo(@PathVariable String tipo) {
        return parametroService.listarPorTipo(tipo);
    }

    @GetMapping("/modulo/{moduloId}")
    public List<ParametroResponse> listarPorModulo(@PathVariable Long moduloId) {
        return parametroService.listarPorModulo(moduloId);
    }

    @GetMapping("/reinicio")
    public List<ParametroResponse> listarRequierenReinicio() {
        return parametroService.listarRequierenReinicio();
    }

    @GetMapping("/buscar")
    public List<ParametroResponse> buscar(@RequestParam String q) {
        return parametroService.buscar(q);
    }

    @GetMapping("/grupos")
    public List<String> listarGrupos() {
        return parametroService.listarGrupos();
    }

    @GetMapping("/categorias")
    public List<String> listarCategorias() {
        return parametroService.listarCategorias();
    }

    @GetMapping("/tipos")
    public List<String> listarTipos() {
        return parametroService.listarTipos();
    }

    @GetMapping("/stats")
    public Map<String, Object> estadisticas() {
        return parametroService.getEstadisticas();
    }

    @GetMapping("/historial")
    public List<ParameterHistory> historialCompleto() {
        return parametroService.obtenerHistorialCompleto();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ParametroResponse> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(parametroService.obtenerPorId(id));
    }

    @GetMapping("/clave/{clave}")
    public ResponseEntity<ParametroResponse> obtenerPorClave(@PathVariable String clave) {
        return ResponseEntity.ok(parametroService.obtenerPorClave(clave));
    }

    @GetMapping("/{id}/historial")
    public List<ParameterHistory> historial(@PathVariable Long id) {
        return parametroService.obtenerHistorial(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<ParametroResponse> crear(@Valid @RequestBody ParametroRequest request) {
        ParametroResponse resp = parametroService.crear(request);
        return ResponseEntity.status(201).body(resp);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<ParametroResponse> actualizar(@PathVariable Long id, @RequestBody ParametroRequest request) {
        return ResponseEntity.ok(parametroService.actualizar(id, request));
    }

    @PatchMapping("/{id}/valor")
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<ParametroResponse> actualizarValor(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String valor = body.get("valor");
        return ResponseEntity.ok(parametroService.actualizarValor(id, valor));
    }

    @PatchMapping("/{id}/toggle")
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<ParametroResponse> toggleActivo(@PathVariable Long id) {
        return ResponseEntity.ok(parametroService.toggleActivo(id));
    }

    @PatchMapping("/{id}/visible")
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<ParametroResponse> toggleVisible(@PathVariable Long id) {
        return ResponseEntity.ok(parametroService.toggleVisible(id));
    }

    @PatchMapping("/{id}/restaurar")
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<ParametroResponse> restaurarDefecto(@PathVariable Long id) {
        return ResponseEntity.ok(parametroService.restaurarDefecto(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        parametroService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
