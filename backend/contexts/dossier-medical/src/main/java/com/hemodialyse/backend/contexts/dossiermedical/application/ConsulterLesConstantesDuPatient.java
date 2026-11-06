package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.HistoriqueDesConstantes;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PageDeConstantes;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Pagination;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;

import java.util.UUID;

/**
 * Consulte l'historique des constantes d'un patient du centre de la session (lecture seule, garde de centre : 403 hors centre).
 * Les constantes sont saisies par la Séance (volet paramédical) ; le Dossier médical ne les duplique pas. Les droits (MEDECIN et
 * ADMIN) sont portés par l'interface.
 */
public class ConsulterLesConstantesDuPatient {

    private final SessionCourante session;
    private final HistoriqueDesConstantes historique;

    public ConsulterLesConstantesDuPatient(SessionCourante session, HistoriqueDesConstantes historique) {
        this.session = session;
        this.historique = historique;
    }

    public PageDeConstantes executer(UUID centreDemande, UUID patient, int page, int taille) {
        var centre = session.centre(centreDemande);
        return historique.dePatient(centre, new PatientId(patient), new Pagination(page, taille));
    }
}
