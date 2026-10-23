package com.hemodialyse.backend.contexts.absencespatients.infrastructure.mois;

import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.absencespatients.domain.MoisDAbsencesOuverts;

import java.time.YearMonth;
import java.util.List;

/**
 * Adaptateur provisoire de {@link MoisDAbsencesOuverts} : la clôture mensuelle des absences (A6) n'existe pas encore, tous les mois
 * sont ouverts. Remplacé par la lecture de {@code mois_absences} quand A6 sera faite ; la détection et la notification sautent déjà
 * un mois clos.
 */
public class TousLesMoisSontOuverts implements MoisDAbsencesOuverts {

    @Override
    public boolean estOuvert(CentreId centre, YearMonth mois) {
        return true;
    }

    @Override
    public List<YearMonth> moisClos(CentreId centre) {
        return List.of();
    }
}
