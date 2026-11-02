# Fase 1: sesiones, autorización documental y clasificación

Fecha: 25 de agosto de 2026  
Alcance: puntos 1 a 3  
Estado: implementado en código local; V80 aplicada y verificada en PostgreSQL local

## Garantías de preservación

- No se eliminó, creó, bloqueó ni modificó ningún usuario registrado.
- V80 se aplicó de forma aditiva en la base PostgreSQL local autorizada.
- El conteo de usuarios permaneció en 3 y no se alteraron sus registros.
- No se copiaron documentos, usuarios, `.env` o credenciales.
- Las pruebas nuevas usan objetos en memoria y no cargan Spring ni una base de datos.
- El checkpoint de la Fase 0 permanece disponible para recuperar el código anterior.

## Punto 1: sesiones y usuarios

Se implementó:

- rechazo de JWT cuando la cuenta está inactiva o bloqueada;
- invalidación mediante `tokenVersion` al bloquear, desbloquear, activar,
  desactivar, cambiar contraseña o cerrar sesiones;
- cierre forzado de sesión sin bloquear innecesariamente la cuenta;
- comprobación de cuenta activa y no bloqueada al finalizar 2FA;
- rechazo de tokens temporales `2fa_pending` en endpoints protegidos;
- propagación de los estados `disabled` y `accountLocked` a Spring Security.

El desbloqueo temporal existente se conserva. Cuando el periodo termina, el
usuario obtiene un token nuevo al iniciar sesión; los tokens anteriores siguen
revocados.

## Punto 2: autorización documental por registro

La autorización ya no depende solamente del permiso general del módulo. Para
cada documento se consideran:

- administrador custodio;
- propietario técnico;
- carpeta personal o compartida;
- acceso individual vigente;
- unidad institucional;
- aprobador pendiente;
- clasificación del documento;
- permiso de lectura o edición.

Los controles se aplican a listado, edición, carga, descarga, vista previa,
aprobación y compartición. La API devuelve además `puedeEditar`,
`puedeDescargar` y `puedeAdministrarAcceso` para que la interfaz no ofrezca
acciones que el backend rechazará.

El cuadro de compartir dejó de contener personas de ejemplo. Ahora consulta
usuarios registrados, permite lectura o edición, fecha de expiración y retiro
de acceso. No crea usuarios ni datos personales nuevos.

## Punto 3: clasificación

Niveles incorporados:

| Nivel | Acceso ordinario |
| --- | --- |
| `PUBLICO` | Usuarios autenticados con permiso `Documentos:Ver` |
| `USO_INTERNO` | Propietario, unidad, carpeta o acceso concedido |
| `RESTRINGIDO` | Propietario, unidad, carpeta o acceso concedido |
| `CONFIDENCIAL` | Propietario, administrador, carpeta explícita, aprobador o acceso individual |
| `DATOS_SENSIBLES` | Igual que confidencial, sin apertura automática por unidad |

Todos los documentos nuevos quedan en `USO_INTERNO` salvo selección expresa.
Después de crear el documento, solamente un administrador puede cambiar su
clasificación. Esto evita que una edición ordinaria reduzca accidentalmente la
protección.

## Migración V80

`V80__secure_document_access_and_classification.sql` es aditiva:

- añade `propietario_id` sin eliminar el propietario textual;
- añade `clasificacion` con valor predeterminado `USO_INTERNO`;
- intenta vincular documentos existentes por correo o nombre normalizado;
- crea accesos individuales con permiso y expiración;
- crea índices y restricciones de valores válidos;
- no contiene `DELETE`, `TRUNCATE` ni inserciones de usuarios o documentos.

Los documentos existentes sin coincidencia de propietario no se eliminan. Por
seguridad quedan limitados a administradores/custodios hasta que se les asigne
un propietario mediante un procedimiento autorizado.

### Resultado en la base local

- Flyway registró V80 correctamente el 25 de agosto de 2026 a las 08:08.
- Migraciones fallidas: 0.
- Usuarios antes y después de la verificación: 3.
- Documentos conservados: 1.
- Clasificación asignada: `USO_INTERNO`.
- Documentos con propietario técnico pendiente: 1.
- Tabla `documento_usuarios`, restricción de clasificación y cuatro índices:
  presentes y validados.
- Accesos individuales creados automáticamente: 0.

Se generó un respaldo transitorio y `pg_restore` validó sus 700 entradas. Como
el entorno todavía no tiene `BACKUP_ENCRYPTION_KEY_BASE64`, la copia sin cifrar
se eliminó después de la comprobación y no se incorporó al repositorio. Antes
de futuras migraciones debe configurarse la clave institucional y custodiarse
fuera del servidor.

## Validaciones realizadas

- Compilación backend: correcta.
- Build web: correcto.
- Pruebas backend aisladas: 149 aprobadas, 0 fallos.
- Pruebas específicas nuevas: sesión, 2FA pendiente, cambio de contraseña y
  autorización documental.
- Pruebas frontend: 25 aprobadas, 0 fallos.
- Lint frontend: aprobado.
- Auditoría UX: aprobada.

No se ejecutaron las pruebas `@SpringBootTest` porque el perfil disponible
apunta a una base PostgreSQL persistente. La validación completa desde cero de
V1 a V80 sigue reservada para un PostgreSQL desechable y autorizado.

## Despliegue seguro posterior

1. Custodiar o cifrar el respaldo local fuera del equipo de aplicación.
2. Preparar PostgreSQL aislado desde cero y ejecutar V1 a V80.
3. Probar propietario resuelto, propietario pendiente y cada clasificación.
4. Asignar mediante procedimiento autorizado el propietario del documento cuyo
   `propietario_id` quedó nulo.
5. Ejecutar matriz de roles y pruebas negativas completas.
6. Repetir V80 primero en preproducción mediante ventana aprobada.
7. Verificar conteos antes y después; deben conservarse usuarios y documentos.
8. Autorizar producción por separado.

La versión anterior de la aplicación puede leer el esquema después de V80
porque las columnas y tablas son adicionales. No debe revertirse la base
eliminando columnas si ya existen accesos registrados; el rollback operativo
consiste en restaurar la aplicación anterior y conservar el esquema aditivo.
