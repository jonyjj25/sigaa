# Revisión de código y limpieza conservadora de SIGAA

Fecha: 26 de octubre de 2026.

## Alcance y método

Se revisaron las referencias del código propio y se ejecutaron compilación, análisis de TypeScript, lint y pruebas existentes. La limpieza se limitó a elementos con evidencia de falta de uso. No se eliminaron archivos, tablas, registros ni migraciones; tampoco se cambiaron permisos, roles, reglas de negocio o diseño visual.

Inventario del código analizado:

| Área | Archivos |
| --- | ---: |
| Aplicación Angular/Ionic, TypeScript | 79 |
| Pruebas del frontend | 34 |
| Plantillas HTML de la aplicación | 30 |
| SCSS de componentes y páginas | 35 |
| Código Java de aplicación | 326 |
| Archivos Java de pruebas y utilidades de prueba | 87 |

Se comprobaron imports estáticos, imports dinámicos de rutas, referencias entre símbolos TypeScript, plantillas, estilos compartidos, tipos Java e imports Java. Los resultados estáticos se contrastaron con anotaciones, ciclo de vida y pruebas antes de retirar código.

Los 79 archivos TypeScript de aplicación pertenecen al grafo de imports iniciado en `main.ts`. No se detectaron HTML o SCSS aislados de las referencias revisadas. Esto no implica que todos los selectores CSS sean imprescindibles: las clases dinámicas, los estados y el responsive requieren comprobación visual.

Las instrucciones de `C:/Users/KEICY/Documents/GitHub/AGENTS.md` prohíben acceder a «archivos de producción» y establecen: «NO conectarse automáticamente a ninguna base de datos real». Por ello no se inspeccionaron archivos de entorno, configuración privada, datos institucionales ni archivos de usuarios. Las pruebas de integración utilizaron PostgreSQL temporal y datos ficticios. Se excluyó `ProductionProfileSecurityIntegrationTest`, porque carga el perfil de producción.

## Limpieza aplicada

| Área | Elementos retirados | Evidencia y comportamiento conservado |
| --- | --- | --- |
| Notas | Temporizador, métodos para iniciar/detener renovación automática y llamadas inertes de parada | No existían llamadas al inicio del temporizador. La interfaz indica expresamente una reserva de 10 minutos sin renovación automática. Se conservan reservar, liberar, guardar y controlar conflictos. |
| Notas | Contadores `totalReservados`, `totalOcupados` y `anuladas` | No tenían consumidores en código ni plantillas. |
| Documentos | 19 propiedades antiguas para gráficos, import del módulo ApexCharts, tipos de gráficos e import de color asociado | El HTML actual no contiene esos gráficos. Se conservan listado, carpetas, permisos y archivos. ApexCharts sigue disponible para los componentes que sí lo utilizan. |
| Notificaciones de Configuración | Estados y handlers antiguos para formularios de canales/eventos y configuración global que no tenían controles en la plantilla | Se contrastó el template inline completo. Se conservan el canal Sistema, sus activaciones, los tipos visibles, preferencias, programación, plantillas e historial existentes. Los endpoints backend permanecen disponibles. |
| Seguridad de Configuración | Handler `updatePolicy` sin consumidor | La pantalla continúa usando `togglePolicy` y `updateNativePolicy`, con su mecanismo actual de guardado y errores. |
| Servicios frontend | `setModuleState`, `getReuniones`, `getInventario` y `getActividadesPorMes` sin consumidores | Se conservan las llamadas utilizadas por las pantallas y los endpoints backend. |
| Tareas | Signal `importFileInput` sin consumidores | La importación CSV continúa usando el input creado por `importarTareas`. |
| Modelos frontend | Ocho interfaces/tipos sin referencias | Se retiraron `ActividadesMensualesData`, `ResumenData` e `InventarioItem` del modelo de Dashboard; `AsistenteRegistro`; `Breadcrumb`; `DocumentoEnCarpeta`, `DocumentoVersion` y `PermisoNivel`. El `InventarioItem` utilizado por Inventario se conserva en su módulo. |
| Colores compartidos | Constante `INSTITUTIONAL_INFO_BACKGROUND` sin referencias | Se conserva `INSTITUTIONAL_BLUE` y la configuración global de tema. |
| Backend | Import no usado de `StandardCharsets` en `NotaPdfFallback` y bloque comentado de imports en `GlobalExceptionHandler` | Sin cambios en generación de documentos ni respuestas HTTP. |

Se activaron `noUnusedLocals` y `noUnusedParameters` en `frontend/tsconfig.json` para detectar futuras incorporaciones de variables, imports y parámetros sin uso. Estas comprobaciones no detectan por sí solas todos los exports públicos ni las referencias desde templates; se complementaron con análisis de símbolos y referencias.

Los comentarios que documentan reglas, contratos y motivos de implementación se conservaron: cumplen una función de mantenimiento. No se sustituyó una revisión de comportamiento por el simple borrado de comentarios o líneas vacías.

## Archivos conservados para confirmar una posible eliminación

