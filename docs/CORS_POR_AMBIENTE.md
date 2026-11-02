# CORS por ambiente

CORS se configura exclusivamente en el backend mediante `app.cors.*`. Se carga al
arrancar; cambiarlo requiere reiniciar la instancia. No depende del administrador
conectado ni de las políticas o listas de orígenes, métodos y encabezados en BD.

## Propiedades

| Propiedad | Variable de despliegue | Valor base |
| --- | --- | --- |
| `app.cors.allowed-origins` | `CORS_ALLOWED_ORIGINS` | Vacío: rechaza acceso cross-origin |
| `app.cors.allowed-methods` | `CORS_ALLOWED_METHODS` | `GET,HEAD,POST,PUT,PATCH,DELETE,OPTIONS` |
| `app.cors.allowed-headers` | `CORS_ALLOWED_HEADERS` | `Authorization,Content-Type,Accept,Origin,X-Requested-With` |
| `app.cors.exposed-headers` | `CORS_EXPOSED_HEADERS` | `Content-Disposition` |
| `app.cors.allow-credentials` | `CORS_ALLOW_CREDENTIALS` | `true` |
| `app.cors.max-age` | `CORS_MAX_AGE` | `600` segundos |

Las listas se separan por comas. Usar orígenes completos (esquema, host y puerto si
corresponde), sin rutas ni barra final. No se admiten patrones de subdominios ni el
origen literal `null`. Spring rechaza al arrancar `*` combinado con credenciales.
No se permite TRACE/CONNECT. No hay interruptor administrativo para desactivar CORS.

## Ambientes

- **Desarrollo:** activar el perfil `dev` (por ejemplo, argumento de aplicación
  `--spring.profiles.active=dev`). `application-dev.properties` admite Angular/Ionic
  locales en puertos 4200/8100 (HTTP/HTTPS), `http://localhost`, `https://localhost`
  para WebView Android y `capacitor://localhost` para iOS. Para LAN o live reload,
  configurar `CORS_ALLOWED_ORIGINS` con la lista completa de orígenes requeridos;
  no se autorizan IP de desarrollo compartidas automáticamente.
  Activar este perfil también al usar el proxy Angular: puede reenviar el encabezado
  `Origin` del navegador hacia la API.
- **Pruebas:** `src/test/resources/application-test.properties` define una lista
  determinista de orígenes ficticios y locales. Las pruebas se ejecutan con la
  fixture PostgreSQL efímera existente y sin importar archivos de entorno reales.
- **Producción:** con el perfil de producción, definir `CORS_ALLOWED_ORIGINS` en
  el mecanismo de configuración del despliegue con los orígenes HTTPS reales del
  frontend y los orígenes móviles utilizados. La base `application.properties`
  permite configurar las seis propiedades. No se modifica el archivo de producción
  ni el entorno instalado como parte de este cambio. Sin una lista explícita,
  el valor base rechaza cross-origin; las solicitudes del mismo origen siguen funcionando.

Se conserva la variable `CORS_ALLOWED_ORIGINS`. La propiedad de servidor anterior
`app.cors-allowed-origins` se admite como respaldo de compatibilidad cuando no se
define esa variable ni se sobrescribe `app.cors.allowed-origins`. Este respaldo
también proviene del servidor, nunca de BD. Conviene usar la clave nueva en futuros
despliegues. Los valores técnicos pueden sobrescribirse con las fuentes habituales
de Spring por ambiente, sin escribir en BD.

## Compatibilidad y límites

Se conserva `withCredentials` del cliente y la autenticación actual por cookie
`sigaa_access` y Bearer. CORS permite enviar credenciales únicamente desde los
orígenes configurados. No cambia atributos Secure/SameSite, permisos, JWT o 2FA.
El navegador continúa aplicando su propia política de cookies; esta etapa no la
modifica. Validar la aplicación instalada en dispositivos reales antes del despliegue.

El proxy web del mismo origen continúa funcionando. Las pruebas HTTP cubren tanto
preflight como solicitudes con una cookie de sesión ficticia válida desde web y
orígenes Capacitor. Permitir un origen no sustituye la autenticación ni los permisos.

## Configuración histórica y API

Los registros existentes y las migraciones históricas se conservan. Las consultas
administrativas GET siguen disponibles como información histórica; no representan
la configuración efectiva. La pantalla ya no presenta controles CORS.

Se rechaza con **403**, también para administradores:

- `PUT /api/security/policies/{id}` cuando corresponde a CORS, incluidas variantes
  de nombres históricos sin acentos y alias antiguos.
- `POST /api/security/allowed-origins`.
- `PUT /api/security/allowed-origins/{id}`.
- `DELETE /api/security/allowed-origins/{id}`.

