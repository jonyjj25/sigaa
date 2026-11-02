# Asistencia: autorización de sesiones y QR público

Revisión del hallazgo P0 de acceso por código/ID de sesión. No se inspeccionaron datos institucionales ni se modificaron datos históricos.

## Causa y asociación real

Las consultas de asistentes e invitados aceptaban `Actas:Ver` o permisos QR generales y localizaban la sesión sin comprobar alcance. Las acciones por ID devolvían metadatos/contadores sin autorización por recurso. El directorio de usuarios admitía a cualquier usuario autenticado.

La asociación persistida es `Acta.sesion` → `AsistenciaSesion`. No existe una FK entre Reunión y sesión QR. El frontend selecciona a veces una sesión por título/fecha; esa coincidencia no autoriza el acceso y no se usa en backend. La política de consulta de Reuniones conserva sus participantes normalizados y las unidades dirigidas; no se inventa una asociación con sesiones para reutilizarla donde no hay una relación real.

## Endpoints revisados

| Método y ruta bajo `/api/asistencia` | Clasificación | Datos y protección |
| --- | --- | --- |
| GET `/sesiones/{codigo}` | Público QR | Solo `nombre`, `fecha`, `horaInicio`; únicamente sesión activa, no archivada y vigente. Sin IDs de usuarios, listas, descripciones ni contadores. |
| POST `/invitado` | Público QR | Registra los datos proporcionados por el propio invitado. Responde solo confirmación/error genérico; un registro repetido responde igual que uno nuevo sin insertar otra fila, evitando confirmar la presencia de un correo. No revela roles administrativos. |
| POST `/escanear` | Privado, registro propio | UID debe coincidir con la identidad autenticada antes de buscar datos de terceros. Confirmación con el nombre del propio usuario; no permite registrar a otra persona. |
| GET `/historial` | Privado, historial propio | Fecha, horario, estado, método y ubicación del usuario autenticado. Los parámetros de IDs ajenos no sustituyen su identidad. |
| GET `/usuarios` | Directorio administrativo | Requiere `Usuarios:Ver`. Proyección explícita de ID, nombre y correo; no se usa desde QR público. |
| GET `/sesiones` | Gestión QR | Permiso QR de lectura/administración o administrador. Filtra sesiones por alcance antes de serializar nombre, descripción, fechas, estado y contadores. Incluye el archivo histórico con la misma política. |
| POST `/sesiones` | Gestión QR | Permiso de crear/administrar o administrador. Registra al creador autenticado. |
| GET `/sesiones/{codigo}/asistentes` | Privado | Permiso funcional existente y alcance de sesión. Devuelve registros/invitados con nombres, correos, horarios y estado, sin entidades, roles, permisos, teléfono, cargo o institución. Resumen mínimo de la sesión. |
| GET `/sesiones/{codigo}/invitados` | Privado | Mismo control; proyección de ID, nombre, correo y hora de registro. Se eliminó teléfono, cargo e institución de esta respuesta. |
| DELETE `/sesiones/{id}` | Gestión QR | Permiso de eliminación/administración o administrador y alcance antes de consultar dependencias o eliminar. |
| PATCH `/sesiones/{id}/cerrar` | Gestión QR | Permiso de edición/administración o administrador y alcance antes de modificar o devolver metadatos. |
| PATCH `/sesiones/{id}/archivar` | Gestión QR | Mismos controles por recurso. |
| PATCH `/sesiones/{id}/restaurar` | Gestión QR | Mismos controles por recurso; conserva la restricción de sesiones antiguas. |

## Regla compartida

`ActaAccessService` contiene la regla de consulta que antes estaba en el controlador: participación para miembros sin gestión y las excepciones institucionales existentes. Actas lo reutiliza para consultar, modificar, eliminar y descargar; no se duplicó esa regla en Asistencia.

`AsistenciaAccessService` comprueba autenticación y permite el recurso al creador registrado, a quien pueda ver un acta vinculada, o al gestor explícito (`Configuracion QR:Administrar` o rol operacional institucional). Cada endpoint sigue exigiendo por separado su permiso funcional; el rol Administrador conserva las excepciones que ya tenían los endpoints QR. Los recursos inexistentes o fuera del alcance responden 404 sin detalles.

Las sesiones sin creador ni acta asociada no conceden acceso a usuarios ordinarios. No se reconstruyó propiedad histórica por nombres, fechas o datos no confiables.

La creación/actualización de Actas comprueba también acceso antes de vincular una sesión existente por ID. Esto cierra la vía indirecta de vincular una sesión ajena para leer sus asistentes desde un acta propia. Las nuevas sesiones generadas desde Actas registran al creador en la columna existente.