Ninguno de los siguientes archivos fue eliminado.

| Archivo | Hallazgo | Clasificación |
| --- | --- | --- |
| `backend/sigaa-api/src/main/java/com/sigaa/model/enums/CategoriaParametro.java` | Sin referencias estáticas en aplicación o pruebas. El modelo vigente usa cadenas. | Candidato a eliminación. |
| `backend/sigaa-api/src/main/java/com/sigaa/model/enums/EstadoParametro.java` | Sin referencias estáticas en aplicación o pruebas. | Candidato a eliminación. |
| `backend/sigaa-api/src/main/java/com/sigaa/model/enums/TipoDatoParametro.java` | Sin referencias estáticas en aplicación o pruebas. | Candidato a eliminación. |
| `backend/sigaa-api/src/main/java/com/sigaa/model/enums/NotificationChannelType.java` | Sin referencias estáticas en aplicación o pruebas. | Candidato a eliminación. |
| `backend/sigaa-api/src/main/java/com/sigaa/model/enums/NotificationEventType.java` | Sin referencias estáticas en aplicación o pruebas. | Candidato a eliminación. |
| `backend/sigaa-api/src/main/java/com/sigaa/model/enums/NotificationFrequency.java` | Sin referencias estáticas en aplicación o pruebas. La configuración vigente utiliza cadenas. | Candidato a eliminación. |
| `backend/sigaa-api/src/main/java/com/sigaa/model/enums/NotificationPriority.java` | Sin referencias estáticas en aplicación o pruebas. | Candidato a eliminación. |
| `backend/sigaa-api/src/main/java/com/sigaa/model/enums/NotificationStatus.java` | Sin referencias estáticas en aplicación o pruebas. | Candidato a eliminación. |
| `package-lock.json` de la raíz | No existe `package.json` en esa ubicación; el lock no contiene paquetes ni dependencias. CI utiliza `frontend/package-lock.json`. | Candidato a eliminación. |
| `backend/sigaa-api/src/main/java/com/sigaa/repository/CarpetaDocumentoRepository.java` | No se encontraron inyecciones o consumidores explícitos. Spring Data sí lo registra como repositorio. | Requiere confirmar su necesidad dentro de la gestión de carpetas; no debe considerarse inactivo solo por carecer de llamadas explícitas. |

Antes de eliminar los candidatos debe confirmarse que no exista un consumidor externo, integración por reflexión o necesidad de compatibilidad fuera del código inspeccionado. Las pruebas ejecutadas los conservaron; no constituyen una prueba de compilación con esos archivos eliminados.

## Elementos que se conservaron por tener función

- Controladores, configuraciones, servicios programados y repositorios descubiertos automáticamente por Spring.
- Entidades, DTO, accesores y callbacks utilizados por JPA, Jackson o reflexión.
- `onKeydown` y `warnBeforeUnload`, conectados mediante `HostListener`.
- `ionViewDidLeave`, invocado por Ionic, e `intercept`, invocado por la cadena HTTP de Angular.
- Rutas lazy, estilos compartidos, imágenes institucionales y recursos del PDF de Actas.
- Los seis scripts de mantenimiento de `frontend/scripts`, referenciados desde los scripts npm.
- Pruebas, migraciones, wrappers de compilación y archivos propios de Android/iOS. Su falta de referencias desde una pantalla no los convierte en archivos prescindibles.
- Archivos generados de compilación y pruebas, diferenciados del código fuente. No se realizó una limpieza física de directorios.

## Fallos detectados al ejecutar la suite completa

La primera ejecución backend registró 1181 casos: 7 fallos de aserción, 15 errores y 3 omisiones. Se identificaron estas causas:

1. `CorrespondenciaAuthorizationTest` todavía esperaba privilegios del rol Administrador retirado. Se cambió la cuenta de la prueba de éxito a Superadmin y se añadió una prueba que rechaza al Administrador retirado.
2. `ReportesArchivosIntegrationTest` utilizaba Administrador para modificar programaciones. El backend lo rechazaba correctamente antes de ejecutar las pruebas de archivos. Se usó Superadmin en las pruebas de éxito y rollback, y se añadió una prueba que confirma 403 para carga/eliminación con el rol retirado incluso acompañándolo de `Reporte:Configurar`.
3. `LeadershipPermissionsTest` todavía esperaba copiar automáticamente permisos de Director a Subdirector. Se ajustó a la política vigente: solo cambia el rol solicitado; Subdirector y Secretaría conservan sus permisos.
4. `ParticipantePolicyTest` confundía el nombre histórico Administrador con el único rol administrativo vigente. Se mantiene la exclusión de Superadmin como participante y se comprueba que el nombre retirado no concede privilegios administrativos. No se modificó la política de participación.
5. La caché de contextos Spring conservaba suficientes pools para agotar las conexiones del PostgreSQL temporal y provocar 14 errores de inicialización en `NotaSeguimientoIntegrationTest`. Se limitó la caché a cuatro contextos únicamente en el perfil Maven de validación aislada. No se cambió el pool de la aplicación ni PostgreSQL real.

