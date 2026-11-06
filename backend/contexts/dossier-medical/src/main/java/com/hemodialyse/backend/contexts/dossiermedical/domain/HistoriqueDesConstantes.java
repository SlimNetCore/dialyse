package com.hemodialyse.backend.contexts.dossiermedical.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

/**
 * Port sortant (lecture) : l'historique des constantes d'un patient dans un centre, tel que la Séance l'a saisi.
 * <p>
 * Contrat (rejoué sur le double et sur la base) : un point par séance du patient dans CE centre, tous statuts de séance ; les
 * séances et volets d'un autre centre sont ignorés ; ordre de la date de séance décroissante puis de la création de la séance
 * décroissante ; une séance sans volet donne des constantes vides ; un patient inconnu, ou d'un autre centre, donne une page
 * vide (KB-MED-03) ; une page au-delà de la dernière est vide mais rend le total.
 */
public interface HistoriqueDesConstantes {

    PageDeConstantes dePatient(CentreId centre, PatientId patient, Pagination pagination);
}
