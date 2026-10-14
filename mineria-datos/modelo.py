"""
modelo.py
=========
Entrena y evalúa el detector de sesiones de riesgo de Backup Airlines.

Tarea:      detección de anomalías (no supervisada)
Algoritmo:  Isolation Forest (scikit-learn)
Referencia: regla de umbrales IQR (Q3 + 3·IQR) + actividad fuera de horario
División:   temporal — julio y agosto = entrenamiento, septiembre = prueba
            (el modelo se ajusta con el pasado y se evalúa sobre sesiones futuras)

Las etiquetas de validación (escenarios inyectados) NO se usan para entrenar.
Solo se usan al final para medir cuántas sesiones de riesgo encuentra el modelo.

Uso:
    python mineria-datos/modelo.py
Salida en mineria-datos/resultados/:
    metricas.json, sesiones_prueba_puntuadas.csv, matriz_confusion.png, curva_pr.png
"""
from __future__ import annotations

import json
import os

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np
import pandas as pd
from sklearn.ensemble import IsolationForest
from sklearn.impute import SimpleImputer
from sklearn.metrics import (average_precision_score, confusion_matrix, f1_score,
                             precision_recall_curve, precision_score, recall_score)
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import StandardScaler

from preparacion import FEATURES

SEMILLA = 42
FECHA_CORTE = "2026-09-01"   # inicio del periodo de prueba
CONTAMINACION = 0.03          # proporción esperada de sesiones de riesgo (supuesto de negocio)

RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PROC = os.path.join(RAIZ, "data", "processed")
RES = os.path.join(RAIZ, "mineria-datos", "resultados")

COLUMNAS_CONTEO = ["n_eventos", "eventos_por_min", "archivos_distintos", "n_download",
                   "n_share", "n_delete", "pct_archivos_nuevos"]


def cargar():
    s = pd.read_csv(os.path.join(PROC, "sesiones_backup_airlines.csv"))
    lab = pd.read_csv(os.path.join(PROC, "etiquetas_validacion.csv"))
    return s, lab


def dividir(s: pd.DataFrame):
    train = s[s["fecha"] < FECHA_CORTE].copy()
    test = s[s["fecha"] >= FECHA_CORTE].copy()
    return train, test


def construir_pipeline() -> Pipeline:
    # Imputación y escalado se ajustan SOLO con entrenamiento (dentro del Pipeline)
    return Pipeline([
        ("imputar", SimpleImputer(strategy="median")),
        ("escalar", StandardScaler()),
        ("modelo", IsolationForest(n_estimators=300, contamination=CONTAMINACION,
                                   random_state=SEMILLA, n_jobs=-1)),
    ])


def regla_base(train: pd.DataFrame, test: pd.DataFrame) -> np.ndarray:
    """Referencia sencilla: marca la sesión si alguna variable de conteo supera
    Q3 + 3·IQR del entrenamiento, o si inicia fuera de horario."""
    umbrales = {}
    for c in COLUMNAS_CONTEO:
        q1, q3 = train[c].quantile([0.25, 0.75])
        umbrales[c] = q3 + 3 * (q3 - q1)
    marca = test["fuera_horario"].eq(1)
    for c, u in umbrales.items():
        marca |= test[c].fillna(0) > u
    return marca.astype(int).to_numpy(), umbrales


def metricas(y, pred, nombre):
    tn, fp, fn, tp = confusion_matrix(y, pred, labels=[0, 1]).ravel()
    return {"modelo": nombre,
            "precision": round(precision_score(y, pred, zero_division=0), 3),
            "recall": round(recall_score(y, pred, zero_division=0), 3),
            "f1": round(f1_score(y, pred, zero_division=0), 3),
            "sesiones_marcadas": int(pred.sum()),
            "TP": int(tp), "FP": int(fp), "FN": int(fn), "TN": int(tn)}


