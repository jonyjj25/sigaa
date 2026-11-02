package com.sigaa.model;

import jakarta.persistence.*;

@Entity
@Table(name = "organization_departments")
public class OrganizationDepartment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;
    @Column(nullable = false, length = 200)
    private String nombre;
    @Column(nullable = false, length = 200)
    private String director = "";
    @Column(name = "director_cargo", nullable = false, length = 200)
    private String directorCargo = "";
    @Column(nullable = false)
    private boolean activo = true;

    public Long getId() { return id; }
    public Organization getOrganization() { return organization; }
    public void setOrganization(Organization value) { organization = value; }
    public String getNombre() { return nombre; }
    public void setNombre(String value) { nombre = value; }
    public String getDirector() { return director; }
    public void setDirector(String value) { director = value; }
    public String getDirectorCargo() { return directorCargo; }
    public void setDirectorCargo(String value) { directorCargo = value; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean value) { activo = value; }
}
