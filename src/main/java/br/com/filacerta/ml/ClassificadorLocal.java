package br.com.filacerta.ml;

import java.text.Normalizer;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Extrai unidade, setor e "sintomas" a partir do nome do local, exatamente como o notebook de Data Science
 * (funcoes extrair_unidade, extrair_setor e a coluna sintomas). Mantenha as duas versoes iguais.
 */
public final class ClassificadorLocal {

    private static final List<String> UNIDADES = List.of("ENGENHO NOVO", "IMPERIAL", "CAMARGO", "MUTINGA", "HMB");
    private static final List<String> SETORES = List.of(
            "TRIAGEM", "EMERGENCIA", "ORTOPEDIA", "PEDIATRIA", "GINECOLOGIA", "ODONTOLOGIA", "CLINICO");
    private static final Pattern PAA = Pattern.compile("\\bPAA\\b");
    private static final Pattern PAI = Pattern.compile("\\bPAI\\b");
    private static final Pattern SINTOMAS = Pattern.compile("SINTOMAS|SUSPEITA");

    private ClassificadorLocal() {
    }

    static String normalizar(String s) {
        String semAcento = Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFKD).replaceAll("\\p{M}", "");
        return semAcento.replaceAll("[^\\x00-\\x7F]", "").toUpperCase();
    }

    public static String unidade(String local) {
        String n = normalizar(local);
        for (String chave : UNIDADES) {
            if (n.contains(chave)) return chave;
        }
        if (PAA.matcher(n).find()) return "PAA";
        if (PAI.matcher(n).find()) return "PAI";
        return "OUTRA";
    }

    public static String setor(String local) {
        String n = normalizar(local).replace("PRONTO ATENDIMENTO", "");
        for (String chave : SETORES) {
            if (n.contains(chave)) return chave;
        }
        return n.contains("ATENDIMENTO") ? "ATENDIMENTO" : "OUTRO";
    }

    public static int sintomas(String local) {
        return SINTOMAS.matcher(normalizar(local)).find() ? 1 : 0;
    }
}
