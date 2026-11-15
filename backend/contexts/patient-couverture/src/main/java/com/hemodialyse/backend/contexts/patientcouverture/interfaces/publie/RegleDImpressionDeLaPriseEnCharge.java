package com.hemodialyse.backend.contexts.patientcouverture.interfaces.publie;

import com.hemodialyse.backend.contexts.documents.publishedlanguage.RegleDImpressionDUnType;
import com.hemodialyse.backend.contexts.patientcouverture.application.ComposerLaPriseEnChargeImprimee;
import com.hemodialyse.backend.contexts.patientcouverture.application.ComposerLaPriseEnChargeImprimee.PriseEnChargeImprimee;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.hemodialyse.backend.contexts.patientcouverture.interfaces.publie.IdentifiantsDuDocument.date;
import static com.hemodialyse.backend.contexts.patientcouverture.interfaces.publie.IdentifiantsDuDocument.lire;

/**
 * La règle d'impression de la prise en charge, publiée à Documents (documents.v1, DOC5h, ADR 0200). Le type n'est PAS gardé (comportement
 * reproduit) ; le gabarit n'embarque plus de requête, le serveur pose la ligne ({@code LIGNES}) composée par Patient et couverture, sur les
 * deux voies. Un identifiant absent ou vide ne désigne rien (document vide) ; l'identifiant de la prise en charge est lu d'abord et celui du
 * patient seulement s'il est absent (comme l'ancien {@code OR} court-circuité) ; un identifiant lu et illisible reste un échec
 * d'impression (500) au texte de l'ancienne erreur SQL. Mêmes noms de champs (majuscules) et mêmes types que ceux du gabarit.
 */
public final class RegleDImpressionDeLaPriseEnCharge implements RegleDImpressionDUnType {

    public static final String TYPE = "PEC";

    private final ComposerLaPriseEnChargeImprimee priseEnCharge;

    public RegleDImpressionDeLaPriseEnCharge(ComposerLaPriseEnChargeImprimee priseEnCharge) {
        this.priseEnCharge = priseEnCharge;
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public void garder(UUID centreDemande) {
        // Aucune garde : le type n'en avait pas avant la conversion et n'en reçoit pas (comportement reproduit).
    }

    @Override
    public Map<String, Object> donneesDuServeur(UUID centre, Map<String, String> parametres) {
        UUID identifiant = lire(parametres.get("pecId"), "prise_en_charge");
        UUID patient = identifiant != null ? null : lire(parametres.get("patientId"), "prise_en_charge");
        return Map.of("LIGNES", priseEnCharge.executer(centre, identifiant, patient).map(p -> List.of(champs(p))).orElse(List.of()));
    }

    static Map<String, Object> champs(PriseEnChargeImprimee imprimee) {
        var p = imprimee.priseEnCharge();
        Map<String, Object> c = new HashMap<>();
        c.put("NOM", p.nom());
        c.put("PRENOM", p.prenom());
        c.put("NUMERO_ASSURANCE", p.numeroAssurance());
        c.put("DATE_NAISSANCE", date(p.dateNaissance()));
        c.put("DATE_DEBUT_DEMANDE", date(p.dateDebutDemande()));
        c.put("DATE_FIN_DEMANDE", date(p.dateFinDemande()));
        c.put("STATUT", p.statut());
        c.put("DATE_DEBUT_EFFECTIF", date(p.dateDebutEffectif()));
        c.put("DATE_FIN_EFFECTIF", date(p.dateFinEffectif()));
        c.put("CENTER_NAME", imprimee.nomDuCentre());
        return c;
    }
}
