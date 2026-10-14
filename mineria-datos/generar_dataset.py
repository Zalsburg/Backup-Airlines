"""
generar_dataset.py
==================
Genera el dataset SIMULADO de bitácora de actividad de Backup Airlines.

¿Por qué simulado?
------------------
La aplicación guarda su bitácora (tabla activity_logs) en una base H2 en memoria,
por lo que no existe todavía un histórico real de uso por parte de empresas.
Siguiendo la Situación C de la guía del curso, en lugar de cambiar de proyecto se
construye un dataset sintético que respeta EXACTAMENTE el esquema y el vocabulario
de acciones que registra la aplicación:

    ActivityLog(id, user, action, file, createdAt)
    action ∈ {UPLOAD_FILE, DOWNLOAD_FILE, PREVIEW_FILE, SHARE_FILE,
              DELETE_FILE, RESTORE_VERSION}

Los perfiles de uso se basan en lo observado en el Anexo 2 (Problematización):
uso en horario laboral, versiones manejadas por el personal de documentación,
consultas del área de calidad y cumplimiento antes de auditorías, etc.

Además se inyectan, de forma controlada, sesiones con comportamientos de riesgo
(descarga masiva, borrado masivo, compartición masiva, actividad fuera de horario).
La etiqueta de esos escenarios se guarda en un archivo APARTE
(etiquetas_validacion.csv) que NUNCA se usa para entrenar: solo sirve para
comprobar si el modelo no supervisado es capaz de encontrarlos.

Cuando la aplicación tenga uso real, este archivo se reemplaza por el CSV que
exporta el endpoint /admin/logs/export.csv con las mismas columnas.

Uso:
    python mineria-datos/generar_dataset.py
Salida:
    data/raw/activity_logs_simulados.csv
    data/processed/etiquetas_validacion.csv  (session_key -> escenario)
"""
from __future__ import annotations

import os
from datetime import datetime, timedelta

import numpy as np
import pandas as pd

SEMILLA = 42
INICIO = datetime(2026, 7, 1)
DIAS = 91                      # 1 jul 2026 – 29 sep 2026 (13 semanas)
N_ARCHIVOS = 600               # archivos existentes en la plataforma
TASA_ANOMALIAS = 0.035         # proporción aproximada de sesiones de riesgo

RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RAW = os.path.join(RAIZ, "data", "raw")
PROC = os.path.join(RAIZ, "data", "processed")

# Perfil por rol: sesiones/día laboral, eventos por sesión y mezcla de acciones
ACCIONES = ["UPLOAD_FILE", "DOWNLOAD_FILE", "PREVIEW_FILE",
            "SHARE_FILE", "DELETE_FILE", "RESTORE_VERSION"]
PERFILES = {
    "coordinador_documentacion": dict(n=8, ses_dia=1.6, ev=(4, 18),
        mezcla=[0.34, 0.18, 0.26, 0.10, 0.06, 0.06]),
    "auxiliar_operaciones": dict(n=16, ses_dia=1.3, ev=(3, 12),
        mezcla=[0.40, 0.15, 0.35, 0.03, 0.05, 0.02]),
    "calidad_cumplimiento": dict(n=8, ses_dia=1.1, ev=(3, 16),
        mezcla=[0.08, 0.40, 0.45, 0.04, 0.01, 0.02]),
    "administrador": dict(n=3, ses_dia=0.7, ev=(2, 10),
        mezcla=[0.10, 0.20, 0.40, 0.05, 0.20, 0.05]),
}

rng = np.random.default_rng(SEMILLA)


def crear_usuarios() -> pd.DataFrame:
    filas = []
    for rol, p in PERFILES.items():
        for i in range(1, p["n"] + 1):
            filas.append({"username": f"{rol.split('_')[0][:5]}{i:02d}", "rol": rol})
    return pd.DataFrame(filas)


def eventos_de_sesion(inicio: datetime, n_ev: int, acciones_prob, gap_seg=(20, 600),
                      archivos=None):
    """Devuelve lista de (timestamp, action, file_id) con huecos < 30 min."""
    t = inicio
    salida = []
    for _ in range(n_ev):
        accion = rng.choice(ACCIONES, p=acciones_prob)
        archivo = int(rng.choice(archivos)) if archivos is not None else int(rng.integers(1, N_ARCHIVOS + 1))
        salida.append((t, accion, archivo))
        t = t + timedelta(seconds=int(rng.integers(*gap_seg)))
    return salida


def hora_laboral() -> tuple[int, int]:
    # Jornada 7:00–18:00 con picos a media mañana y media tarde
    h = rng.choice([7, 8, 9, 10, 11, 13, 14, 15, 16, 17],
                   p=[0.04, 0.12, 0.14, 0.13, 0.10, 0.07, 0.12, 0.11, 0.09, 0.08])
    return int(h), int(rng.integers(0, 60))


