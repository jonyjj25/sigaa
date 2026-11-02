package com.sigaa.model;

import jakarta.persistence.*;

@Entity
@Table(name = "tarea_checklist")
public class TareaChecklist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tarea_id", nullable = false)
    private Long tareaId;

    @Column(nullable = false, length = 500)
    private String texto;

    @Column(nullable = false)
    private Boolean completado = false;

    public TareaChecklist() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTareaId() { return tareaId; }
    public void setTareaId(Long tareaId) { this.tareaId = tareaId; }
    public String getTexto() { return texto; }
    public void setTexto(String texto) { this.texto = texto; }
    public Boolean getCompletado() { return completado; }
    public void setCompletado(Boolean completado) { this.completado = completado; }
}
