package com.hemodialyse.backend.contexts.patientcouverture.interfaces.publie;

import com.hemodialyse.backend.contexts.documents.publishedlanguage.RegleDImpressionDUnType;
import com.hemodialyse.backend.contexts.patientcouverture.application.ListeDesAttestationsAImprimer;
import com.hemodialyse.backend.contexts.patientcouverture.application.ListeDesAttestationsAImprimer.AttestationImprimee;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.sql.Date;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * La règle d'impression de la liste des ouvertures de droit, publiée à Documents (documents.v1, DOC5g, ADR 0199). Le type n'est PAS gardé
 * (comportement reproduit) ; le gabarit n'embarque plus de requête, le serveur pose les lignes ({@code LIGNES}) lues par Patient et
 * couverture pour le centre de la demande, sur les deux voies. Mêmes noms de champs (en majuscules) et mêmes types que ceux que le
 * gabarit déclare ({@code java.sql.Date} pour les deux dates, texte sinon ; une valeur absente reste nulle).
 */
public final class RegleDImpressionDeLaListeDesAttestations implements RegleDImpressionDUnType {

    public static final String TYPE = "LISTE_ATTESTATIONS";

    private final ListeDesAttestationsAImprimer attestations;

    public RegleDImpressionDeLaListeDesAttestations(ListeDesAttestationsAImprimer attestations) {
        this.attestations = attestations;
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
        return Map.of("LIGNES", champs(attestations.duCentre(new CentreId(centre))));
    }

    static List<Map<String, Object>> champs(List<AttestationImprimee> lignes) {
        return lignes.stream().map(RegleDImpressionDeLaListeDesAttestations::champs).toList();
    }

    static Map<String, Object> champs(AttestationImprimee a) {
        Map<String, Object> c = new HashMap<>();
        c.put("CODE_PATIENT", a.codePatient());
        c.put("NOM", a.nom());
        c.put("PRENOM", a.prenom());
        c.put("NUMERO_ASSURANCE", a.numeroAssurance());
        c.put("DATE_DEBUT", date(a.dateDebut()));
        c.put("DATE_FIN", date(a.dateFin()));
        return c;
    }

    private static Date date(LocalDate d) {
        return d == null ? null : Date.valueOf(d);
    }
}
