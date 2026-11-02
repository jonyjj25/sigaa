# Inventario de cambios pendientes al 25 de agosto de 2026

## Referencia

- Rama: `codex/android-apk-sigaa`
- Commit base: `ba66e5129cde1c469e539b93e9685495bd761c18`
- Archivos rastreados modificados: 100
- Archivos nuevos no rastreados: 79
- Estado: trabajo local pendiente de consolidar; no publicado por esta fase

## Distribución

| Área | Modificados | Nuevos | Clasificación principal |
| --- | ---: | ---: | --- |
| Backend | 33 | 51 | Seguridad, API, solicitudes, reportes, respaldos, migraciones y pruebas |
| Frontend y Android | 61 | 17 | UX, configuración, solicitudes, servicios, pruebas y estilos |
| GitHub Actions | 0 | 3 | Integración continua, seguridad y Dependabot |
| Operación | 0 | 7 | Monitoreo, alertas, respaldos y preparación de producción |
| Documentación | 4 archivos raíz | 1 | Despliegue, APK, operación y recuperación |
| Configuración raíz | 3 | 0 | Variables de ejemplo, exclusiones y Docker Compose |

Los totales de la tabla separan archivos raíz de los directorios principales;
no representan commits independientes.

## Bloques funcionales detectados

### Backend

- Autenticación, control de acceso, límites de login y carga de archivos.
- Auditoría, respaldo y observabilidad.
- Reportes y reportes programados.
- Solicitudes, tipos, etapas, aprobaciones, comentarios, adjuntos, historial y
  SLA.
- Tareas, reuniones, asistencia, dashboard e inventario.
- Migraciones Flyway V75 a V79.
- Pruebas de contratos, servicios, controladores, observabilidad y permisos.

### Frontend

- Pantallas de configuración y administración.
- Solicitudes y configuración de tipos de solicitud.
- Reportes, documentos, inventario, tareas, reuniones, grupos y asistencia.
- Correcciones de modo oscuro, tokens, componentes comunes y accesibilidad.
- Transporte autenticado, recuperación de contraseña y pruebas frontend.
- Configuración Android y entorno de API.

### Operación y entrega

- Workflows de CI y seguridad.
- Dependabot.
- Prometheus, Alertmanager y reglas de alerta.
- Scripts de clave de respaldo y preparación de producción.
- Docker Compose de producción y documentación RPO/RTO.

## Clasificación para consolidación

| Bloque | Prioridad | Riesgo de mezcla | Acción antes de integrar |
| --- | --- | --- | --- |
| Sesiones, JWT y contraseñas | Crítica | Muy alto | Revisión de seguridad y regresión de autenticación |
| Permisos, auditoría y archivos | Crítica | Muy alto | Matriz rol-registro y pruebas negativas |
| Migraciones V75-V79 | Crítica | Muy alto | Validación desde base limpia y respaldo previo |
| Solicitudes y aprobaciones | Alta | Alto | Flujo completo y compatibilidad de permisos |
| Respaldos y observabilidad | Alta | Alto | Restauración aislada y alerta de prueba |
| Reportes y tareas | Alta | Medio | Validación funcional y autorización |
| UX, modo oscuro y accesibilidad | Media | Medio | Revisión por rutas y temas |
| Android/APK | Alta | Alto | Configuración release, HTTPS y firma |
| CI y dependencias | Alta | Medio | Ejecución completa y protección de rama |
| Documentación | Media | Bajo | Alinear con comportamiento verificado |

## Reglas de preservación

- No descartar, resetear ni sobrescribir los cambios inventariados.
- No reordenar las migraciones ya numeradas.
- No ejecutar migraciones sobre datos persistentes durante la Fase 0.
- No eliminar ni modificar usuarios registrados.
- No incorporar `.env`, claves, certificados, cargas o respaldos a Git.
- No asumir que un archivo nuevo está terminado solo porque compila.

## Orden sugerido para la consolidación posterior

1. Sesiones y revocación de acceso.
2. Permisos documentales y protección de archivos.
3. Contraseñas y recuperación de cuentas.
4. Auditoría institucional.
5. Migraciones y compatibilidad del modelo.
6. Solicitudes y flujos administrativos.
7. Operación, respaldo y monitoreo.
8. Frontend, accesibilidad y Android release.

