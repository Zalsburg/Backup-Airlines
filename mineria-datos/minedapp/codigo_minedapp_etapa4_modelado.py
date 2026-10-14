# Código generado por MinedApp — Etapa 4 (transcripción textual, sin cambios)
# ATENCIÓN: este código NO se ejecuta tal cual sobre el dataset del proyecto:
#   - KeyError 'target': el dataset no tiene variable objetivo (tarea no supervisada).
#   - PCA no tiene método predict(): es reducción de dimensionalidad, no un clasificador.
# La versión corregida y ejecutada está en pca_corregido.py
import pandas as pd
import numpy as np
from sklearn.model_selection import train_test_split
from sklearn.decomposition import PCA

# Definir X e y
y = df['target']
X = df.drop(columns=['target'])

# Train/test split
X_train, X_test, y_train, y_test = train_test_split(X, y, test_size=0.2, random_state=42)

# Modelo: PCA
model = PCA()
model.fit(X_train, y_train)
y_pred = model.predict(X_test)

print("Predicciones (primeras 10):", y_pred[:10])
