package com.sigaa.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "reuniones")
public class Reunion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "lock_version", nullable = false)
    private long lockVersion;

    @Column(nullable = false, length = 30, unique = true)
    private String codigo;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(name = "fecha_iso", length = 20)
    private String fechaIso = "";

    @Column(name = "hora_inicio", nullable = false)
    private LocalTime horaInicio;

    @Column(name = "hora_fin", nullable = false)
    private LocalTime horaFin;

    @Column(nullable = false, length = 300)
    private String titulo;

    @Column(length = 500)
    private String subtitulo = "";

    @Column(columnDefinition = "TEXT")
    private String descripcion = "";

    @Column(columnDefinition = "TEXT")
    private String objetivo = "";

    @Column(name = "tipo_reunion", length = 50)
    private String tipoReunion = "General";

    @Column(length = 200)
    private String area = "";

    @Column(length = 200)
    private String organizador = "";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organizador_usuario_id")
    private Usuario organizadorUsuario;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "reunion_participantes_usuario",
            joinColumns = @JoinColumn(name = "reunion_id"),
            inverseJoinColumns = @JoinColumn(name = "usuario_id"))
    private Set<Usuario> participantesUsuarios = new LinkedHashSet<>();

    // Unidades de coordinación: no amplían el alcance de autorización.
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "reunion_unidades", joinColumns = @JoinColumn(name = "reunion_id"),
            inverseJoinColumns = @JoinColumn(name = "unidad_id"))
    @org.hibernate.annotations.BatchSize(size = 50)
    private Set<Unidad> unidades = new LinkedHashSet<>();

    public Set<Unidad> getUnidades() { return unidades; }
    public void setUnidades(Set<Unidad> value) { unidades = value; }

    @Column(length = 200)
    private String lugar = "";

    @Column(length = 50)
    private String modalidad = "Presencial";

    @Column(name = "enlace_virtual", length = 500)
    private String enlaceVirtual = "";

    @Column(length = 200)
    private String sala = "";

    @Column(name = "unidad_responsable", length = 200)
    private String unidadResponsable = "";

    @Column(length = 200)
    private String organizacion = "";

    @Column(length = 50)
    private String estado = "programada";

    @Column(nullable = false)
    private Integer participantes = 0;

    @Column(name = "participantes_nombres", columnDefinition = "TEXT")
    private String participantesNombres = "[]";

    @Column(columnDefinition = "TEXT")
    private String observaciones = "";

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Reunion() {}

    public Long getId() { return id; }
    public long getLockVersion() { return lockVersion; }
    public void setId(Long id) { this.id = id; }
    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }
    public LocalDate getFecha() { return fecha; }
    public void setFecha(LocalDate fecha) { this.fecha = fecha; }
    public String getFechaIso() { return fechaIso; }
    public void setFechaIso(String fechaIso) { this.fechaIso = fechaIso; }
    public LocalTime getHoraInicio() { return horaInicio; }
    public void setHoraInicio(LocalTime horaInicio) { this.horaInicio = horaInicio; }
    public LocalTime getHoraFin() { return horaFin; }
    public void setHoraFin(LocalTime horaFin) { this.horaFin = horaFin; }
    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }
    public String getSubtitulo() { return subtitulo; }
    public void setSubtitulo(String subtitulo) { this.subtitulo = subtitulo; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public String getObjetivo() { return objetivo; }
    public void setObjetivo(String objetivo) { this.objetivo = objetivo; }
    public String getTipoReunion() { return tipoReunion; }
    public void setTipoReunion(String tipoReunion) { this.tipoReunion = tipoReunion; }
    public String getArea() { return area; }
    public void setArea(String area) { this.area = area; }
    public String getOrganizador() { return organizador; }
    public void setOrganizador(String organizador) { this.organizador = organizador; }
    public Usuario getOrganizadorUsuario() { return organizadorUsuario; }
    public void setOrganizadorUsuario(Usuario organizadorUsuario) { this.organizadorUsuario = organizadorUsuario; }
    public Set<Usuario> getParticipantesUsuarios() { return participantesUsuarios; }
    public void setParticipantesUsuarios(Set<Usuario> participantesUsuarios) { this.participantesUsuarios = participantesUsuarios; }
    public String getLugar() { return lugar; }
    public void setLugar(String lugar) { this.lugar = lugar; }
    public String getModalidad() { return modalidad; }
    public void setModalidad(String modalidad) { this.modalidad = modalidad; }
    public String getEnlaceVirtual() { return enlaceVirtual; }
    public void setEnlaceVirtual(String enlaceVirtual) { this.enlaceVirtual = enlaceVirtual; }
    public String getSala() { return sala; }
    public void setSala(String sala) { this.sala = sala; }
    public String getUnidadResponsable() { return unidadResponsable; }
    public void setUnidadResponsable(String unidadResponsable) { this.unidadResponsable = unidadResponsable; }
    public String getOrganizacion() { return organizacion; }
    public void setOrganizacion(String organizacion) { this.organizacion = organizacion; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public Integer getParticipantes() { return participantes; }
    public void setParticipantes(Integer participantes) { this.participantes = participantes; }
    public String getParticipantesNombres() { return participantesNombres; }
    public void setParticipantesNombres(String participantesNombres) { this.participantesNombres = participantesNombres; }
    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String observaciones) { this.observaciones = observaciones; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
