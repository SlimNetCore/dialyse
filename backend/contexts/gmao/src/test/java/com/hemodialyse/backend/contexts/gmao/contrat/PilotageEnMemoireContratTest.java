package com.hemodialyse.backend.contexts.gmao.contrat;

import com.hemodialyse.backend.contexts.gmao.application.equipement.HistoriqueDesStatuts;
import com.hemodialyse.backend.contexts.gmao.application.equipement.LectureEquipements;
import com.hemodialyse.backend.contexts.gmao.application.intervention.LectureInterventions;
import com.hemodialyse.backend.contexts.gmao.application.pilotage.LectureDesComptages;
import com.hemodialyse.backend.contexts.gmao.application.pilotage.LectureDesCouts;
import com.hemodialyse.backend.contexts.gmao.publishedlanguage.GenerateursDuCentre;
import com.hemodialyse.backend.contexts.gmao.doubles.HistoriqueEnMemoire;
import com.hemodialyse.backend.contexts.gmao.doubles.InterventionsEnMemoire;
import com.hemodialyse.backend.contexts.gmao.doubles.ParcEnMemoire;
import com.hemodialyse.backend.contexts.gmao.doubles.PilotageEnMemoire;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementRepository;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.InterventionId;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.InterventionRepository;

/** Les doubles in-memory du pilotage passent le contrat partagé des lectures de consultation et de pilotage. */
class PilotageEnMemoireContratTest extends PilotageContrat {

    private final ParcEnMemoire parc = new ParcEnMemoire();
    private final InterventionsEnMemoire interventions = new InterventionsEnMemoire();
    private final PilotageEnMemoire pilotage = new PilotageEnMemoire(parc, interventions);
    private final HistoriqueEnMemoire historique = new HistoriqueEnMemoire(parc);

    @Override
    protected EquipementRepository equipements() {
        return parc;
    }

    @Override
    protected LectureEquipements lectureEquipements() {
        return parc;
    }

    @Override
    protected InterventionRepository interventions() {
        return interventions;
    }

    @Override
    protected LectureInterventions lectureInterventions() {
        return interventions;
    }

    @Override
    protected HistoriqueDesStatuts historique() {
        return historique;
    }

    @Override
    protected LectureDesComptages comptages() {
        return pilotage;
    }

    @Override
    protected LectureDesCouts couts() {
        return pilotage;
    }

    @Override
    protected GenerateursDuCentre generateursDuCentre() {
        return pilotage;
    }

    @Override
    protected void supprimerLogiquementEquipement(EquipementId id) {
        parc.supprimerLogiquement(id);
    }

    @Override
    protected void supprimerLogiquementIntervention(InterventionId id) {
        interventions.supprimerLogiquement(id);
    }
}
