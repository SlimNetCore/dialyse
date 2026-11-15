package com.hemodialyse.backend.contexts.patientcouverture.interfaces.publie;

import com.hemodialyse.backend.contexts.documents.publishedlanguage.RegleDImpressionDUnType;
import com.hemodialyse.backend.contexts.patientcouverture.application.ListeDesPatientsAImprimer;
import com.hemodialyse.backend.contexts.patientcouverture.application.ListeDesPatientsAImprimer.PatientImprime;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.sql.Date;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * La règle d'impression de la liste des patients, publiée à Documents (documents.v1, DOC5f, ADR 0198). Le type n'est PAS gardé (tout
 * utilisateur authentifié l'imprime : KB-DOC1-3, inchangé) ; le gabarit n'embarque plus de requête, le serveur pose les lignes de la
 * liste ({@code LIGNES}) lues par Patient et couverture pour le centre de la demande, sur les deux voies. Mêmes noms de champs (en
 * majuscules) et mêmes types que ceux que le gabarit déclare ({@code java.sql.Date} pour les deux dates, texte sinon ; une valeur
 * absente reste nulle).
 */
public final class RegleDImpressionDeLaListeDesPatients implements RegleDImpressionDUnType {

    public static final String TYPE = "LISTE_PATIENTS";

    private final ListeDesPatientsAImprimer patients;

    public RegleDImpressionDeLaListeDesPatients(ListeDesPatientsAImprimer patients) {
        this.patients = patients;
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
        return Map.of("LIGNES", champs(patients.duCentre(new CentreId(centre))));
    }

    static List<Map<String, Object>> champs(List<PatientImprime> lignes) {
        return lignes.stream().map(RegleDImpressionDeLaListeDesPatients::champs).toList();
    }

    static Map<String, Object> champs(PatientImprime p) {
        Map<String, Object> c = new HashMap<>();
        c.put("CODE_PATIENT", p.codePatient());
        c.put("NOM", p.nom());
        c.put("PRENOM", p.prenom());
        c.put("SEXE", p.sexe());
        c.put("GROUPE_SANGUIN", p.groupeSanguin());
        c.put("DATE_NAISSANCE", date(p.dateNaissance()));
        c.put("DATE_ADMISSION", date(p.dateAdmission()));
        c.put("NUMERO_ASSURANCE", p.numeroAssurance());
        c.put("TEL_MOBILE", p.telMobile());
        c.put("ETAT_PATIENT", p.etatPatient());
        return c;
    }

    private static Date date(LocalDate d) {
        return d == null ? null : Date.valueOf(d);
    }
}
