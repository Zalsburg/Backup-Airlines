# Corrida en MinedApp — evidencias

Recorrido de las etapas de MinedApp con `data/processed/sesiones_minedapp.csv`
(2.948 filas × 17 variables numéricas), realizado el 13 de octubre de 2026.

## Capturas (`capturas/`)

| Etapa | Archivos |
|---|---|
| 1. Problema | `01_problema.png` — nombre del proyecto y tipo de tarea **Otro** |
| 2. Datos | `02_preview_a/b.png`, `02_estadisticas.png`, `02_nulos.png`, `02_atipicos_iqr.png`, `02_histogramas.png` (17 histogramas; individuales en `histogramas/`), `02_correlacion_a/b.png` |
| 3. Preprocesamiento | `03_preprocesamiento.png`, `03_codigo_generado.png` |
| 4. Modelado | `04_modelado.png`, `04_codigo_generado.png` |
| 5. Evaluación | `05_evaluacion.png` |
| 6. Reporte | `06_reporte_a.png`, `06_reporte_b.png` — transcripción textual en `reporte_minedapp.md` |

## Código generado por la app (transcripción textual)

| Archivo | Observación |
|---|---|
| `codigo_minedapp_etapa3_preprocesamiento.py` | Se ejecuta correctamente: 2.948 × 17 antes y después |
| `codigo_minedapp_etapa4_modelado.py` | **No ejecuta**: pide una columna `target` que no existe y llama `PCA.predict()`, que no existe |
| `codigo_minedapp_etapa5_evaluacion.py` | Plantilla vacía: para la tarea "Otro" la app no propone métricas |

## Corrección

`pca_corregido.py` usa PCA como lo permite la técnica: detector de anomalías por
error de reconstrucción, con la misma preparación configurada en la app y la misma
división temporal del modelo principal. Resultado en `../resultados/metricas_pca.json`:
F1 = 0,618 frente a 0,776 del Isolation Forest, lo que respalda la elección del
modelo principal.
