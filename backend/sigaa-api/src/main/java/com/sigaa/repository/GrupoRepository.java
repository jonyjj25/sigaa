package com.sigaa.repository;
import com.sigaa.model.Grupo; import java.util.*; import org.springframework.data.jpa.repository.*; import org.springframework.data.repository.query.Param;
public interface GrupoRepository extends JpaRepository<Grupo,Long> {
 boolean existsByUnidadIdAndNombreIgnoreCase(Long unidadId,String nombre);
 List<Grupo> findByUnidadIdIn(Collection<Long> unidadIds);
 @Query("select distinct g from Grupo g left join g.miembros m where lower(g.nombre) like lower(concat('%',:q,'%')) or lower(coalesce(g.descripcion,'')) like lower(concat('%',:q,'%')) or lower(g.unidad.nombre) like lower(concat('%',:q,'%')) or lower(g.tipo) like lower(concat('%',:q,'%')) or lower(coalesce(m.usuario.nombre,'')) like lower(concat('%',:q,'%'))") List<Grupo> buscar(@Param("q") String q);
 @Query("select distinct g from Grupo g join g.miembros m where m.usuario.id=:usuarioId and m.activo=true") List<Grupo> findVisiblesParaUsuario(@Param("usuarioId") Long usuarioId);
}
