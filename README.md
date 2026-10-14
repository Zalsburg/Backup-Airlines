# ✈ Backup Airlines

Plataforma web de respaldo y gestión documental con control de acceso por roles, versionado automático, papelera con retención y bitácora de actividad, más un **componente de minería de datos** que detecta sesiones de uso de riesgo a partir de esa bitácora.

**Proyecto de Aula — Minería de Datos I** · Docente: Ing. Cristian Camilo Cuadrado Beltrán

| Integrantes |
|---|
| Gabriel Santiago Osorio Piñeres |
| Jhon Jairo Carmona Macea |

## Problema que aborda

Las pymes de logística y servicios aeroportuarios de Cartagena de Indias guardan y comparten documentos sujetos a auditoría (manuales, certificados, órdenes de trabajo) en herramientas de nube de uso general, sin versionado controlado, sin registro centralizado de quién hace qué y sin revisión de patrones de acceso (ver `documentos/anexos/`). Backup Airlines resuelve el control y la trazabilidad; el componente de minería de datos responde a la pregunta:

> ¿Es posible identificar automáticamente, a partir de la bitácora de actividad, las sesiones cuyo comportamiento se aparta del habitual (descargas, borrados o comparticiones masivas, o actividad fuera de horario) para priorizar su revisión en una auditoría?

## Tecnologías

- **Prototipo:** Java · Spring Boot 3.1 · Spring Security 6 · Spring Data JPA / Hibernate · H2 · Thymeleaf · Bootstrap 5 · Maven
- **Minería de datos:** Python 3.10+ · pandas · NumPy · scikit-learn (Isolation Forest) · Matplotlib · Jupyter · MinedApp (guía de las seis etapas)

## Estructura del repositorio

```
backup-airlines/
├── README.md
├── pom.xml
├── requirements.txt              ← dependencias del análisis en Python
├── src/                          ← prototipo Spring Boot
├── data/
│   ├── README.md                 ← origen, diccionario de datos y condiciones
│   ├── raw/activity_logs_simulados.csv
│   └── processed/
│       ├── sesiones_backup_airlines.csv     ← dataset completo del modelo
│       ├── sesiones_minedapp.csv            ← 17 variables numéricas, para cargar en MinedApp
│       ├── etiquetas_validacion.csv         ← SOLO para evaluar (no se entrena con él)
│       └── etiquetas_validacion_eventos.csv
├── mineria-datos/
│   ├── analisis.ipynb            ← notebook ejecutado con las 6 etapas y sus salidas
│   ├── generar_dataset.py        ← genera la bitácora simulada (semilla 42)
│   ├── preparacion.py            ← sesionización y variables
│   ├── modelo.py                 ← entrenamiento, evaluación y gráficas
│   ├── minedapp/                 ← capturas de la corrida en MinedApp, su código y la corrección de PCA
│   └── resultados/               ← métricas, gráficas y sesiones puntuadas
└── documentos/
    ├── Propuesta_Modelo_Predictivo_Backup_Airlines.pdf
    ├── Informe_Metodologico_Mineria_Datos_Backup_Airlines.pdf
    ├── antecedentes/             ← informe técnico, informe de planteamiento y Lienzo Canvas previos
    └── anexos/                   ← Anexo 1 (contextualización) y Anexo 2 (problematización)
```

## Fuente de los datos

La aplicación registra cada acción en la tabla `activity_logs` (`UPLOAD_FILE`, `DOWNLOAD_FILE`, `PREVIEW_FILE`, `SHARE_FILE`, `DELETE_FILE`, `RESTORE_VERSION`), pero esa tabla vive en una base H2 en memoria y aún no hay uso real por parte de empresas. Por eso el análisis usa una **bitácora simulada** con el mismo esquema, generada por `mineria-datos/generar_dataset.py` a partir de los perfiles de uso observados en el Anexo 2. Los detalles están en [`data/README.md`](data/README.md).

Para pasar a datos reales, la aplicación incluye el endpoint **`GET /admin/logs/export.csv`** (solo `ROLE_ADMIN`, botón *Exportar bitácora (CSV)* en `/admin/logs`), que descarga la bitácora con las mismas columnas que el archivo simulado.

## Cómo ejecutar

### Prototipo

```bash
mvn spring-boot:run
# http://localhost:8080  ·  admin / admin123  ·  usuario1 / user1234
```

### Análisis de minería de datos

```bash
python -m venv .venv && source .venv/bin/activate     # Windows: .venv\Scripts\activate
pip install -r requirements.txt
python mineria-datos/generar_dataset.py   # 1. bitácora simulada
python mineria-datos/preparacion.py       # 2. sesiones + variables
python mineria-datos/modelo.py            # 3. entrenamiento y evaluación
# o todo junto, con explicaciones y gráficas:
jupyter notebook mineria-datos/analisis.ipynb
```

Con la semilla fija (42) los resultados son reproducibles. Para analizar datos reales, guarde el CSV exportado como `data/raw/activity_logs_simulados.csv` (o cambie la ruta `RAW` en `preparacion.py`) y ejecute desde el paso 2; la evaluación con etiquetas solo aplica cuando existan incidentes confirmados.

## Resultados obtenidos (datos simulados)

Dataset: 27.998 eventos → 2.948 sesiones de 35 usuarios (1 jul – 29 sep 2026), 17 variables. Entrenamiento: julio–agosto (1.997 sesiones). Prueba: septiembre (951 sesiones, 28 de riesgo).

| Modelo | Precisión | Recall | F1 | Sesiones marcadas |
|---|---|---|---|---|
| **Isolation Forest** | 0,905 | 0,679 | 0,776 | 21 |
| PCA por error de reconstrucción (algoritmo que ofrece MinedApp, corregido) | 0,630 | 0,607 | 0,618 | 27 |
| Regla IQR + horario (referencia) | 0,483 | 1,000 | 0,651 | 58 |
| Híbrido IF + fuera de horario *(análisis posterior)* | 0,812 | 0,929 | 0,867 | 32 |

Average precision del puntaje de riesgo: 0,949. El modelo detecta todas las descargas y borrados masivos, pero solo 2 de 9 sesiones fuera de horario; sus dos falsas alarmas fueron sesiones legítimas de preparación de auditoría a fin de mes. Detalle en `mineria-datos/resultados/metricas.json` y en el informe metodológico.

> Estas métricas validan el procedimiento sobre escenarios simulados; **no** son una medida del desempeño con usuarios reales.

## Documentos de la entrega

- [Propuesta del modelo predictivo (PDF)](documentos/Propuesta_Modelo_Predictivo_Backup_Airlines.pdf)
- [Informe metodológico (PDF)](documentos/Informe_Metodologico_Mineria_Datos_Backup_Airlines.pdf)

## Seguridad

Las credenciales `admin/admin123` y `usuario1/user1234` son datos semilla de desarrollo creados en `BackupApplication.java`; no se usan en ningún entorno real. El repositorio no incluye la base de datos local, los archivos subidos (`uploads/`) ni datos personales.
