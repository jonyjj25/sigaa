package com.sigaa.dto;

import jakarta.validation.constraints.NotNull;
import java.util.Set;

public class RolePermissionsRequest {

    @NotNull(message = "Debe indicar la lista de permisos")
    private Set<@NotNull Long> permisoIds;

    public Set<Long> getPermisoIds() { return permisoIds; }
    public void setPermisoIds(Set<Long> permisoIds) { this.permisoIds = permisoIds; }
}
