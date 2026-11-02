# Roles y alcance institucional

Revisión y cambios de código: 20 de octubre de 2026.

Aplicación local autorizada: 20 de octubre de 2026. V119 aplicada con Flyway,
checksum validado y ninguna migración pendiente hasta esa versión. Backend
compilado en su salida habitual con DevTools activo. Angular responde HTTP 200;
las API protegidas responden 401 sin sesión y el preflight del login responde 200.

## Resultado

| Rol | Permisos y alcance |
| --- | --- |
| Superadmin | Administración global y acceso a los recursos operativos. Recibe el catálogo vigente de permisos activos, aunque un permiso nuevo no figure en su matriz histórica. |
| Director / Directora | Permisos operativos institucionales y alcance global sobre esos recursos. No recibe el alias técnico ni los privilegios de Superadmin. |
| Subdirector / Subdirectora | Permisos funcionales operativos, combinados con una relación real con el recurso. El cargo por sí solo no concede alcance global. |
| Jefe de Unidad, Secretaría, Miembro, Invitado | Se conserva la matriz de permisos existente y sus reglas específicas. Se corrigen controles de lectura ausentes o que concedían alcance por descarte. |
| Administrador | Retirado. La migración lo deja inactivo y conserva su registro, permisos históricos y asignaciones. El backend lo rechaza como fuente de autorización y en nuevas asignaciones, incluso si una fila antigua siguiera activa. |

Los permisos funcionales no sustituyen la autorización por recurso. Los estados,
validaciones, restricciones de participación y demás reglas de negocio siguen aplicándose.

## Causas encontradas

- `InstitutionalRoles.operational` concedía el mismo alcance global a Administrador,
  Director y Subdirector. Sus consumidores incluían Reuniones, Tareas, Asistencia,
  Grupos y Correspondencia.
- Editar permisos de Dirección sincronizaba automáticamente los de Subdirección,
  y viceversa. Ahora se administran de forma independiente.
- Actas solo restringía a Miembro sin gestión. Un cargo distinto podía superar el
  control por descarte. Ahora reconoce excepciones explícitas y aplica la política
  de participación a los demás usuarios; Subdirección tiene una consulta propia de ámbito.
- Superadmin dependía de los permisos guardados en su matriz y tenía restricciones
  de custodia documental incompatibles con el acceso total solicitado. Se aplica
  la nueva excepción explícita para Superadmin y Dirección.
- Inventario y Reportes no disponían de un control contextual para Subdirección.
- `Reporte.generadoPor` es texto editable y no puede demostrar propiedad. Se añade
  una identidad de creador asignada exclusivamente por el servidor.
- La vista JPA de roles usa `ROW_NUMBER()` como identificador. Una prueba reprodujo
  una lectura incorrecta por reutilización de entidades al cambiar la numeración
  dentro del mismo contexto. Autenticación y `/auth/me` ahora leen una proyección
  escalar de nombres desde la misma vista, sin depender de esa identidad inestable.
- Los GET de Tareas no exigían el permiso funcional de lectura. Ahora exigen
  `Tareas:Ver`, además de aplicar el alcance existente.

## Alcance de Subdirección

| Módulo | Relación utilizada |
| --- | --- |
| Actas | Participación según la política existente, responsable identificado por correo/nombre o creador de la sesión QR vinculada. No se infiere una unidad que el modelo no registra. |
| Reuniones | Organizador identificado por FK, participante normalizado o unidad de una membresía activa. |
| Tareas | Responsable identificado por FK, participante normalizado o unidad de una membresía activa. Al crear/importar, el servidor incluye al creador Subdirector entre los participantes. |
| Asistencia / QR | Creador de la sesión o acceso mediante el Acta vinculada. `Configuracion QR:Administrar` no concede alcance global a Subdirección. |
| Grupos | Creador, miembro activo o unidad de una membresía activa. Se mantiene la prohibición de eliminar grupos que todavía tengan miembros y la protección de la asignación de permisos. |
| Documentos | Propiedad, carpeta, acceso compartido, área y aprobación según la política documental existente. Una compartición de lectura no se transforma automáticamente en edición. |
| Notas / Memos | Creador, seguimiento y firmante cuando el modelo lo contempla. Se reutiliza CorrespondenciaAccessService. |
| Inventario | Asignación directa a la persona o a una unidad de su membresía activa. Se comprueba antes de editar/eliminar y después de cambiar la asignación. |
| Reportes | Creador persistido por el servidor. La generación reutiliza las políticas de Reuniones, Actas, Tareas y Asistencia para limitar la instantánea. También requiere permiso de consulta del módulo fuente. |