La recarga de caché sigue atendiendo otras políticas y no cambia CORS. Los endpoints
de métodos y encabezados eran de solo lectura y conservan ese comportamiento.

## Ejecución aislada de las pruebas backend

Desde `backend/sigaa-api`, indicar la carpeta de binarios PostgreSQL locales en
`sigaa.test.postgres.bin` y activar el perfil de validación:

```powershell
.\mvnw.cmd -o '-Dsigaa.security.validation=true' '-Dsigaa.test.postgres.bin=<carpeta de binarios PostgreSQL>' '-Dtest=CorsPropertiesTest,CorsSecurityIntegrationTest,ConfigurationAuthorizationIntegrationTests' test
```

El perfil `isolated-security-validation` activa la extensión efímera, deshabilita
la importación de archivos de entorno y establece una URL de respaldo no operativa.
Si falta la fixture o sus binarios, las pruebas deben fallar antes de utilizar una
base existente. La extensión sustituye esa URL por la de su cluster nuevo de prueba.

## Archivos de esta etapa

| Archivo desde la raíz del repositorio | Cambio |
| --- | --- |
| `backend/sigaa-api/src/main/java/com/sigaa/config/SecurityConfig.java` | Fuente CORS de Spring ligada solo a propiedades |
| `backend/sigaa-api/src/main/java/com/sigaa/config/CorsProperties.java` | Propiedades tipadas y validación al arrancar |
| `backend/sigaa-api/src/main/java/com/sigaa/controller/SecurityPolicyController.java` | Bloqueo de escrituras CORS; consultas históricas conservadas |
| `backend/sigaa-api/src/main/java/com/sigaa/service/SecurityPolicyService.java` | Retiro de accesores y repositorios CORS del servicio de políticas |
| `backend/sigaa-api/src/main/resources/application.properties` | Valores base, variables de despliegue y compatibilidad de clave anterior |
| `backend/sigaa-api/src/main/resources/application-dev.properties` | Orígenes locales de desarrollo web/móvil |
| `backend/sigaa-api/src/test/resources/application-test.properties` | Configuración CORS determinista de pruebas |
| `backend/sigaa-api/pom.xml` | Aislamiento obligatorio en el perfil existente de validación |
| `backend/sigaa-api/src/test/java/com/sigaa/config/CorsPropertiesTest.java` | Binding, credenciales y rechazo de configuración inválida |
| `backend/sigaa-api/src/test/java/com/sigaa/CorsSecurityIntegrationTest.java` | HTTP, cookies, web/móvil, API bloqueada y BD sin efecto en CORS |
| `backend/sigaa-api/src/test/java/com/sigaa/ConfigurationAuthorizationIntegrationTests.java` | La política CORS deja de ser editable; demás políticas conservan su contrato |
| `frontend/src/app/pages/config/security/security.component.ts` | Eliminación de controles, alias y avisos de edición CORS |
| `frontend/src/app/pages/config/security/security.component.spec.ts` | Ausencia de controles CORS y conservación del guardado de otras políticas |
| `frontend/src/app/pages/config/services/config-data.service.ts` | Retiro de helpers sin uso que escribían orígenes CORS |
| `frontend/src/app/pages/config/services/config-data.service.spec.ts` | Actualización del contrato del servicio |
| `docs/CORS_POR_AMBIENTE.md` | Configuración, compatibilidad y ejecución aislada |

## Validación de esta etapa

- Backend compilado; 88 pruebas relacionadas aprobadas: 13 de propiedades, 54 de
  autorización/configuración y 21 de integración CORS. Los resultados válidos se
  obtuvieron usando la fixture PostgreSQL efímera y el perfil de aislamiento reforzado.
- Frontend: 44 pruebas relacionadas aprobadas, lint correcto y compilación de
  desarrollo correcta.
- La compilación de producción se detiene por el presupuesto del SCSS de Reuniones:
  32.10 kB frente al límite de 32 kB (103 bytes de exceso). No se modifica ese módulo
  ni se amplía su presupuesto en esta etapa.
- Cubiertos: origen permitido/rechazado, métodos y encabezados, OPTIONS sin sesión,
  cookie válida desde web y orígenes móviles, autenticación obligatoria, rechazo de
  escrituras administrativas CORS, conservación de registros ficticios históricos
  y ausencia de efecto de BD/recarga de caché sobre CORS.
- No se certifica una prueba manual en teléfono/tablet ni un despliegue de producción.
  Antes de reiniciar, elegir el perfil de desarrollo o configurar los orígenes del
  despliegue. Las demás políticas de seguridad quedan fuera de esta etapa.
