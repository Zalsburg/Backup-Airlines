# Datos — Backup Airlines

## Origen

| Aspecto | Detalle |
|---|---|
| Tipo de fuente | Dataset **simulado** (Situación C de la guía del curso) con el esquema real de la aplicación |
| Esquema de referencia | Entidad `ActivityLog` → tabla `activity_logs` (`src/main/java/com/example/backup/model/ActivityLog.java`) |
| Generador | `mineria-datos/generar_dataset.py` (semilla 42, reproducible) |
| Periodo simulado | 1 de julio – 29 de septiembre de 2026 (13 semanas) |
| Contexto | 35 usuarios de una pyme de logística / servicios aeroportuarios de Cartagena (Anexos 1 y 2) |
| Datos personales | Ninguno. Los usuarios son identificadores ficticios (`coord01`, `auxil07`, …) |
| Condiciones de uso | Libre uso académico; generado por el equipo |

### ¿Por qué simulado?

La aplicación guarda su bitácora en una base H2 **en memoria** (`jdbc:h2:mem:backupdb`), que se borra al reiniciar, y todavía no la usan empresas reales. En lugar de cambiar de proyecto, se simuló la bitácora respetando:

- las seis acciones que la aplicación realmente registra: `UPLOAD_FILE`, `DOWNLOAD_FILE`, `PREVIEW_FILE`, `SHARE_FILE`, `DELETE_FILE`, `RESTORE_VERSION`;
- los perfiles de uso observados en el Anexo 2 (jornada laboral, turnos de sábado en operaciones, consultas intensivas de calidad antes de auditorías a fin de mes);
- un conjunto de archivos "habituales" por usuario.

Sobre ese uso normal se inyectó, de forma controlada, un **3,5 %** de sesiones de riesgo en cuatro escenarios: descarga masiva, borrado masivo, compartición masiva por enlace y actividad fuera de horario.

### Paso a datos reales

El endpoint `GET /admin/logs/export.csv` (solo administradores) descarga la bitácora real con las mismas columnas. Para conservar el histórico entre reinicios, cambie en `application.properties` la URL a `jdbc:h2:file:./data/backupdb`.

## Archivos

### `raw/activity_logs_simulados.csv` — 27.998 eventos

| Columna | Tipo | Descripción |
|---|---|---|
| `id` | entero | Identificador del evento |
| `created_at` | fecha-hora | Momento del evento (hora de Colombia) |
| `username` | texto | Usuario que realizó la acción |
| `rol` | texto | Perfil del usuario (en el export real: `ROLE_ADMIN` / `ROLE_USER`) |
| `action` | categórica | Una de las seis acciones registradas por la aplicación |
| `file_id` | entero | Archivo afectado (vacío si el archivo ya fue purgado) |

### `processed/sesiones_backup_airlines.csv` — 2.948 sesiones · 22 columnas

Archivo que usa el modelo (incluye columnas de identificación). Una sesión agrupa los eventos de un usuario separados por menos de 30 minutos (timeout de sesión de la aplicación).

| Columna | Descripción |
|---|---|
| `session_id`, `username`, `rol`, `fecha`, `inicio` | Identificación (no entran al modelo) |
| `n_eventos` | Total de acciones en la sesión |
| `duracion_min` | Minutos entre el primer y el último evento |
| `eventos_por_min` | `n_eventos / (duracion_min + 1)` — intensidad |
| `archivos_distintos` | Archivos diferentes tocados |
| `n_upload` … `n_restore_version` | Conteo por tipo de acción |
| `pct_download`, `pct_delete`, `pct_share` | Proporción de cada acción sensible |
| `hora_inicio` | Hora (0–23) de inicio |
| `fuera_horario` | 1 si inicia antes de las 06:00 o desde las 20:00 |
| `fin_de_semana` | 1 si es domingo (el sábado hay turno de operaciones) |
| `pct_archivos_nuevos` | % de archivos que el usuario no había tocado antes. **Nulo en la primera sesión de cada usuario** (35 casos) |

### `processed/sesiones_minedapp.csv` — 2.948 filas · 17 columnas

Las mismas sesiones, pero solo con las 17 variables numéricas del modelo. Es el archivo que conviene cargar en **MinedApp**. Si se carga el archivo completo, la etapa de codificación One-Hot convierte `username`, `fecha` e `inicio` en miles de columnas que no aportan nada al análisis.

### `processed/etiquetas_validacion.csv`

`session_id`, `escenario` (`normal`, `descarga_masiva`, `borrado_masivo`, `comparticion_masiva`, `fuera_de_horario`) y `es_anomala` (0/1). **No se usa para entrenar**: solo para medir, al final, cuántas sesiones de riesgo encontró el modelo. No debe cargarse en MinedApp junto con las variables.

### `processed/etiquetas_validacion_eventos.csv`

El mismo escenario a nivel de evento; `preparacion.py` lo usa para trasladar la etiqueta a la sesión reconstruida.
