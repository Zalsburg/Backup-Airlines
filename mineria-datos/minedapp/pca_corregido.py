"""
pca_corregido.py
================
Corrección del código de modelado que generó MinedApp para la tarea "Otro".

Para ese tipo de tarea la aplicación solo ofrece PCA y genera este código:

    y = df['target']                       # KeyError: el dataset no tiene 'target'
    model = PCA(); model.fit(X_train, y_train)
    y_pred = model.predict(X_test)         # AttributeError: PCA no tiene predict()

PCA no es un clasificador sino una técnica de reducción de dimensionalidad. Sí puede
usarse para detectar anomalías: se ajusta con el comportamiento habitual y cada
sesión se puntúa por su ERROR DE RECONSTRUCCIÓN (cuánto se pierde al proyectarla
en las componentes principales y devolverla al espacio original). Las sesiones
que el patrón habitual no logra reconstruir son las inusuales.

Se usa la misma preparación configurada en MinedApp (mediana + StandardScaler) y la
misma división temporal y etiquetas de validación del modelo principal, para que la
comparación con Isolation Forest sea justa.

Uso:  python mineria-datos/minedapp/pca_corregido.py
"""
import json
import os
import sys

import numpy as np
import pandas as pd
from sklearn.decomposition import PCA
from sklearn.impute import SimpleImputer
from sklearn.metrics import average_precision_score
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import StandardScaler

AQUI = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(AQUI))
import modelo  # noqa: E402
from preparacion import FEATURES  # noqa: E402

VARIANZA = 0.90       # componentes que explican el 90 % de la varianza
PERCENTIL = 97        # umbral: percentil 97 del error en entrenamiento (≈ contamination 0,03)


def main():
    s, lab = modelo.cargar()
    s = s.merge(lab[["session_id", "escenario", "es_anomala"]], on="session_id")
    train, test = modelo.dividir(s)

    prep = Pipeline([("imputar", SimpleImputer(strategy="median")),
                     ("escalar", StandardScaler())])
    Xtr = prep.fit_transform(train[FEATURES])
    Xte = prep.transform(test[FEATURES])

    pca = PCA(n_components=VARIANZA, random_state=42).fit(Xtr)
    err = lambda X: ((X - pca.inverse_transform(pca.transform(X))) ** 2).sum(axis=1)
    umbral = np.percentile(err(Xtr), PERCENTIL)
    test["error_reconstruccion"] = err(Xte)
    test["pred_pca"] = (test["error_reconstruccion"] > umbral).astype(int)

    y = test["es_anomala"].to_numpy()
    res = modelo.metricas(y, test["pred_pca"].to_numpy(), "PCA (error de reconstrucción)")
    res["average_precision"] = round(float(average_precision_score(y, test["error_reconstruccion"])), 3)
    res["componentes"] = int(pca.n_components_)
    res["varianza_explicada"] = round(float(pca.explained_variance_ratio_.sum()), 3)
    por_esc = (test[test.es_anomala == 1].groupby("escenario")["pred_pca"]
               .agg(["size", "sum"]).rename(columns={"size": "sesiones", "sum": "detectadas"}))

    print(json.dumps(res, ensure_ascii=False, indent=1))
    print(por_esc)
    os.makedirs(modelo.RES, exist_ok=True)
    with open(os.path.join(modelo.RES, "metricas_pca.json"), "w", encoding="utf-8") as f:
        json.dump({"resultado": res, "por_escenario": por_esc.reset_index().to_dict(orient="records"),
                   "umbral_error": round(float(umbral), 4)}, f, ensure_ascii=False, indent=2)


if __name__ == "__main__":
    main()
