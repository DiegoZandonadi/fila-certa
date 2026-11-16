"""Executa as celulas do notebook (secoes 1 a 7.1) fora do Jupyter e gera:
  - ml/_work/modelo.json            (modelo exportado)
  - ml/_work/paridade.json          (casos com a saida do scikit-learn, para testar o Java)
Uso: python executar_e_exportar.py [real|simulado]
"""
import json, os, sys
from pathlib import Path
import matplotlib
matplotlib.use("Agg")
import nbformat

modo = sys.argv[1] if len(sys.argv) > 1 else "simulado"
aqui = Path(__file__).resolve().parent
trabalho = aqui / "_work"
trabalho.mkdir(exist_ok=True)
os.chdir(trabalho)

nb = nbformat.read(aqui / "GS_FilaDigital_DEMAS_corrigido.ipynb", as_version=4)
ns = {"display": print}
parar_em = "# 10.0 - Configura"          # a secao 10 (DEMAS) faz chamadas de rede e nao treina nada
for c in nb.cells:
    if c.cell_type != "code":
        continue
    if parar_em in c.source:
        break
    src = c.source.replace('MODO = "simulado"', f'MODO = "{modo}"', 1)
    exec(compile(src, "<celula>", "exec"), ns)

# casos de paridade: linhas do conjunto de teste com a previsao do sklearn
teste = ns["teste"].sample(12, random_state=1)
NUM, CAT = ns["NUM"], ns["CAT"]
reg, clf = ns["reg"], ns["clf"]
X = teste[NUM + CAT]
casos = []
for (_, linha), prev, prob in zip(X.iterrows(), reg.predict(X), clf.predict_proba(X)[:, 1]):
    casos.append({"num": {k: float(linha[k]) for k in NUM}, "unidade": linha["unidade"], "setor": linha["setor"],
                  "reg": float(prev), "prob": float(prob)})
Path("paridade.json").write_text(json.dumps(casos, indent=2), encoding="utf-8")
print("paridade.json:", len(casos), "casos")
