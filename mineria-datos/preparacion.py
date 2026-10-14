"""
preparacion.py
==============
Transforma la bitácora de eventos (activity_logs) en un dataset de SESIONES,
que es la unidad de análisis del modelo.

Regla de sesión: eventos consecutivos del mismo usuario separados por menos de
30 minutos pertenecen a la misma sesión (coincide con server.servlet.session.timeout=30m
configurado en application.properties de la aplicación).

Uso:
    python mineria-datos/preparacion.py
Entrada:
    data/raw/activity_logs_simulados.csv   (o el CSV exportado de /admin/logs/export.csv)
Salida:
    data/processed/sesiones_backup_airlines.csv     -> dataset para MinedApp / modelo
    data/processed/etiquetas_validacion.csv         -> escenario por sesión (solo validación)
"""
from __future__ import annotations

import os

import numpy as np
import pandas as pd

RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RAW = os.path.join(RAIZ, "data", "raw", "activity_logs_simulados.csv")
PROC = os.path.join(RAIZ, "data", "processed")

GAP_SESION_MIN = 30
HORA_INICIO_LABORAL, HORA_FIN_LABORAL = 6, 20   # 06:00–19:59 se considera horario laboral

# Variables predictoras finales (todas numéricas; ninguna usa la etiqueta)
FEATURES = [
    "n_eventos", "duracion_min", "eventos_por_min", "archivos_distintos",
    "n_upload", "n_download", "n_preview", "n_share", "n_delete", "n_restore_version",
    "pct_download", "pct_delete", "pct_share",
    "hora_inicio", "fuera_horario", "fin_de_semana", "pct_archivos_nuevos",
]


def cargar_logs(ruta: str = RAW) -> pd.DataFrame:
    df = pd.read_csv(ruta, parse_dates=["created_at"])
    return df.sort_values(["username", "created_at"]).reset_index(drop=True)


def sesionizar(df: pd.DataFrame) -> pd.DataFrame:
    """Asigna session_id reconstruyendo sesiones por inactividad > 30 min."""
    df = df.copy()
    gap = df.groupby("username")["created_at"].diff()
    nueva = gap.isna() | (gap > pd.Timedelta(minutes=GAP_SESION_MIN))
    df["session_id"] = nueva.cumsum().astype(int)
    return df


def construir_sesiones(df: pd.DataFrame) -> pd.DataFrame:
    """Agrega los eventos por sesión y calcula las variables del modelo."""
    df = df.copy()
    acciones = {"UPLOAD_FILE": "n_upload", "DOWNLOAD_FILE": "n_download",
                "PREVIEW_FILE": "n_preview", "SHARE_FILE": "n_share",
                "DELETE_FILE": "n_delete", "RESTORE_VERSION": "n_restore_version"}
    conteos = (pd.crosstab(df["session_id"], df["action"])
               .reindex(columns=list(acciones), fill_value=0)
               .rename(columns=acciones))

    g = df.groupby("session_id")
    base = pd.DataFrame({
        "username": g["username"].first(),
        "rol": g["rol"].first() if "rol" in df.columns else "desconocido",
        "inicio": g["created_at"].min(),
        "fin": g["created_at"].max(),
        "n_eventos": g.size(),
        "archivos_distintos": g["file_id"].nunique(),
    })
    s = base.join(conteos)
    s["duracion_min"] = ((s["fin"] - s["inicio"]).dt.total_seconds() / 60).round(2)
    # +1 min evita división por cero en sesiones de un solo evento
    s["eventos_por_min"] = (s["n_eventos"] / (s["duracion_min"] + 1)).round(3)
    for col, origen in [("pct_download", "n_download"), ("pct_delete", "n_delete"), ("pct_share", "n_share")]:
        s[col] = (s[origen] / s["n_eventos"]).round(3)
    s["hora_inicio"] = s["inicio"].dt.hour
    s["fuera_horario"] = ((s["hora_inicio"] < HORA_INICIO_LABORAL) | (s["hora_inicio"] >= HORA_FIN_LABORAL)).astype(int)
    s["fin_de_semana"] = (s["inicio"].dt.weekday == 6).astype(int)  # domingo (sábado tiene turno)
    s["fecha"] = s["inicio"].dt.date.astype(str)

    # % de archivos de la sesión que el usuario NO había tocado en sesiones anteriores
    df = df.sort_values("created_at")
    vistos: dict[str, set] = {}
    pct_nuevos = {}
    for sid, grupo in df.groupby("session_id", sort=False):
        user = grupo["username"].iat[0]
        prev = vistos.setdefault(user, set())
        archivos = set(grupo["file_id"].dropna())   # file_id vacío = archivo ya purgado
        pct_nuevos[sid] = len(archivos - prev) / len(archivos) if (prev and archivos) else np.nan
        prev |= archivos
    s["pct_archivos_nuevos"] = pd.Series(pct_nuevos).round(3)

    s = s.reset_index().sort_values("inicio").reset_index(drop=True)
    s["inicio"] = s["inicio"].dt.strftime("%Y-%m-%d %H:%M:%S")
    columnas = ["session_id", "username", "rol", "fecha", "inicio"] + FEATURES
    return s[columnas]


def etiquetas_por_sesion(df_ses_eventos: pd.DataFrame, ruta_labels: str) -> pd.DataFrame:
    """Une las etiquetas de validación (por evento) a la sesión reconstruida.
    Una sesión es 'anómala' si contiene al menos un evento de un escenario inyectado."""
    lab = pd.read_csv(ruta_labels)
    m = df_ses_eventos[["id", "session_id"]].merge(lab, on="id")
    def resumir(x):
        esc = [e for e in x if e != "normal"]
        return esc[0] if esc else "normal"
    out = m.groupby("session_id")["escenario"].agg(resumir).reset_index()
    out["es_anomala"] = (out["escenario"] != "normal").astype(int)
    return out


def main():
    logs = cargar_logs()
    logs_s = sesionizar(logs)
    sesiones = construir_sesiones(logs_s)
    os.makedirs(PROC, exist_ok=True)
    sesiones.to_csv(os.path.join(PROC, "sesiones_backup_airlines.csv"), index=False)
    ruta_lab = os.path.join(PROC, "etiquetas_validacion_eventos.csv")
    if os.path.exists(ruta_lab):
        etiquetas_por_sesion(logs_s, ruta_lab).to_csv(
            os.path.join(PROC, "etiquetas_validacion.csv"), index=False)
    print(f"Eventos: {len(logs):,} -> sesiones: {len(sesiones):,} | variables: {len(FEATURES)}")


if __name__ == "__main__":
    main()
