# Limpieza adicional autorizada y aplicada — 31 de octubre de 2026

El usuario aprobó expresamente este listado y se aplicó sin ampliar su alcance. Se guardó una copia local de recuperación en `.runtime/cleanup-rollback`. La revisión automática había bloqueado la operación masiva inicial.

Se buscaron estos nombres en las fuentes y pruebas del backend. Cada uno aparece únicamente en su declaración. No son endpoints, callbacks, tareas programadas, métodos de entidades/DTO ni implementaciones de interfaces. Se propone retirar únicamente los métodos siguientes, conservando sus clases y el resto de su contenido. El análisis estático no prueba la ausencia de integraciones externas por reflexión.

| Archivo bajo backend/sigaa-api/src/main/java/com/sigaa/service | Métodos propuestos |
| --- | --- |
| `InstitutionalRoles.java` | `leadership` |
| `NotificationChannelService.java` | `countActivos` |
| `NotificationHistoryService.java` | `registrarEnvio` |
| `ParametroService.java` | `obtenerValorPorClave` |
| `PasswordHistoryService.java` | `isExpired` |
| `PermissionService.java` | `findActive`, `bulkAssign`, `copyRolePermissions`, `resetRolePermissions` |
| `ReunionService.java` | `listarPorRangoFechas` |
| `UsuarioService.java` | `obtenerPorCorreo` |
| `SecurityPolicyService.java` | `getSessionInactivityMs`, `getMaxSessionDurationMs`, `isAutoRenewToken`, `isAutoCloseInactivity`, `isIpRangeRestrictionEnabled`, `isLocationRestrictionEnabled`, `isWhitelistActive`, `isBlacklistActive`, `getMaxRequestSizeMB`, `isAuditCriticalEventsEnabled`, `isAuditAccessEnabled`, `isAuditDataChangesEnabled`, `getAuditRetentionDays`, `getAuditPolicyLevel` |

También se propone retirar dos imágenes sin referencias en código, plantillas, configuración o estilos: `frontend/src/assets/icon/favicon.png` y `frontend/src/assets/images/alerta-amber-logo.png`. Los logos usados por inicio de sesión y navegación se conservan.

Los cinco respaldos de `output/db-backups` y los documentos de usuario se conservan en su ubicación; no forman parte de esta solicitud de borrado. Las tablas y datos de PostgreSQL tampoco se modifican.

El resultado de las comprobaciones está documentado en `LIMPIEZA_2026-10-31.md`.
