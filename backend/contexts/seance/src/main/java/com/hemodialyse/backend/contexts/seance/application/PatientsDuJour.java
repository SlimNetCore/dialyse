package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.seance.domain.PatientId;
import com.hemodialyse.backend.contexts.seance.domain.SeanceId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.LocalDate;
import java.util.List;

/**
 * Port sortant : les patients qui ont une séance un jour donné dans le centre, avec leur identité (code, nom, prénom) tenue par le
 * Patient. Tous statuts confondus, une ligne par séance, triés par nom puis prénom (KB-SEA-61, reproduit : la liste est servie à tout
 * profil du journal).
 */
public interface PatientsDuJour {

    List<PatientDuJour> deLaJournee(CentreId centre, LocalDate jour);

    /** Le statut reste tel que lu en base (texte) : le journal ne l'interprète pas. */
    record PatientDuJour(SeanceId seance, PatientId patient, LocalDate jour, String statut, String code, String nom, String prenom) {
    }
}
