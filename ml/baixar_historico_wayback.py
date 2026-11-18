"""Baixa as capturas historicas do painel "Tempo medio de espera - PS" (Prefeitura de Barueri) guardadas no
Internet Archive (Wayback Machine) e as transforma em linhas (local, tempo_espera, ts), no mesmo formato do notebook.

Fonte original : https://saudeservicos.barueri.sp.gov.br/AgendamentoOnline/TempoAtendimentoPS.aspx
Arquivo        : https://web.archive.org  (capturas publicas da pagina; nenhum dado pessoal)

Uso: python baixar_historico_wayback.py            -> grava dados/historico_wayback.csv
"""
import io
import json
import re
import time
import urllib.request
from pathlib import Path

import pandas as pd

ORIGEM = "saudeservicos.barueri.sp.gov.br/AgendamentoOnline/TempoAtendimentoPS.aspx"
CDX = ("http://web.archive.org/cdx/search/cdx?url=" + ORIGEM +
       "&output=json&fl=timestamp,statuscode,digest&filter=statuscode:200&collapse=digest")
SAIDA = Path(__file__).resolve().parent / "dados" / "historico_wayback.csv"
UA = {"User-Agent": "Mozilla/5.0 (projeto academico FIAP - GS CitizenTech)"}


def parse_minutos(texto):
    """"2 minutos" -> 2 | "1 h e 36 min" -> 96 | "Sem espera" -> 0 | nao entendido -> None."""
    t = str(texto).lower()
    if "sem " in t:
        return 0.0
    h = re.search(r"(\d+)\s*h", t)
    m = re.search(r"(\d+)\s*min", t)
    if h or m:
        return (int(h.group(1)) * 60 if h else 0) + (int(m.group(1)) if m else 0)
    n = re.search(r"(\d+)", t)
    return float(n.group(1)) if n else None


def baixar(url, tentativas=3):
    for i in range(tentativas):
        try:
            req = urllib.request.Request(url, headers=UA)
            with urllib.request.urlopen(req, timeout=60) as r:
                return r.read().decode("utf-8", errors="ignore")
        except Exception as e:  # rede instavel: tenta de novo
            time.sleep(3 * (i + 1))
            ultimo = e
    raise ultimo


def tabela_da_pagina(html):
    for t in pd.read_html(io.StringIO(html)):
        if t.shape[1] == 3:
            t = t.copy()
            t.columns = ["local", "tempo_txt", "atualizado_em"]
            return t
    return None


def main():
    capturas = json.loads(baixar(CDX))[1:]
    print(f"{len(capturas)} capturas distintas")
    linhas, falhas = [], []
    for i, (carimbo, _, _) in enumerate(capturas, 1):
        url = f"http://web.archive.org/web/{carimbo}id_/https://{ORIGEM}"  # id_ = HTML original, sem a moldura do Archive
        try:
            tab = tabela_da_pagina(baixar(url))
            if tab is None:
                raise ValueError("sem tabela de 3 colunas")
            tab = tab.dropna(subset=["local", "tempo_txt"])
            tab["tempo_espera"] = tab["tempo_txt"].map(parse_minutos)
            tab["ts"] = pd.to_datetime(tab["atualizado_em"], dayfirst=True, errors="coerce")
            tab["captura"] = pd.to_datetime(carimbo, format="%Y%m%d%H%M%S")
            ok = tab.dropna(subset=["tempo_espera", "ts"])
            linhas.append(ok[["local", "tempo_txt", "tempo_espera", "ts", "captura"]])
            print(f"[{i:02d}/{len(capturas)}] {carimbo}: {len(ok)} linhas")
        except Exception as e:
            falhas.append((carimbo, str(e)[:80]))
            print(f"[{i:02d}/{len(capturas)}] {carimbo}: FALHOU ({str(e)[:60]})")
        time.sleep(1.2)  # educado com o servico do Archive

    df = pd.concat(linhas, ignore_index=True)
    df["fonte"] = "Prefeitura de Barueri via Wayback Machine"
    df = df.drop_duplicates(["local", "ts"]).sort_values(["ts", "local"])
    SAIDA.parent.mkdir(parents=True, exist_ok=True)
    df.to_csv(SAIDA, index=False, encoding="utf-8")
    print(f"\n{len(df)} linhas, {df['ts'].nunique()} instantes -> {SAIDA}")
    if falhas:
        print("falhas:", falhas)


if __name__ == "__main__":
    main()
