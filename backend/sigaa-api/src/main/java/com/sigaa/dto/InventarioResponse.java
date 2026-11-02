package com.sigaa.dto;

public record InventarioResponse(
        String id, String codigo, String nombre, String categoria, String ubicacion,
        String unidad, String estado, String fechaAdquisicion, String marcaModelo,
        String serie, String serieInstitucional, String responsable,
        String proveedor, String descripcion, long lockVersion, String tipoAsignacion,
        Long asignadoUsuarioId, Long asignadoUnidadId) {
    public record OpcionAsignacion(Long id, String nombre) {}
}
