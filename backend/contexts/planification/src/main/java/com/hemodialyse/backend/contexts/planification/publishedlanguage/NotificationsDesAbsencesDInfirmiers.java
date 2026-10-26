package com.hemodialyse.backend.contexts.planification.publishedlanguage;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Port consommé par la Planification (ADR 0053), fourni par le legacy (centre de notifications : journal durable et diffusion temps
 * réel, qui rafraîchit les écrans de planification). Appelé APRÈS l'enregistrement, hors transaction : un échec de notification
 * n'annule pas l'écriture.
 */
public interface NotificationsDesAbsencesDInfirmiers {

    /**
     * Une absence a été enregistrée par l'administration ou le secrétariat : le médecin est prévenu, l'administrateur aussi si la saisie ne vient pas de lui.
     */
    void absenceEnregistree(UUID centre, String infirmier, LocalDate debut, LocalDate fin, boolean prevenirAdministrateur);

    /**
     * Un infirmier a déclaré lui-même une absence : administration, secrétariat et médecin sont prévenus.
     */
    void absenceDeclareeParLInfirmier(UUID centre, String infirmier, LocalDate debut, LocalDate fin);

    /**
     * Une absence a été retirée par l'administration ou le secrétariat (KB-PLN-34).
     */
    void absenceRetireeParLAdministration(UUID centre, String infirmier, LocalDate debut, LocalDate fin, boolean prevenirAdministrateur);

    /**
     * Un infirmier a retiré lui-même une de ses absences à venir (KB-PLN-34).
     */
    void absenceRetireeParLInfirmier(UUID centre, String infirmier, LocalDate debut, LocalDate fin);
}
