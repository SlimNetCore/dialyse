package com.hemodialyse.backend.contexts.dossiermedical.domain;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tension arterielle d'une seance lue d'un texte saisi par l'infirmier (« 130/80 », « 120-70 », « 140/90 mmHg »). Seuls 2 ou 3 chiffres de
 * part et d'autre de {@code /} ou {@code -} sont lus (suffixe libre) ; tout autre texte est ILLISIBLE et rendu sans valeur, sans signal
 * (KB-PL4-3, REPRODUIT du legacy : dette consignee, a confirmer avec les infirmiers).
 */
public record Tension(Integer systolique, Integer diastolique) {

    public static final Tension ILLISIBLE = new Tension(null, null);

    private static final Pattern FORME = Pattern.compile("^\\s*(\\d{2,3})\\s*[/\\-]\\s*(\\d{2,3})");

    public static Tension lire(String brut) {
        if (brut == null) return ILLISIBLE;
        Matcher lecture = FORME.matcher(brut);
        if (!lecture.find()) return ILLISIBLE;
        return new Tension(Integer.valueOf(lecture.group(1)), Integer.valueOf(lecture.group(2)));
    }

    public boolean estLisible() {
        return systolique != null;
    }
}