También se reforzaron tres pruebas de notificaciones para comprobar explícitamente el estado resultante tras una respuesta lenta, una sesión restringida y la recuperación de visibilidad. Ya contenían comprobaciones HTTP; se añadieron expectativas sobre el estado y desaparecieron los avisos de Jasmine por ausencia de expectativas propias.

No se reintrodujeron privilegios ni se modificaron las clases de autorización para hacer pasar las pruebas.

## Validación

| Comprobación | Resultado |
| --- | --- |
| Compilación Java y pruebas | Correcta en `target/security-build`, sin modificar las clases observadas por el backend local. |
| Suite backend tras las correcciones | 1184 casos: 1181 aprobados, 0 fallos, 0 errores y 3 omitidos por conversor externo de documentos no configurado. |
| Cobertura backend | Líneas 69,81%; ramas 47,91%; métodos 79,93%. |
| TypeScript, con comprobaciones de no uso | Correcto, incluyendo las fuentes de pruebas. |
| Compilación frontend de desarrollo | Correcta. La última ejecución se completó con dos trabajadores tras repetir un cierre nativo de la herramienta en el entorno restringido. |
| Lint frontend | Correcto. |
| Suite frontend y cobertura | 286 pruebas aprobadas. Líneas 57,15%; sentencias 52,89%; funciones 40,88%; ramas 37,91%. Se cumplen los umbrales actuales del proyecto. |
| Eliminación de archivos | Ninguna. |

Los artefactos de comprobación quedan en `backend/sigaa-api/target/code-review-*` y `frontend/.angular/code-review/`, dentro de ubicaciones ignoradas por Git. Los datos y archivos utilizados por las pruebas son ficticios.

## Hallazgos y verificaciones pendientes

1. `ConfigDataService` conserva 23 recuperaciones mediante `catchError` que devuelven listas vacías, resúmenes en cero u otros valores por defecto. Algunas pueden ser alternativas funcionales previstas; otras, como los resúmenes y facetas de auditoría, pueden ocultar un error temporal como ausencia de datos. Requieren una corrección coordinada con sus pantallas y pruebas de recuperación; no se eliminaron indiscriminadamente.
2. Permanecen capturas silenciosas de errores en operaciones auxiliares, auditoría y limpieza de recursos. No todas representan un fallo: cerrar un stream durante una limpieza y ocultar un fallo de auditoría requieren tratamientos distintos. Revisar especialmente los handlers de auditoría de organizaciones, unidades y usuarios, y el almacenamiento de reportes.
3. El compilador informa usos de API obsoleta en generación de documentos y algunas pruebas, además de operaciones genéricas no comprobadas en una prueba de contratos. No impiden compilar; actualizar la generación exige validar el documento renderizado antes de modificarla.
4. Queda pendiente ejecutar las tres pruebas de renderizado con un conversor de documentos configurado, comprobar manualmente los flujos de usuario y verificar APK/iOS. Esta revisión no genera ni instala una nueva aplicación móvil.
5. La compilación revisada es de desarrollo; no se inspeccionó ni cambió configuración privada de producción. La revisión estática tampoco valida credenciales, servicios externos reales o el despliegue institucional.
6. Las coberturas medidas no alcanzan el 100%. Una compilación y una suite aprobadas respaldan los flujos cubiertos, pero no permiten afirmar que todas las rutas, condiciones y funciones hayan sido verificadas manualmente.

## Archivos modificados

- `backend/sigaa-api/pom.xml`
- `backend/sigaa-api/src/main/java/com/sigaa/config/GlobalExceptionHandler.java`
- `backend/sigaa-api/src/main/java/com/sigaa/service/NotaPdfFallback.java`
- `backend/sigaa-api/src/test/java/com/sigaa/controller/CorrespondenciaAuthorizationTest.java`
- `backend/sigaa-api/src/test/java/com/sigaa/controller/ReportesArchivosIntegrationTest.java`
- `backend/sigaa-api/src/test/java/com/sigaa/service/LeadershipPermissionsTest.java`
- `backend/sigaa-api/src/test/java/com/sigaa/service/ParticipantePolicyTest.java`
- `frontend/tsconfig.json`
- `frontend/src/app/models/dashboard.models.ts`
- `frontend/src/app/pages/actas/actas.models.ts`
- `frontend/src/app/pages/config/models/config.models.ts`
- `frontend/src/app/pages/config/notifications/notification-list.component.ts`
- `frontend/src/app/pages/config/security/security.component.ts`
- `frontend/src/app/pages/config/services/config-data.service.ts`
- `frontend/src/app/pages/documentos/documentos.models.ts`
- `frontend/src/app/pages/documentos/documentos.page.ts`
- `frontend/src/app/pages/notas/notas.page.ts`
- `frontend/src/app/pages/tareas/tareas.page.ts`
- `frontend/src/app/services/dashboard-notifications.spec.ts`
- `frontend/src/app/services/dashboard.service.ts`
- `frontend/src/app/shared/ui-colors.ts`
- Este informe.

No se hicieron commits ni push.
