package com.sigaa.repository;

import com.sigaa.model.Nota;
import java.util.List;
import java.util.Optional;
import java.time.LocalDate;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotaRepository extends JpaRepository<Nota, Long> {
    String NUMERO_LISTADO = "lower(case when numero.consecutivo is not null then "
        + "concat('OAT-', function('lpad', cast(numero.consecutivo as String), 3, '0'), '-', cast(numero.anio as String)) "
        + "when function('regexp_like', numero.numero, '(?i)^(N-|OAT-)?[0-9]{1,3}(-[0-9]{4})?$') = true then "
        + "concat('OAT-', function('lpad', cast(function('regexp_replace', numero.numero, "
        + "'(?i)^(N-|OAT-)?([0-9]{1,3})(-[0-9]{4})?$', '\\2') as String), 3, '0'), '-', cast(year(nota.fechaOficial) as String)) "
        + "else numero.numero end)";
    String RESPONSABLE_LISTADO = "lower(concat(coalesce(responsable.nombre, ''), ' ', "
        + "case when responsable.segundoNombre is null or trim(responsable.segundoNombre) = '' then '' "
        + "else concat(responsable.segundoNombre, ' ') end, coalesce(responsable.apellido, '')))";
    String FILTROS_LISTADO = "from Nota nota join nota.numero numero join nota.creadoPor creador "
        + "join nota.seguimientoUsuario responsable left join nota.firmante firmante where "
        + "(:admin = true or creador.id = :usuarioId or responsable.id = :usuarioId or firmante.id = :usuarioId) "
        + "and (:query = '' or lower(numero.numero) like :queryPatron escape '\\' or " + NUMERO_LISTADO + " like :queryPatron escape '\\' "
        + "or lower(nota.asunto) like :queryPatron escape '\\' or lower(nota.destino) like :queryPatron escape '\\' "
        + "or lower(nota.destinatarioNombre) like :queryPatron escape '\\' "
        + "or lower(concat(coalesce(creador.nombre, ''), ' ', case when creador.segundoNombre is null or trim(creador.segundoNombre) = '' "
        + "then '' else concat(creador.segundoNombre, ' ') end, coalesce(creador.apellido, ''))) like :queryPatron escape '\\' "
        + "or " + RESPONSABLE_LISTADO + " like :queryPatron escape '\\') "
        + "and (:numero = '' or lower(numero.numero) like :numeroPatron escape '\\' or " + NUMERO_LISTADO + " like :numeroPatron escape '\\') "
        + "and (:destinatario = '' or lower(nota.destinatarioNombre) like :destinatarioPatron escape '\\') "
        + "and (:estado = '' or nota.estado = :estado) "
        + "and (cast(:anioDesde as date) is null or nota.fechaOficial >= :anioDesde) "
        + "and (cast(:anioHasta as date) is null or nota.fechaOficial <= :anioHasta) "
        + "and (cast(:fechaDesde as date) is null or nota.fechaOficial >= :fechaDesde) "
        + "and (cast(:fechaHasta as date) is null or nota.fechaOficial <= :fechaHasta) "
        + "and (:responsable = '' or " + RESPONSABLE_LISTADO + " like :responsablePatron escape '\\') ";

    @Query("select nota " + FILTROS_LISTADO + "order by nota.createdAt desc, nota.id desc")
    @EntityGraph(attributePaths = { "numero", "creadoPor", "seguimientoUsuario", "firmante", "institucion",
        "firmadaPor", "cerradaPor", "oficializadaPor", "anuladaPor" })
    List<Nota> listarFiltradas(@Param("query") String query, @Param("queryPatron") String queryPatron,
        @Param("numero") String numero, @Param("numeroPatron") String numeroPatron,
        @Param("destinatario") String destinatario, @Param("destinatarioPatron") String destinatarioPatron,
        @Param("estado") String estado, @Param("anioDesde") LocalDate anioDesde, @Param("anioHasta") LocalDate anioHasta,
        @Param("fechaDesde") LocalDate fechaDesde, @Param("fechaHasta") LocalDate fechaHasta,
        @Param("responsable") String responsable, @Param("responsablePatron") String responsablePatron,
        @Param("usuarioId") Long usuarioId, @Param("admin") boolean admin, Pageable pageable);

    @Query("select nota.estado, count(nota) " + FILTROS_LISTADO + "group by nota.estado")
    List<Object[]> resumirFiltradas(@Param("query") String query, @Param("queryPatron") String queryPatron,
        @Param("numero") String numero, @Param("numeroPatron") String numeroPatron,
        @Param("destinatario") String destinatario, @Param("destinatarioPatron") String destinatarioPatron,
        @Param("estado") String estado, @Param("anioDesde") LocalDate anioDesde, @Param("anioHasta") LocalDate anioHasta,
        @Param("fechaDesde") LocalDate fechaDesde, @Param("fechaHasta") LocalDate fechaHasta,
        @Param("responsable") String responsable, @Param("responsablePatron") String responsablePatron,
        @Param("usuarioId") Long usuarioId, @Param("admin") boolean admin);

    @Query("select nota from Nota nota left join nota.firmante firmante where "
        + "(:admin = true or nota.creadoPor.id = :usuarioId or nota.seguimientoUsuario.id = :usuarioId or firmante.id = :usuarioId) "
        + "and (:query = '' or lower(nota.numero.numero) like lower(concat('%', :query, '%')) "
        + "or lower(nota.asunto) like lower(concat('%', :query, '%')) "
        + "or lower(nota.creadoPor.nombre) like lower(concat('%', :query, '%')) "
        + "or lower(nota.seguimientoUsuario.nombre) like lower(concat('%', :query, '%'))) "
        + "and (:excluirId is null or nota.id <> :excluirId) order by nota.createdAt desc")
    Page<Nota> buscarAutorizadas(@Param("query") String query, @Param("excluirId") Long excluirId,
                                @Param("usuarioId") Long usuarioId, @Param("admin") boolean admin, Pageable pageable);

    List<Nota> findAllByOrderByCreatedAtDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select nota from Nota nota where nota.id = :id")
    Optional<Nota> findByIdForUpdate(@Param("id") Long id);

    @Query("select nota from Nota nota where "
        + "(:query = '' or lower(nota.numero.numero) like lower(concat('%', :query, '%')) "
        + "or lower(nota.asunto) like lower(concat('%', :query, '%')) "
        + "or lower(nota.creadoPor.nombre) like lower(concat('%', :query, '%')) "
        + "or lower(nota.seguimientoUsuario.nombre) like lower(concat('%', :query, '%'))) "
        + "and (:excluirId is null or nota.id <> :excluirId) order by nota.createdAt desc")
    Page<Nota> buscarRelacionables(@Param("query") String query,
                                   @Param("excluirId") Long excluirId,
                                   Pageable pageable);
}