def ejecutar(guardar: bool = True) -> dict:
    s, lab = cargar()
    s = s.merge(lab[["session_id", "escenario", "es_anomala"]], on="session_id", how="left")
    train, test = dividir(s)

    pipe = construir_pipeline()
    pipe.fit(train[FEATURES])                          # sin etiquetas
    test["puntaje_riesgo"] = -pipe.score_samples(test[FEATURES])   # mayor = más anómalo
    test["pred_iforest"] = (pipe.predict(test[FEATURES]) == -1).astype(int)
    test["pred_regla"], umbrales = regla_base(train, test)

    y = test["es_anomala"].to_numpy()
    res_if = metricas(y, test["pred_iforest"].to_numpy(), "Isolation Forest")
    res_if["average_precision"] = round(average_precision_score(y, test["puntaje_riesgo"]), 3)
    res_rg = metricas(y, test["pred_regla"].to_numpy(), "Regla IQR + horario (referencia)")
    k = int(y.sum())
    top_k = test.nlargest(k, "puntaje_riesgo")
    res_if["precision_at_k"] = round(float(top_k["es_anomala"].mean()), 3)
    # Análisis POSTERIOR (no fue la configuración elegida a priori): combinar el
    # modelo con la marca de horario, porque la Isolation Forest diluye una sola
    # variable binaria entre 17. Se reporta aparte para no sobrevalorar resultados.
    test["pred_hibrido"] = (test["pred_iforest"].eq(1) | test["fuera_horario"].eq(1)).astype(int)
    res_hb = metricas(y, test["pred_hibrido"].to_numpy(), "Híbrido IF + fuera de horario (posterior)")

    por_escenario = (test[test["es_anomala"] == 1].groupby("escenario")
                     .agg(sesiones=("es_anomala", "size"),
                          detectadas_iforest=("pred_iforest", "sum"),
                          detectadas_regla=("pred_regla", "sum"),
                          detectadas_hibrido=("pred_hibrido", "sum")).reset_index())

    salida = {
        "dataset": {"sesiones_total": int(len(s)), "entrenamiento": int(len(train)),
                    "prueba": int(len(test)), "anomalas_en_prueba": k,
                    "tasa_anomalas_prueba": round(k / len(test), 4),
                    "variables": FEATURES, "fecha_corte": FECHA_CORTE},
        "parametros": {"algoritmo": "IsolationForest", "n_estimators": 300,
                       "contamination": CONTAMINACION, "random_state": SEMILLA,
                       "imputacion": "mediana (ajustada en entrenamiento)",
                       "escalado": "StandardScaler (ajustado en entrenamiento)"},
        "resultados": [res_if, res_rg, res_hb],
        "por_escenario": por_escenario.to_dict(orient="records"),
        "umbrales_regla": {k2: round(float(v), 3) for k2, v in umbrales.items()},
    }

    if guardar:
        os.makedirs(RES, exist_ok=True)
        with open(os.path.join(RES, "metricas.json"), "w", encoding="utf-8") as f:
            json.dump(salida, f, ensure_ascii=False, indent=2)
        cols = ["session_id", "username", "rol", "inicio"] + FEATURES + \
               ["puntaje_riesgo", "pred_iforest", "pred_regla", "pred_hibrido", "escenario", "es_anomala"]
        test.sort_values("puntaje_riesgo", ascending=False)[cols].to_csv(
            os.path.join(RES, "sesiones_prueba_puntuadas.csv"), index=False)
        _graficas(test, y)
    salida["_test"] = test
    salida["_pipe"] = pipe
    return salida


def _graficas(test, y):
    fig, axes = plt.subplots(1, 2, figsize=(9, 3.8))
    for ax, col, titulo in [(axes[0], "pred_iforest", "Isolation Forest"),
                            (axes[1], "pred_regla", "Regla de referencia")]:
        cm = confusion_matrix(y, test[col], labels=[0, 1])
        ax.imshow(cm, cmap="Blues")
        for i in range(2):
            for j in range(2):
                ax.text(j, i, cm[i, j], ha="center", va="center",
                        color="white" if cm[i, j] > cm.max() / 2 else "black", fontsize=13)
        ax.set_xticks([0, 1], ["Normal", "Riesgo"]); ax.set_yticks([0, 1], ["Normal", "Riesgo"])
        ax.set_xlabel("Predicción"); ax.set_ylabel("Escenario real"); ax.set_title(titulo)
    fig.tight_layout(); fig.savefig(os.path.join(RES, "matriz_confusion.png"), dpi=150); plt.close(fig)

    p, r, _ = precision_recall_curve(y, test["puntaje_riesgo"])
    fig, ax = plt.subplots(figsize=(5, 3.8))
    ax.plot(r, p, color="#003580", lw=2, label="Isolation Forest")
    ax.axhline(y.mean(), ls="--", color="gray", label=f"Azar ({y.mean():.3f})")
    ax.set_xlabel("Recall"); ax.set_ylabel("Precisión"); ax.set_title("Curva precisión–recall (prueba)")
    ax.legend(); fig.tight_layout(); fig.savefig(os.path.join(RES, "curva_pr.png"), dpi=150); plt.close(fig)


if __name__ == "__main__":
    out = ejecutar()
    for r in out["resultados"]:
        print(r)
    print(pd.DataFrame(out["por_escenario"]))
