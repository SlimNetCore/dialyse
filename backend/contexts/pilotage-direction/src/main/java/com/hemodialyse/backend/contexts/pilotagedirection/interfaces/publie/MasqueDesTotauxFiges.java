package com.hemodialyse.backend.contexts.pilotagedirection.interfaces.publie;

import com.hemodialyse.backend.contexts.pilotagedirection.domain.SeuilDAnonymat;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Suppression complémentaire des totaux appliquée À LA LECTURE d'un instantané mensuel figé (KB-DIR-01 / KB-MED-104, corrigés le
 * 2026-11-09, ADR 0112). Les instantanés figés avant la correction contiennent des totaux de patients bruts : la règle est relue du JSON
 * publié ({@link SeuilDAnonymat#totalPublieDepuis}) et appliquée à l'arbre lu en mémoire ; le contenu stocké ({@code direction_snapshot.payload})
 * n'est JAMAIS réécrit (RG-DIR-090). Idempotente : un instantané capturé après la correction est déjà conforme et ne change pas.
 * <p>
 * Champs visés : {@code overview.totaux.patients|patientsSousKt} contre {@code overview.centres[]} ; les sept effectifs cliniques et, pour les
 * cinq marqueurs, les patients évalués et les taux, de {@code indicators.totaux.clinique} contre {@code indicators.centres[].clinique} ;
 * {@code breakdown.caisseTotaux[].patients} contre {@code breakdown.caisses[]} de même {@code caisseCode}.
 */
final class MasqueDesTotauxFiges {

    private static final List<String> EFFECTIFS_CLINIQUES = List.of("vhbPositifs", "vhcPositifs", "vihPositifs",
            "patientsObservanceEnRetard", "greffeListeAttente", "greffeBilanEnCours", "greffesPeriode");
    private static final List<String> MARQUEURS = List.of("ktV", "hemoglobine", "phosphore", "pth", "albumine");
    private static final List<String> MESURES_D_UN_MARQUEUR = List.of("evalues", "pctDansCible", "pctSousCible", "pctAuDessus");

    private MasqueDesTotauxFiges() {
    }

    static void appliquer(JsonNode overview, JsonNode indicateurs, JsonNode repartition) {
        vueDEnsemble(overview);
        indicateurs(indicateurs);
        repartition(repartition);
    }

    private static Long effectif(JsonNode n) {
        return n == null || n.isMissingNode() || n.isNull() ? null : n.asLong();
    }

    private static boolean objet(JsonNode n) {
        return n != null && n.isObject();
    }

    private static boolean tableau(JsonNode n) {
        return n != null && n.isArray();
    }

    private static void vueDEnsemble(JsonNode overview) {
        if (!objet(overview) || !objet(overview.path("totaux")) || !tableau(overview.path("centres"))) return;
        ObjectNode totaux = (ObjectNode) overview.path("totaux");
        for (String champ : List.of("patients", "patientsSousKt")) {
            List<Long> centres = new ArrayList<>();
            for (JsonNode c : overview.path("centres")) centres.add(effectif(c.path(champ)));
            supprimerSiCompose(totaux, champ, centres);
        }
    }

    private static void indicateurs(JsonNode indicateurs) {
        if (!objet(indicateurs) || !objet(indicateurs.path("totaux").path("clinique")) || !tableau(indicateurs.path("centres"))) return;
        ObjectNode total = (ObjectNode) indicateurs.path("totaux").path("clinique");
        for (String champ : EFFECTIFS_CLINIQUES) {
            List<Long> centres = new ArrayList<>();
            for (JsonNode c : indicateurs.path("centres")) centres.add(effectif(c.path("clinique").path(champ)));
            supprimerSiCompose(total, champ, centres);
        }
        for (String marqueur : MARQUEURS) {
            if (!objet(total.path(marqueur))) continue;
            List<Long> centres = new ArrayList<>();
            for (JsonNode c : indicateurs.path("centres")) centres.add(effectif(c.path("clinique").path(marqueur).path("evalues")));
            if (SeuilDAnonymat.totalPublieDepuis(0L, centres) == null) {
                ObjectNode m = (ObjectNode) total.path(marqueur);
                for (String mesure : MESURES_D_UN_MARQUEUR) m.putNull(mesure);
            }
        }
    }

    private static void repartition(JsonNode repartition) {
        if (!objet(repartition) || !tableau(repartition.path("caisseTotaux")) || !tableau(repartition.path("caisses"))) return;
        for (JsonNode total : repartition.path("caisseTotaux")) {
            if (!objet(total)) continue;
            List<Long> centres = new ArrayList<>();
            for (JsonNode c : repartition.path("caisses")) {
                if (c.path("caisseCode").equals(total.path("caisseCode"))) centres.add(effectif(c.path("patients")));
            }
            supprimerSiCompose((ObjectNode) total, "patients", centres);
        }
    }

    private static void supprimerSiCompose(ObjectNode total, String champ, List<Long> centresPublies) {
        if (!total.has(champ)) return;
        if (SeuilDAnonymat.totalPublieDepuis(effectif(total.path(champ)), centresPublies) == null) total.putNull(champ);
    }
}
