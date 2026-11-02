# Fase 0: control, confidencialidad y línea base

Fecha de apertura: 25 de agosto de 2026  
Repositorio: SIGAA  
Rama observada: `codex/android-apk-sigaa`  
Commit de referencia: `ba66e5129cde1c469e539b93e9685495bd761c18`

## Objetivo

Establecer una línea base recuperable del código y reglas de trabajo antes de
modificar controles de seguridad. Esta fase no cambia datos persistentes ni
autoriza un despliegue.

## Restricciones obligatorias

1. No crear datos, documentos, cuentas o usuarios ficticios durante esta fase.
2. No eliminar, desactivar, bloquear ni modificar los usuarios registrados.
3. No ejecutar migraciones contra la base de datos persistente.
4. No ejecutar pruebas que escriban sobre datos existentes.
5. No copiar datos institucionales a CI, archivos ZIP, tickets o repositorios.
6. No incluir `.env`, claves, certificados, respaldos, cargas o bases de datos
   en checkpoints de código.
7. No publicar cambios en GitHub sin autorización expresa.
8. No registrar contraseñas, tokens, documentos o datos personales en salidas
   de consola, capturas o informes.

Estas restricciones no autorizan el uso de datos reales para pruebas. Toda
prueba futura que necesite escritura requerirá un entorno aislado y un método
de datos aprobado por la institución. Hasta entonces, solo se permiten
validaciones estáticas o pruebas que no alcancen la base persistente.

## Línea base

El inventario inicial encontró:

- 100 archivos rastreados con modificaciones locales.
- 79 archivos nuevos no rastreados por Git.
- 2,791 inserciones y 1,878 eliminaciones en los archivos rastreados.
- Cambios mezclados en backend, frontend, Android, CI, operación y
  documentación.
- Un archivo `.env` local ignorado por Git; no fue leído ni incorporado al
  checkpoint.
- Ninguna clave privada con extensión habitual aparece rastreada por Git.

La lista clasificada está en
`docs/production-readiness/INVENTARIO_CAMBIOS_2026-08-25.md`.

## Checkpoint recuperable

El script `ops/scripts/New-SourceCheckpoint.ps1` genera un ZIP local en
`.phase0/checkpoints/`, acompañado de:

- una suma SHA-256 del ZIP;
- un manifiesto SHA-256 de cada archivo incluido;
- la rama y el commit de referencia.

El directorio `.phase0/` está ignorado por Git. El ZIP es una salvaguarda del
código pendiente, no un respaldo de la base de datos ni de los documentos.

Para verificarlo:

```powershell
$checkpoint = Get-ChildItem .phase0/checkpoints/*.zip |
  Sort-Object LastWriteTime -Descending |
  Select-Object -First 1
Get-FileHash -Algorithm SHA256 -LiteralPath $checkpoint.FullName
Get-Content "$($checkpoint.FullName).sha256"
```

Para recuperar, se debe descomprimir en un directorio vacío y comparar primero
el manifiesto. No se debe extraer directamente sobre el repositorio activo sin
una revisión y autorización de cambio.

## Separación de ambientes

| Ambiente | Estado en Fase 0 | Datos permitidos | Regla de cambio |
| --- | --- | --- | --- |
| Desarrollo local | Existente | No crear ni alterar datos en esta fase | Código local, sin publicación |
| CI | Configurado localmente | Ningún dato institucional | Solo pruebas aisladas aprobadas |
| Preproducción | Pendiente de formalizar | Sin copia directa de producción | Despliegue firmado y autorizado |
| Producción | No autorizado | Datos institucionales clasificados | Ventana, respaldo y reversión |

## Control de cambios desde esta fase

Cada bloque posterior debe contener:

1. Alcance y riesgo específico.
2. Archivos y migraciones involucrados.
3. Pruebas positivas, negativas y de regresión.
4. Confirmación de que no se alteraron datos fuera del alcance.
5. Procedimiento de reversión.
6. Evidencia de revisión de seguridad.
7. Autorización separada para publicar o desplegar.

No se mezclarán correcciones visuales con cambios de autenticación, permisos o
persistencia en un mismo bloque de entrega.

## Responsabilidades por asignar

| Responsabilidad | Titular | Suplente | Estado |
| --- | --- | --- | --- |
| Propietario funcional | Por designar | Por designar | Pendiente |
| Responsable de seguridad | Por designar | Por designar | Pendiente |
| Responsable de protección de datos | Por designar | Por designar | Pendiente |
| Responsable de archivo y retención | Por designar | Por designar | Pendiente |
| Operación e infraestructura | Por designar | Por designar | Pendiente |
| Custodio de respaldos y claves | Por designar | Por designar | Pendiente |
| Control de calidad | Por designar | Por designar | Pendiente |

Una misma persona no debe ser simultáneamente la única custodio de datos,
claves, respaldos y autorización de despliegue.

## Criterios de cierre de la Fase 0

- Inventario de cambios disponible y clasificado.
- Checkpoint de código creado y verificado por SHA-256.
- Secretos y datos excluidos del checkpoint.
- Restricciones de datos y usuarios documentadas.
- Ambientes y control de cambios documentados.
- Responsables institucionales identificados como pendientes de designación.