Las listas, consultas por ID, modificaciones, eliminaciones y descargas de Reportes
aplican el mismo ámbito. Cambiar el texto «generado por» no cambia el propietario.
Las programaciones institucionales quedan para Superadmin o Dirección con
`Reporte:Configurar`; Subdirección no obtiene un proceso programado con alcance global.

## Permisos y configuración técnica

V119 incorpora permisos activos de los módulos operativos a Director y Subdirector,
sin duplicar las filas de `rol_permisos`. Usa una lista explícita de módulos:
Reuniones, Tareas, Actas, Documentos, Grupos, Notas, Memos, Inventario, Reporte y
Configuracion QR; de Panel solo incorpora Ver. No modifica las matrices de los
otros cargos ni introduce roles o permisos nuevos.

Superadmin conserva `ROLE_superadmin` y el alias de compatibilidad
`ROLE_administrador` para endpoints existentes. El rol retirado Administrador no
recibe ese alias. No se utiliza caché de autorizaciones.

El control técnico de CORS, JWT, red y máximos absolutos continúa en las propiedades
del backend y en el despliegue. La administración total de la aplicación no concede
permisos del sistema operativo ni reabre las API técnicas bloqueadas en las etapas
anteriores. Director/Subdirector no acceden a administración de roles ni a la
pantalla global de Seguridad.

## Migración y cuentas

Migración nueva: `backend/sigaa-api/src/main/resources/db/migration/V119__separate_institutional_role_scope.sql`.

- Desactiva Administrador sin borrar usuarios, asociaciones, permisos ni auditoría.
- No convierte ninguna cuenta en Superadmin.
- Conserva el acceso que una cuenta tenga mediante otros roles vigentes.
- Añade `reportes.creado_por_usuario_id`, con FK y `ON DELETE SET NULL`, para no
  impedir la eliminación de una cuenta ni borrar el reporte por esa relación.
- No reconstruye la autoría de reportes históricos. Sin identidad fiable, esos
  reportes quedan fuera del ámbito de Subdirección y disponibles para los gestores
  globales según sus permisos.
- No modifica migraciones anteriores.

**V119 ya fue aplicada a la base local configurada, tras autorización expresa.**
Antes se verificó V118 y que la única migración pendiente fuera V119. Después se
validaron el historial/checksum, Administrador inactivo, Superadmin activo y la
columna de autoría de Reportes. Solo se consultaron metadatos de migraciones,
estructura y roles; no se listaron usuarios ni se inspeccionaron datos personales.
No se confirmó el número ni la identidad de cuentas afectadas. La migración no
elimina cuentas, no modifica sus asignaciones y no promueve usuarios a Superadmin.

## Archivos de esta intervención

Rutas relativas a la raíz del repositorio. Los cambios anteriores que ya estaban
en el árbol de trabajo se conservaron.

Backend, bajo `backend/sigaa-api/src/main/java/com/sigaa/`:

- `service/InstitutionalRoles.java`
- `service/UsuarioDetailsService.java`
- `service/RoleAdministrationService.java`
- `service/UsuarioService.java`
- `service/ActaAccessService.java`
- `service/AsistenciaAccessService.java`
- `service/TareaAccessService.java`
- `service/ReunionService.java`
- `service/DocumentoAccessService.java`
- `service/GrupoService.java`
- `service/InventarioService.java`
- `service/ReporteGenerationService.java`
- `service/ParticipantePolicy.java`
- `controller/AuthController.java`
- `controller/RoleController.java`
- `controller/TareaController.java`
- `controller/ReunionController.java`
- `controller/DocumentoController.java`
- `controller/ReporteController.java`
- `controller/ReporteProgramacionController.java`
- `repository/ActaRepository.java`
- `repository/ReunionRepository.java`
- `repository/UsuarioRepository.java`
- `repository/VwUsuarioRolRepository.java`
- `model/Reporte.java`

