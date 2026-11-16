"""Gera ml/GS_FilaDigital_DEMAS_corrigido.ipynb a partir do notebook original do grupo (que fica intacto):
  1) corrige a leitura de "1 h e 36 min" (o original lia 1 minuto);
  2) acrescenta a celula 7.1, que exporta os modelos treinados para o app Fila Certa (Java).
Uso: python preparar_notebook.py "<caminho do notebook original>"
"""
import sys
import nbformat

origem = sys.argv[1]
nb = nbformat.read(origem, as_version=4)

CORRECAO_INI = '''def _parse_minutos(t):
    """"2 minutos" -> 2 | "1 h e 36 min" -> 96 | "Sem espera" -> 0."""
    t = str(t).lower()
    if "sem " in t:
        return 0.0
    h = re.search(r"(\d+)\s*h", t)
    m = re.search(r"(\d+)\s*min", t)
    if h or m:
        return (int(h.group(1)) * 60 if h else 0) + (int(m.group(1)) if m else 0)
    n = re.search(r"(\d+)", t)
    return float(n.group(1)) if n else np.nan


def coletar_snapshot():'''

EXPORT = '''# 7.1 - Exportar os modelos para o app Fila Certa (Java)
# Os dois modelos sao lineares: o app so precisa do intercepto, dos coeficientes e do padronizador.
import json

def exportar_modelo(caminho="modelo.json"):
    pre = reg.named_steps["pre"]
    scaler = pre.named_transformers_["num"]
    ohe = pre.named_transformers_["cat"]
    cats = {c: [str(v) for v in valores] for c, valores in zip(CAT, ohe.categories_)}
    lr, lg = reg.named_steps["m"], clf.named_steps["m"]
    n_esperado = len(NUM) + sum(len(v) for v in cats.values())
    assert len(lr.coef_) == n_esperado and len(lg.coef_[0]) == n_esperado, "ordem/tamanho das variaveis mudou"
    saida = {
        "versao": 1,
        "fonte_treino": "real" if MODO == "real" else "SIMULADO",
        "gerado_em": str(pd.Timestamp.now().floor("s")),
        "horizonte_min": HORIZONTE_MIN,
        "limiar_longa": LIMIAR_LONGA,
        "num": NUM,
        "scaler": {"mean": scaler.mean_.tolist(), "scale": scaler.scale_.tolist()},
        "cat": cats,
        "reg": {"intercept": float(lr.intercept_), "coef": lr.coef_.tolist()},
        "clf": {"intercept": float(lg.intercept_[0]), "coef": lg.coef_[0].tolist()},
        "metricas": {
            "mae_min": float(mae), "mae_baseline_min": float(mae_b), "r2": float(r2),
            "precisao": float(precision_score(y_te_clf, pred_c, zero_division=0)),
            "recall": float(recall_score(y_te_clf, pred_c, zero_division=0)),
            "f1": float(f1_score(y_te_clf, pred_c, zero_division=0)),
            "auc": float(roc_auc_score(y_te_clf, proba)) if y_te_clf.nunique() == 2 else 0.0,
        },
    }
    Path(caminho).write_text(json.dumps(saida, indent=2, ensure_ascii=False), encoding="utf-8")
    print("Modelo exportado em", caminho, "| treino:", saida["fonte_treino"])
    print("Copie para src/main/resources/ml/modelo.json (ou aponte filacerta.ml.modelo-path para ele).")
    return saida

modelo_exportado = exportar_modelo()
'''

for c in nb.cells:
    if c.cell_type == "code" and "def coletar_snapshot():" in c.source:
        c.source = c.source.replace("def coletar_snapshot():", CORRECAO_INI, 1)
        c.source = c.source.replace(
            'num = txt.str.extract(r"(\d+)")[0].astype(float)\n    num[txt.str.contains("sem ")] = 0',
            "num = txt.map(_parse_minutos).astype(float)")
        assert "_parse_minutos).astype" in c.source, "substituicao do parser falhou"

idx = next(i for i, c in enumerate(nb.cells) if c.cell_type == "code" and "ConfusionMatrixDisplay(cm" in c.source)
nb.cells.insert(idx + 1, nbformat.v4.new_code_cell(EXPORT))
nb.cells.insert(idx + 1, nbformat.v4.new_markdown_cell(
    "## 7.1 Exportar os modelos para o app Fila Certa\n\nGera `modelo.json` (intercepto, coeficientes e padronizador das duas regressões). "
    "O app em Java aplica esses números às leituras do painel de Barueri para mostrar a previsão em 15 min e a chance de espera longa."))
nbformat.write(nb, "GS_FilaDigital_DEMAS_corrigido.ipynb")
print("ok:", len(nb.cells), "celulas")
