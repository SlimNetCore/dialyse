package com.hemodialyse.backend.contexts.patientcouverture.interfaces.publie;

import com.hemodialyse.backend.contexts.documents.publishedlanguage.RegleDImpressionDUnType;
import com.hemodialyse.backend.contexts.patientcouverture.application.ListeDesPrisesEnChargeAImprimer;
import com.hemodialyse.backend.contexts.patientcouverture.application.ListeDesPrisesEnChargeAImprimer.PriseEnChargeImprimee;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.sql.Date;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * La règle d'impression de la liste des prises en charge, publiée à Documents (documents.v1, DOC5g, ADR 0199). Le type n'est PAS gardé
 * (comportement reproduit) ; le gabarit n'embarque plus de requête, le serveur pose les lignes ({@code LIGNES}) lues par Patient et
 * couverture pour le centre de la demande, sur les deux voies. Mêmes noms de champs (en majuscules) et mêmes types que ceux que le
 * gabarit déclare ({@code java.sql.Date} pour les deux dates, texte sinon ; une valeur absente reste nulle).
 */
public final class RegleDImpressionDeLaListeDesPrisesEnCharge implements RegleDImpressionDUnType {

    public static final String TYPE = "LISTE_PEC";

    private final ListeDesPrisesEnChargeAImprimer prisesEnCharge;

    public RegleDImpressionDeLaListeDesPrisesEnCharge(ListeDesPrisesEnChargeAImprimer prisesEnCharge) {
        this.prisesEnCharge = prisesEnCharge;
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
        return Map.of("LIGNES", champs(prisesEnCharge.duCentre(new CentreId(centre))));
    }

    static List<Map<String, Object>> champs(List<PriseEnChargeImprimee> lignes) {
        return lignes.stream().map(RegleDImpressionDeLaListeDesPrisesEnCharge::champs).toList();
    }

    static Map<String, Object> champs(PriseEnChargeImprimee p) {
        Map<String, Object> c = new HashMap<>();
        c.put("CODE_PATIENT", p.codePatient());
        c.put("NOM", p.nom());
        c.put("PRENOM", p.prenom());
        c.put("NUMERO_ASSURANCE", p.numeroAssurance());
        c.put("DATE_DEBUT_DEMANDE", date(p.dateDebutDemande()));
        c.put("DATE_FIN_DEMANDE", date(p.dateFinDemande()));
        c.put("STATUT", p.statut());
        return c;
    }

    private static Date date(LocalDate d) {
        return d == null ? null : Date.valueOf(d);
    }
}