Frontend, bajo `frontend/src/app/`:

- `shared/institutional-roles.ts`
- `services/auth.service.ts`
- `services/role.guard.ts`
- `services/dashboard.service.ts`
- `dashboard/dashboard.page.ts`
- `pages/config/roles/role-permissions.component.ts`
- `pages/config/guia/guia.component.ts`
- `pages/reuniones/reuniones.page.ts`
- `pages/documentos/documentos.page.ts`
- `pages/documentos/documentos.page.html`
- `pages/reportes/reportes.page.ts`
- `pages/reportes/reportes.page.html`
- `pages/tareas/tareas.page.html`

Pruebas backend nuevas o ajustadas, bajo `backend/sigaa-api/src/test/java/com/sigaa/`:

- `InstitutionalScopeIntegrationTest.java` (nuevo, 16 casos)
- `RoleAdministrationIntegrationTests.java`
- `RoleEndpointAuthorizationIntegrationTests.java`
- `controller/ActaResourceAuthorizationTest.java`
- `controller/AsistenciaResourceAuthorizationTest.java`
- `service/InstitutionalRoleAssignmentTest.java`
- `service/InstitutionalRolesTest.java`
- `service/UsuarioDetailsServiceTest.java`
- `service/DocumentoAccessServiceTest.java`
- `service/CorrespondenciaAccessServiceTest.java`

Pruebas frontend ajustadas, bajo `frontend/src/app/`:

- `shared/institutional-roles.spec.ts`
- `services/auth.service.spec.ts`
- `services/guards.spec.ts`
- `services/dashboard.service.spec.ts`
- `dashboard/dashboard.page.spec.ts`
- `pages/reportes/reportes.page.spec.ts`
- `pages/documentos/documentos.page.spec.ts`

También se añadió V119 y este informe; el README registra su aplicación local.
No se modificaron estilos, dependencias ni configuración de ambientes. La base
local recibió únicamente V119. No se hicieron commits ni push.

## Validación

- Backend: compilación correcta. Primera suite final: 450 pruebas sin fallos.
  Verificación del último ajuste de membresías: 26 pruebas sin fallos, incluida
  una nueva. Total: **451 pruebas distintas**, sin sumar dos veces las repetidas.
- PostgreSQL efímero: migraciones hasta V119 correctas y datos exclusivamente ficticios.
- Frontend: **105 pruebas correctas**, incluidos autenticación, guards, menús,
  Dashboard, Documentos, Tareas y Reportes.
- Compilación frontend de desarrollo final: correcta.
- Lint frontend final: correcto.
- Compilación frontend de producción: bloqueada por el presupuesto preexistente
  de `reuniones.page.scss`: 32.10 kB frente al máximo de 32.00 kB, excedido en
  103 bytes. No se aumentó el presupuesto ni se modificó el SCSS.

## Límites y pendientes

- V119 ya está aplicada localmente. Producción y otros ambientes deben realizar
  su propio despliegue y verificación. No se verificó una sesión autenticada real
  ni dispositivos móviles; las comprobaciones HTTP locales se hicieron sin cookies.
- Los reportes antiguos no tienen autoría confiable para otorgar acceso individual.
  No deben atribuirse usando texto editable.
- La entidad de la vista de roles conserva su identificador histórico; autenticación
  ya no lo usa para decidir permisos. Cualquier consumidor futuro debe utilizar la
  proyección o una identidad realmente estable.
- Jefe de Unidad conserva sus excepciones actuales de alcance, incluyendo la de
  Actas. No se redefinió esa regla de negocio.
- El acceso total de Superadmin y el acceso operativo global de Dirección son
  excepciones explícitas de la nueva política y permiten acceso documental amplio.
  Los demás usuarios continúan sujetos a clasificación, propiedad y compartición.
