package com.sigaa.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "asistencia_registros")
public class AsistenciaRegistro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(name = "hora_entrada", nullable = false)
    private LocalTime horaEntrada;

    @Column(name = "hora_salida")
    private LocalTime horaSalida;

    @Column(name = "lat_entrada", nullable = false)
    private double latEntrada;

    @Column(name = "lng_entrada", nullable = false)
    private double lngEntrada;

    @Column(name = "lat_salida")
    private Double latSalida;

    @Column(name = "lng_salida")
    private Double lngSalida;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sede_id")
    private Ubicacion sede;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sesion_id")
    private AsistenciaSesion sesion;

    @Column(length = 10, nullable = false)
    private String metodo = "GPS";

    @Column(length = 20, nullable = false)
    private String estado = "presente";

    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @PrePersist
    public void prePersist() {
        if (this.fecha == null) this.fecha = LocalDate.now();
        if (this.creadoEn == null) this.creadoEn = LocalDateTime.now();
    }

    public AsistenciaRegistro() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }
    public LocalDate getFecha() { return fecha; }
    public void setFecha(LocalDate fecha) { this.fecha = fecha; }
    public LocalTime getHoraEntrada() { return horaEntrada; }
    public void setHoraEntrada(LocalTime horaEntrada) { this.horaEntrada = horaEntrada; }
    public LocalTime getHoraSalida() { return horaSalida; }
    public void setHoraSalida(LocalTime horaSalida) { this.horaSalida = horaSalida; }
    public double getLatEntrada() { return latEntrada; }
    public void setLatEntrada(double latEntrada) { this.latEntrada = latEntrada; }
    public double getLngEntrada() { return lngEntrada; }
    public void setLngEntrada(double lngEntrada) { this.lngEntrada = lngEntrada; }
    public Double getLatSalida() { return latSalida; }
    public void setLatSalida(Double latSalida) { this.latSalida = latSalida; }
    public Double getLngSalida() { return lngSalida; }
    public void setLngSalida(Double lngSalida) { this.lngSalida = lngSalida; }
    public Ubicacion getSede() { return sede; }
    public void setSede(Ubicacion sede) { this.sede = sede; }
    public AsistenciaSesion getSesion() { return sesion; }
    public void setSesion(AsistenciaSesion sesion) { this.sesion = sesion; }
    public String getMetodo() { return metodo; }
    public void setMetodo(String metodo) { this.metodo = metodo; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public LocalDateTime getCreadoEn() { return creadoEn; }
    public void setCreadoEn(LocalDateTime creadoEn) { this.creadoEn = creadoEn; }
}
