package com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage;

import java.util.UUID;

/**
 * Port publié (implémenté par la plateforme) : prévient les écrans connectés qu'une prise en charge est validée, clôturée ou
 * supprimée (diffusions {@code PEC_*}). Le nom du patient est le nom RÉEL (décision du 2026-11-03, KB-PAT-27 : plus le littéral
 * « Patient »).
 */
public interface NotificationsDeLaPec {

    void pecValidee(UUID centre, UUID pec, UUID patient, String nomDuPatient);

    void pecCloturee(UUID centre, UUID pec, UUID patient, String nomDuPatient);

    /** @param patient celui de la requête (peut être nul), comme aujourd'hui */
    void pecSupprimee(UUID centre, UUID pec, UUID patient);
}
