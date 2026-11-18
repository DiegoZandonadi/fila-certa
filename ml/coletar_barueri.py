"""Coletor continuo do painel de espera dos Prontos-Atendimentos de Barueri (dados publicos e agregados).

Le a pagina a cada INTERVALO minutos e acrescenta as leituras novas em dados/espera_barueri_ao_vivo.csv
(sem duplicar). Independe do app Java: pode ficar rodando por horas ou dias.

Uso: python coletar_barueri.py [intervalo_min] [duracao_horas]      (padrao: 5 min, sem limite)
"""
import io
import sys
import time
from pathlib import Path

import pandas as pd
import requests

from baixar_historico_wayback import parse_minutos

URL = "https://saudeservicos.barueri.sp.gov.br/AgendamentoOnline/TempoAtendimentoPS.aspx"
CSV = Path(__file__).resolve().parent / "dados" / "espera_barueri_ao_vivo.csv"
HEADERS = {"User-Agent": "Mozilla/5.0 (projeto academico FIAP - GS CitizenTech)"}


def ler_pagina():
    r = requests.get(URL, timeout=30, headers=HEADERS)
    r.raise_for_status()
    tab = next((t for t in pd.read_html(io.StringIO(r.text)) if t.shape[1] == 3), None)
    if tab is None:
        raise ValueError("tabela com 3 colunas nao encontrada (o layout da pagina mudou?)")
    tab.columns = ["local", "tempo_txt", "atualizado_em"]
    tab = tab.dropna(subset=["local", "tempo_txt"]).copy()
    tab["tempo_espera"] = tab["tempo_txt"].map(parse_minutos)  # "1 h e 36 min" = 96
    tab["ts"] = pd.to_datetime(tab["atualizado_em"], dayfirst=True, errors="coerce")
    tab["coletado_em"] = pd.Timestamp.now().floor("s")
    tab["fonte"] = "Prefeitura de Barueri - Tempo de espera PS"
    return tab.dropna(subset=["tempo_espera", "ts"])[["local", "tempo_espera", "ts", "coletado_em", "fonte"]]


def salvar(novo):
    CSV.parent.mkdir(parents=True, exist_ok=True)
    if CSV.exists():
        novo = pd.concat([pd.read_csv(CSV, parse_dates=["ts", "coletado_em"]), novo])
    antes = len(novo)
    novo = novo.drop_duplicates(["local", "ts"]).sort_values(["ts", "local"])
    novo.to_csv(CSV, index=False, encoding="utf-8")
    return len(novo)


def main():
    intervalo = float(sys.argv[1]) if len(sys.argv) > 1 else 5
    fim = time.time() + float(sys.argv[2]) * 3600 if len(sys.argv) > 2 else None
    print(f"Coletando a cada {intervalo:g} min -> {CSV}", flush=True)
    while fim is None or time.time() < fim:
        try:
            total = salvar(ler_pagina())
            print(f"{pd.Timestamp.now():%d/%m %H:%M} ok - {total} linhas no CSV", flush=True)
        except Exception as e:
            print(f"{pd.Timestamp.now():%d/%m %H:%M} falha: {e}", flush=True)
        time.sleep(intervalo * 60)


if __name__ == "__main__":
    main()
