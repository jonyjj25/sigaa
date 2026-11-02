# 500 local: reinicio con clases incompletas

El registro adjunto muestra un reinicio DevTools tras 487 eliminaciones de clases.
Spring falló al crear AdminPasswordResetService porque no podía cargar su record RequestView.
Los 500 del proxy Angular son coherentes con esa caída del backend; no confirman una sesión rechazada.

En la revisión posterior ya existían ambos records compilados. No se cambiaron DTO, consultas,
permisos ni autenticación. Para prevenir la colisión con validaciones, el perfil Maven activado
por -Dsigaa.security.validation=true genera sus clases en target/security-build.
El backend normal sigue usando target/classes y conserva DevTools.

La prueba puntual reproduce el fallo con RequestView ausente y comprueba la carga correcta
con los records presentes. No inicia Spring ni utiliza .env, PostgreSQL o datos reales.
Resultado: compilación y dos pruebas aprobadas, sin fallos ni omisiones. Se comprobó que
el archivo RequestView.class del directorio utilizado por el backend no cambió durante la validación.

## Recuperación local

Detener la instancia fallida en su terminal con Ctrl+C. Desde backend/sigaa-api ejecutar:

    .\mvnw.cmd spring-boot:run

Esperar Started SigaaApiApplication y recargar Angular. No limpiar ni recompilar target/classes
mientras una instancia lo utiliza; para la batería aislada usar el perfil de validación.

No se reinició automáticamente la instancia con su configuración real. AGENTS.md prohíbe
conectar automáticamente una base real. Ese arranque debe realizarlo el operador o autorizarse
expresamente; la revisión no leyó .env ni accedió a datos institucionales.

Los errores 401 siguen limpiando la sesión y los 500 temporales conservan el estado para recuperarse.
Si el nuevo arranque falla, diagnosticar la primera causa nueva sin compartir credenciales ni datos personales.
