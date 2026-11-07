package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto;

import com.hemodialyse.backend.contexts.dossiermedical.application.ConsulterLObservance;

import java.time.LocalDate;

/** L'observance en cours de l'EPO et du fer, champ par champ comme le legacy ({@code null} quand le traitement n'est pas suivi). */
public record ObservanceReponse(Traitement epo, Traitement fer) {

    public record Traitement(LocalDate periodeDebut, LocalDate periodeFin, int dosesAttendues, int dosesAdministrees, int dosesRestantes,
                             long joursRestants, String uniteDose, Integer dosePrescrite, Integer doseAttendue, Integer doseAdministree,
                             Integer doseRestante) {
        static Traitement de(ConsulterLObservance.Observance o) {
            return new Traitement(o.debut(), o.fin(), o.dosesAttendues(), o.dosesAdministrees(), o.dosesRestantes(), o.joursRestants(), o.uniteDose(),
                    o.dosePrescrite(), o.doseAttendue(), o.doseAdministree(), o.doseRestante());
        }
    }

    public static ObservanceReponse de(ConsulterLObservance.Observances o) {
        return new ObservanceReponse(o.epo().map(Traitement::de).orElse(null), o.fer().map(Traitement::de).orElse(null));
    }
}
