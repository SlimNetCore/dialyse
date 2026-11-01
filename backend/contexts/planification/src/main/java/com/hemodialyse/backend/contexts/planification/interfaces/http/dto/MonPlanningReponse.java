package com.hemodialyse.backend.contexts.planification.interfaces.http.dto;

import com.hemodialyse.backend.contexts.planification.application.ConsulterMonPlanning.MonPlanning;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.SemaineDeLInfirmier.CaseDeLInfirmier;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.SemaineDeLInfirmier.Creneau;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.SemaineDeLInfirmier.CreneauPersonnel;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.SemaineDeLInfirmier.JourDuPlanning;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.SemaineDeLInfirmier.Salle;

import java.time.LocalDate;
import java.util.List;

public record MonPlanningReponse(InfirmierReponse infirmier, LocalDate debut, LocalDate fin, List<Salle> salles,
                                 List<Creneau> creneaux, List<CreneauPersonnel> mesCreneaux,
                                 List<JourDuPlanning> jours,
                                 List<CaseDeLInfirmier> mesCases) {
    public static MonPlanningReponse depuis(MonPlanning p) {
        var s = p.semaine();
        return new MonPlanningReponse(InfirmierReponse.depuis(p.infirmier()), s.debut(), s.fin(), s.salles(), s.creneaux(),
                s.mesCreneaux(), s.jours(), s.mesCases());
    }
}
