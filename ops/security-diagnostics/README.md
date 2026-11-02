# Diagnóstico histórico: token reutilizable después de logout

**Corregido el 1/11/2026.** Logout autentica el Bearer/cookie y revoca todas las
sesiones de la cuenta con un incremento atómico en la BD. La regresión permanente
`RequiredPasswordSecurityTest.logoutRevokesCapturedBearerAndCookieSessionsAfterCommit`
exige 401 para ambos tokens después del commit. El texto siguiente conserva la
evidencia del fallo anterior; ya no describe el comportamiento vigente.

Se reprodujo con la cadena Spring Security, JwtFilter y AuthController existentes y la fixture ficticia de A03.
El verificador JWT está simulado como un token válido con versión 4; la autorización y el cierre de sesión son código real.
No usa credenciales ni cuentas institucionales, no carga `.env` y no necesita PostgreSQL.

Secuencia verificada:

1. GET protegido con token válido: 200.
2. POST logout con su cookie: 204 y Max-Age=0.
3. Repetir GET con el mismo token Bearer: 200; la expectativa de revocación es 401 y falla.

El controlador borra la cookie sin cambiar `tokenVersion` ni registrar la revocación del token.
Por tanto, un token capturado sigue utilizable mientras sea válido. No se cambió esta política dentro de A08.

El reproductor se conserva aquí como diagnóstico separado, fuera de la suite ordinaria de regresiones de A08.
Su resultado fallido figura expresamente en el informe; no constituye una prueba de seguridad aprobada.
No se añadió una exclusión, un @Disabled ni se cambió su expectativa a 200 para hacerlo pasar.

Para repetirlo, copiar únicamente `LogoutReplayDiagnosticTest.java` al paquete
`backend/sigaa-api/src/test/java/com/sigaa/security/` y ejecutar Maven con
`-Dtest=LogoutReplayDiagnosticTest#aCapturedValidTokenMustNotAuthorizeAfterLogout test`.
En Windows puede requerirse el agente Mockito que ya usa la validación local.
Después, retirar solamente esa copia de diagnóstico y su .class generado, preservando el log de evidencia.
Cuando se decida la política de revocación (por sesión o para todas las sesiones), implementar el cambio
y convertir este reproductor en una regresión permanente que exija 401.
