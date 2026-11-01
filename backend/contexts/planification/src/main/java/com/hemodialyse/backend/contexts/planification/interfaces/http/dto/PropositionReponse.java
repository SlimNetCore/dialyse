package com.hemodialyse.backend.contexts.planification.interfaces.http.dto;

import com.hemodialyse.backend.contexts.planification.domain.JourDeLaSemaine;

import java.util.List;

public record PropositionReponse(SalleReponse salle, CreneauReponse creneau, GenerateurReponse generateur,
                                 List<GenerateurReponse> generateursAlternatifs, List<JourDeLaSemaine> jours, int score,
                                 List<String> raisons, List<FermetureReponse> fermetures) {
}
