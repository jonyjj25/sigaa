package com.sigaa.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "tareas")
public class Tarea {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "lock_version", nullable = false)
    private long lockVersion;

    @Column(name = "acta_id")
    private Long actaId;
    @Column(name = "acuerdo_clave", length = 64)
    private String acuerdoClave;
    public Long getActaId() { return actaId; }
    public void setActaId(Long value) { actaId = value; }
    public String getAcuerdoClave() { return acuerdoClave; }
    public void setAcuerdoClave(String value) { acuerdoClave = value; }

    @Column(nullable = false, length = 300)
    private String titulo;

    @Column(columnDefinition = "TEXT")
    private String descripcion = "";

    @Column(length = 200)
    private String proyecto = "";

    @Column(nullable = false, unique = true, length = 50)
    private String codigo;

    @Column(length = 100)
    private String tipo = "Configuración";

    @Column(length = 200)
    private String asignado = "";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asignado_usuario_id")
    private Usuario asignadoUsuario;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "tarea_participantes_usuario",
        joinColumns = @JoinColumn(name = "tarea_id"),
        inverseJoinColumns = @JoinColumn(name = "usuario_id"))
    private Set<Usuario> participantesUsuarios = new LinkedHashSet<>();

    @Column(length = 200)
    private String cargo = "";

    @Column(length = 500)
    private String unidades = "";

    @Column(length = 50)
    private String prioridad = "media";

    @Column(name = "fecha_limite")
    private LocalDate fechaLimite;

    @Column(name = "hora_limite")
    private LocalTime horaLimite;

    @Column(name = "fecha_creacion")
    private LocalDate fechaCreacion;

    @Column(length = 50)
    private String estado = "por_hacer";

    @Column(name = "creado_por", length = 200)
    private String creadoPor = "";

    @Column(nullable = false)
    private Integer progreso = 0;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    public Tarea() {}

    public Long getId() { return id; }
    public long getLockVersion() { return lockVersion; }
    public void setId(Long id) { this.id = id; }
    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public String getProyecto() { return proyecto; }
    public void setProyecto(String proyecto) { this.proyecto = proyecto; }
    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }
    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
    public String getAsignado() { return asignado; }
    public void setAsignado(String asignado) { this.asignado = asignado; }
    public Usuario getAsignadoUsuario() { return asignadoUsuario; }
    public void setAsignadoUsuario(Usuario asignadoUsuario) { this.asignadoUsuario = asignadoUsuario; }
    public Set<Usuario> getParticipantesUsuarios() { return participantesUsuarios; }
    public void setParticipantesUsuarios(Set<Usuario> participantesUsuarios) {
        this.participantesUsuarios = participantesUsuarios == null ? new LinkedHashSet<>() : new LinkedHashSet<>(participantesUsuarios);
    }
    public String getCargo() { return cargo; }
    public void setCargo(String cargo) { this.cargo = cargo; }
    public String getUnidades() { return unidades; }
    public void setUnidades(String unidades) { this.unidades = unidades; }
    public String getPrioridad() { return prioridad; }
    public void setPrioridad(String prioridad) { this.prioridad = prioridad; }
    public LocalDate getFechaLimite() { return fechaLimite; }
    public void setFechaLimite(LocalDate fechaLimite) { this.fechaLimite = fechaLimite; }
    public LocalTime getHoraLimite() { return horaLimite; }
    public void setHoraLimite(LocalTime horaLimite) { this.horaLimite = horaLimite; }
    public LocalDate getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(LocalDate fechaCreacion) { this.fechaCreacion = fechaCreacion; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public String getCreadoPor() { return creadoPor; }
    public void setCreadoPor(String creadoPor) { this.creadoPor = creadoPor; }
    public Integer getProgreso() { return progreso; }
    public void setProgreso(Integer progreso) { this.progreso = progreso; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