## Validación

Pruebas HTTP con la cadena de seguridad, `ActaAccessService` y `AsistenciaAccessService` reales y repositorios simulados. Cubren alcance por código/ID, permisos, anonimato, gestores, filtrado antes de contar/leer asistentes, vinculación de sesiones, proyecciones y QR público. Pruebas frontend conservan registro propio, invitado y recuperación del registro pendiente sin consultar directorios o listas privadas.

No se añadieron dependencias, permisos, roles, migraciones ni cambios a la relación de datos de Reuniones.

Resultados de la ejecución final:

- Compilación Maven y 92 pruebas seleccionadas correctas: 37 de Asistencia, 31 de Actas, 15 de archivos privados, 3 de archivado y 6 del filtro de autenticación.
- 201 pruebas frontend correctas, incluidas 5 del registro QR público.
- TypeScript, lint y compilación web de producción correctos.
- No se ejecutó la suite completa con base de datos ni se accedió a datos reales. La matriz de integración se ajustó, pero sus casos con BD no se ejecutaron.
- Sin commits ni push.

## Archivos modificados

Backend:

- [AsistenciaController.java](C:/Users/KEICY/Documents/GitHub/sigaa-keicy/backend/sigaa-api/src/main/java/com/sigaa/controller/AsistenciaController.java)
- [ActaController.java](C:/Users/KEICY/Documents/GitHub/sigaa-keicy/backend/sigaa-api/src/main/java/com/sigaa/controller/ActaController.java)
- [ActaRepository.java](C:/Users/KEICY/Documents/GitHub/sigaa-keicy/backend/sigaa-api/src/main/java/com/sigaa/repository/ActaRepository.java)
- [ActaAccessService.java](C:/Users/KEICY/Documents/GitHub/sigaa-keicy/backend/sigaa-api/src/main/java/com/sigaa/service/ActaAccessService.java)
- [AsistenciaAccessService.java](C:/Users/KEICY/Documents/GitHub/sigaa-keicy/backend/sigaa-api/src/main/java/com/sigaa/service/AsistenciaAccessService.java)

Frontend:

- [asistencia-publica.page.ts](C:/Users/KEICY/Documents/GitHub/sigaa-keicy/frontend/src/app/pages/asistencia-publica/asistencia-publica.page.ts)
- [asistencia.service.ts](C:/Users/KEICY/Documents/GitHub/sigaa-keicy/frontend/src/app/services/asistencia.service.ts)

Pruebas nuevas o ajustadas:

- [AsistenciaResourceAuthorizationTest.java](C:/Users/KEICY/Documents/GitHub/sigaa-keicy/backend/sigaa-api/src/test/java/com/sigaa/controller/AsistenciaResourceAuthorizationTest.java)
- [ActaResourceAuthorizationTest.java](C:/Users/KEICY/Documents/GitHub/sigaa-keicy/backend/sigaa-api/src/test/java/com/sigaa/controller/ActaResourceAuthorizationTest.java)
- [PrivateFileAccessTest.java](C:/Users/KEICY/Documents/GitHub/sigaa-keicy/backend/sigaa-api/src/test/java/com/sigaa/controller/PrivateFileAccessTest.java)
- [AsistenciaControllerArchiveTest.java](C:/Users/KEICY/Documents/GitHub/sigaa-keicy/backend/sigaa-api/src/test/java/com/sigaa/controller/AsistenciaControllerArchiveTest.java)
- [RoleEndpointAuthorizationIntegrationTests.java](C:/Users/KEICY/Documents/GitHub/sigaa-keicy/backend/sigaa-api/src/test/java/com/sigaa/RoleEndpointAuthorizationIntegrationTests.java)
- [asistencia-publica.page.spec.ts](C:/Users/KEICY/Documents/GitHub/sigaa-keicy/frontend/src/app/pages/asistencia-publica/asistencia-publica.page.spec.ts)

Este informe forma parte de los archivos añadidos para documentar la revisión.

## Aspectos que permanecen fuera del alcance

La participación heredada de Actas se identifica por correo o nombre; una mejora posterior puede normalizarla por ID de usuario, como ya hace Reuniones, sin reconstruir identidades históricas a ciegas. Se preservó esa política para no alterar reglas funcionales de Actas.

El alta pública de invitados no verifica la propiedad del correo proporcionado. Esto requiere una decisión funcional separada si se necesita acreditar identidad; no se añadió verificación por correo ni se modificó el sistema de autenticación.
