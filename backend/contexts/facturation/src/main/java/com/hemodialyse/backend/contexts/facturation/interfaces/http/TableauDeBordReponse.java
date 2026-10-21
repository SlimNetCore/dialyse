package com.hemodialyse.backend.contexts.facturation.interfaces.http;

import com.hemodialyse.backend.contexts.facturation.application.pilotage.TableauDeBordDuMois;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * Le JSON du tableau de bord de facturation, inchangé depuis le legacy (noms anglais des champs, consommés par le front).
 */
public record TableauDeBordReponse(
        UUID centerId,
        YearMonth month,
        BigDecimal revenueTtc,
        BigDecimal revenueHt,
        long billedSeances,
        long billedPatients,
        long createdInvoices,
        List<Caisse> byInsurance,
        List<Situation> byPatientStatus
) {

    public record Caisse(String code, String label, long seancesCount, long patientsCount) {
    }

    public record Situation(String code, String label, long seancesCount) {
    }

    static TableauDeBordReponse depuis(TableauDeBordDuMois tableau) {
        return new TableauDeBordReponse(tableau.centre(), tableau.mois(), tableau.chiffreDAffairesTtc(), tableau.chiffreDAffairesHt(),
                tableau.seancesFacturees(), tableau.patientsFactures(), tableau.facturesEmises(),
                tableau.parCaisse().stream().map(c -> new Caisse(c.code(), c.libelle(), c.seances(), c.patients())).toList(),
                tableau.parSituation().stream().map(s -> new Situation(s.code(), s.libelle(), s.seances())).toList());
    }
}
