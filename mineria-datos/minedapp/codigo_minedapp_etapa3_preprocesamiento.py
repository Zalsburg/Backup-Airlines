# Código generado por MinedApp — Etapa 3 (transcripción textual, sin cambios)
import pandas as pd
import numpy as np
from sklearn.preprocessing import StandardScaler, MinMaxScaler, RobustScaler, OneHotEncoder, LabelEncoder

# Cargar datos
df = pd.read_csv("sesiones_minedapp.csv")
print("Filas x Columnas:", df.shape)

# Imputar nulos numéricos con la mediana
num_cols = df.select_dtypes(include=[np.number]).columns
for c in num_cols:
    df[c] = df[c].fillna(df[c].median())


# Escalado con StandardScaler
scaler = StandardScaler()
num_cols = df.select_dtypes(include=[np.number]).columns
df[num_cols] = scaler.fit_transform(df[num_cols])


print("Después de preprocesar:", df.shape)
print(df.head())