def generar():
    usuarios = crear_usuarios()
    registros, etiquetas = [], []
    session_key = 0

    for _, u in usuarios.iterrows():
        p = PERFILES[u.rol]
        # Cada usuario trabaja sobre un subconjunto "habitual" de archivos
        habituales = rng.choice(np.arange(1, N_ARCHIVOS + 1), size=60, replace=False)
        for d in range(DIAS):
            dia = INICIO + timedelta(days=d)
            laboral = dia.weekday() < 5
            # Algunos sábados hay turno de operaciones (normal en servicios aeroportuarios)
            if not laboral:
                if not (dia.weekday() == 5 and u.rol == "auxiliar_operaciones" and rng.random() < 0.15):
                    continue
            n_ses = rng.poisson(p["ses_dia"] if laboral else 0.8)
            ultimo_fin = None
            horas = sorted(hora_laboral() for _ in range(n_ses))
            for (h, m) in horas:
                inicio = dia.replace(hour=h, minute=m)
                if ultimo_fin and inicio < ultimo_fin + timedelta(minutes=45):
                    inicio = ultimo_fin + timedelta(minutes=int(rng.integers(45, 120)))
                n_ev = int(rng.integers(*p["ev"]))
                mezcla = p["mezcla"]
                # Preparación de auditoría: fin de mes, calidad descarga más (comportamiento NORMAL pero alto)
                if u.rol == "calidad_cumplimiento" and dia.day >= 25 and rng.random() < 0.35:
                    n_ev = int(rng.integers(15, 35))
                    mezcla = [0.02, 0.60, 0.33, 0.03, 0.00, 0.02]
                evs = eventos_de_sesion(inicio, n_ev, mezcla, archivos=habituales)
                session_key += 1
                for t, a, f in evs:
                    registros.append((t, u.username, u.rol, a, f, session_key))
                etiquetas.append((session_key, "normal"))
                ultimo_fin = evs[-1][0]

    n_normales = len(etiquetas)
    n_anom = int(n_normales * TASA_ANOMALIAS)
    escenarios = ["descarga_masiva", "borrado_masivo", "comparticion_masiva", "fuera_de_horario"]
    for k in range(n_anom):
        u = usuarios.sample(1, random_state=int(rng.integers(0, 1e9))).iloc[0]
        esc = escenarios[k % len(escenarios)]
        dia = INICIO + timedelta(days=int(rng.integers(0, DIAS)))
        if esc == "fuera_de_horario":
            h = int(rng.choice([0, 1, 2, 3, 4, 22, 23]))
            inicio = dia.replace(hour=h, minute=int(rng.integers(0, 60)))
            n_ev = int(rng.integers(6, 25))
            mezcla = [0.05, 0.55, 0.20, 0.15, 0.05, 0.00]
            evs = eventos_de_sesion(inicio, n_ev, mezcla, gap_seg=(15, 300))
        else:
            h, m = hora_laboral()
            inicio = dia.replace(hour=h, minute=m)
            if esc == "descarga_masiva":
                n_ev, mezcla = int(rng.integers(40, 120)), [0.0, 0.92, 0.05, 0.03, 0.0, 0.0]
            elif esc == "borrado_masivo":
                n_ev, mezcla = int(rng.integers(15, 50)), [0.0, 0.05, 0.05, 0.0, 0.90, 0.0]
            else:  # comparticion_masiva (enlaces públicos)
                n_ev, mezcla = int(rng.integers(12, 40)), [0.0, 0.10, 0.10, 0.80, 0.0, 0.0]
            # Ráfagas rápidas sobre archivos que NO son los habituales del usuario
            evs = eventos_de_sesion(inicio, n_ev, mezcla, gap_seg=(2, 40))
        session_key += 1
        for t, a, f in evs:
            registros.append((t, u.username, u.rol, a, f, session_key))
        etiquetas.append((session_key, esc))

    logs = pd.DataFrame(registros, columns=["created_at", "username", "rol", "action", "file_id", "session_key"])
    logs = logs.sort_values(["created_at", "username"]).reset_index(drop=True)
    logs.insert(0, "id", np.arange(1, len(logs) + 1))
    logs["created_at"] = logs["created_at"].dt.strftime("%Y-%m-%d %H:%M:%S")

    os.makedirs(RAW, exist_ok=True)
    os.makedirs(PROC, exist_ok=True)
    # El CSV de bitácora NO incluye session_key: la sesión se reconstruye en preparacion.py
    logs.drop(columns=["session_key"]).to_csv(os.path.join(RAW, "activity_logs_simulados.csv"), index=False)
    # Mapa evento -> escenario, solo para validación
    lab = pd.DataFrame(etiquetas, columns=["session_key", "escenario"])
    ev_lab = logs[["id", "session_key"]].merge(lab, on="session_key")[["id", "escenario"]]
    ev_lab.to_csv(os.path.join(PROC, "etiquetas_validacion_eventos.csv"), index=False)
    print(f"Eventos: {len(logs):,} | sesiones generadas: {len(lab):,} | inyectadas: {n_anom}")


if __name__ == "__main__":
    generar()
