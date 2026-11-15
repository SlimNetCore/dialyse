package com.hemodialyse.backend.contexts.patientcouverture.interfaces.publie;

import com.hemodialyse.backend.contexts.documents.publishedlanguage.RegleDImpressionDUnType;
import com.hemodialyse.backend.contexts.patientcouverture.application.ComposerLAttestationImprimee;
import com.hemodialyse.backend.contexts.patientcouverture.application.ComposerLAttestationImprimee.AttestationImprimee;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.hemodialyse.backend.contexts.patientcouverture.interfaces.publie.IdentifiantsDuDocument.date;
import static com.hemodialyse.backend.contexts.patientcouverture.interfaces.publie.IdentifiantsDuDocument.lire;

/**
 * La règle d'impression de l'attestation d'ouverture de droit, publiée à Documents (documents.v1, DOC5h, ADR 0200). Le type n'est PAS gardé
 * (comportement reproduit) ; le gabarit n'embarque plus de requête, le serveur pose la ligne ({@code LIGNES}) composée par Patient et
 * couverture, sur les deux voies. Un identifiant absent ou vide ne désigne rien (document vide) ; l'identifiant de l'attestation est lu
 * d'abord et celui du patient seulement s'il est absent (comme l'ancien {@code OR} court-circuité) ; un identifiant lu et illisible reste un
 * échec d'impression (500) au texte de l'ancienne erreur SQL. Mêmes noms de champs (majuscules) et mêmes types que ceux du gabarit.
 */
public final class RegleDImpressionDeLAttestation implements RegleDImpressionDUnType {

    public static final String TYPE = "ATTESTATION";

    private final ComposerLAttestationImprimee attestation;

    public RegleDImpressionDeLAttestation(ComposerLAttestationImprimee attestation) {
        this.attestation = attestation;
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
        UUID identifiant = lire(parametres.get("attestationId"), "attestation");
        UUID patient = identifiant != null ? null : lire(parametres.get("patientId"), "attestation");
        return Map.of("LIGNES", attestation.executer(centre, identifiant, patient).map(a -> List.of(champs(a))).orElse(List.of()));
    }

    static Map<String, Object> champs(AttestationImprimee imprimee) {
        var a = imprimee.attestation();
        Map<String, Object> c = new HashMap<>();
        c.put("NOM", a.nom());
        c.put("PRENOM", a.prenom());
        c.put("SEXE", a.sexe());
        c.put("DATE_NAISSANCE", date(a.dateNaissance()));
        c.put("NUMERO_ASSURANCE", a.numeroAssurance());
        c.put("ADRESSE", a.adresse());
        c.put("TEL_MOBILE", a.telephoneMobile());
        c.put("DATE_DEBUT", date(a.dateDebut()));
        c.put("DATE_FIN", date(a.dateFin()));
        c.put("CENTER_NAME", imprimee.nomDuCentre());
        return c;
    }
}
