package com.hemodialyse.backend.contexts.patientcouverture.infrastructure.acl.notifications;

import com.hemodialyse.backend.contexts.notifications.publishedlanguage.Audience;
import com.hemodialyse.backend.contexts.notifications.publishedlanguage.DemandeDeNotification;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Catalogue des notifications émises par Patient et couverture (RG-NOT, ADR 0153) : codes de type conservés tels quels (le front en dépend),
 * clés du contenu, audience. Toutes sont de simples diffusions en temps réel (jamais au journal) destinées à tous les utilisateurs du centre :
 * <ul>
 *   <li>{@code PATIENT_CREATED}, {@code PATIENT_UPDATED} : patientId, patientCode, nom, prenom ;</li>
 *   <li>{@code PEC_VALIDATED}, {@code PEC_CLOSED} : pecId, patientId, patientNom ;</li>
 *   <li>{@code PEC_DELETED} : pecId, patientId (si connu) ;</li>
 *   <li>{@code ATTESTATION_CREATED}, {@code ATTESTATION_DELETED} : attestationId, patientId (si connu).</li>
 * </ul>
 */
final class CatalogueDesNotificationsDeLaFiche {

    private CatalogueDesNotificationsDeLaFiche() {
    }

    static DemandeDeNotification ficheCreee(UUID centre, UUID patient, String code, String nom, String prenom) {
        return pourTous(centre, "PATIENT_CREATED", identite(patient, code, nom, prenom));
    }

    static DemandeDeNotification ficheModifiee(UUID centre, UUID patient, String code, String nom, String prenom) {
        return pourTous(centre, "PATIENT_UPDATED", identite(patient, code, nom, prenom));
    }

    static DemandeDeNotification pecValidee(UUID centre, UUID pec, UUID patient, String nomDuPatient) {
        return pourTous(centre, "PEC_VALIDATED", pecDuPatient(pec, patient, nomDuPatient));
    }

    static DemandeDeNotification pecCloturee(UUID centre, UUID pec, UUID patient, String nomDuPatient) {
        return pourTous(centre, "PEC_CLOSED", pecDuPatient(pec, patient, nomDuPatient));
    }

    static DemandeDeNotification pecSupprimee(UUID centre, UUID pec, UUID patient) {
        return pourTous(centre, "PEC_DELETED", avecPatientFacultatif("pecId", pec, patient));
    }

    static DemandeDeNotification attestationCreee(UUID centre, UUID attestation, UUID patient) {
        return pourTous(centre, "ATTESTATION_CREATED", avecPatientFacultatif("attestationId", attestation, patient));
    }

    static DemandeDeNotification attestationSupprimee(UUID centre, UUID attestation, UUID patient) {
        return pourTous(centre, "ATTESTATION_DELETED", avecPatientFacultatif("attestationId", attestation, patient));
    }

    private static DemandeDeNotification pourTous(UUID centre, String type, Map<String, String> contenu) {
        return new DemandeDeNotification(centre, type, contenu, Audience.tous(), null, false);
    }

    private static Map<String, String> identite(UUID patient, String code, String nom, String prenom) {
        var contenu = new HashMap<String, String>();
        // une valeur absente (fiche héritée aux colonnes nulles) fait échouer l'émission, comme avant (KB : 500 à la modification, golden PC1)
        contenu.put("patientId", patient.toString());
        contenu.put("patientCode", Objects.requireNonNull(code));
        contenu.put("nom", Objects.requireNonNull(nom));
        contenu.put("prenom", Objects.requireNonNull(prenom));
        return contenu;
    }

    private static Map<String, String> pecDuPatient(UUID pec, UUID patient, String nomDuPatient) {
        var contenu = new HashMap<String, String>();
        contenu.put("pecId", pec.toString());
        contenu.put("patientId", patient.toString());
        contenu.put("patientNom", Objects.requireNonNull(nomDuPatient));
        return contenu;
    }

    private static Map<String, String> avecPatientFacultatif(String cle, UUID identifiant, UUID patient) {
        var contenu = new HashMap<String, String>();
        contenu.put(cle, identifiant.toString());
        if (patient != null) {
            contenu.put("patientId", patient.toString());
        }
        return contenu;
    }
}
