package com.hemodialyse.backend.contexts.absencespatients.contrat;

import com.hemodialyse.backend.contexts.absencespatients.doubles.AbsencesEnMemoire;
import com.hemodialyse.backend.contexts.absencespatients.domain.AbsencesRepository;
import com.hemodialyse.backend.contexts.absencespatients.domain.EcritureConcurrente;
import com.hemodialyse.backend.contexts.absencespatients.domain.StatutAbsence;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contrat des absences exécuté contre le double en mémoire, qui tient aussi l'unicité du rattrapage (index unique partiel de
 * PostgreSQL, migration V43).
 */
class AbsencesEnMemoireContratTest extends AbsencesContrat {

    private final AbsencesEnMemoire absences = new AbsencesEnMemoire();

    @Override
    protected AbsencesRepository absences() {
        return absences;
    }

    @Test
    void une_seance_ne_rattrape_qu_une_absence_rattrapee_du_patient() {
        absences.creer(dansLEtat(1, CENTRE, AMRANI, JOUR, StatutAbsence.RATTRAPEE, JOUR.plusDays(3)));
        absences.creer(aQualifier(2, CENTRE, AMRANI, JOUR.plusDays(1)));
        var seconde = absences.verrouiller(CENTRE, id(2)).orElseThrow();
        seconde.rattraper(JOUR.plusDays(3), MEDECIN, com.hemodialyse.backend.contexts.absencespatients.domain.DroitDeCorrection.ACCORDE,
                (c, p, j) -> false, new FaitsPermissifs(), JOUR.plusDays(10), INSTANT);

        assertThatThrownBy(() -> absences.enregistrer(seconde)).isInstanceOf(EcritureConcurrente.class);
    }

    private static final class FaitsPermissifs implements com.hemodialyse.backend.contexts.absencespatients.domain.FaitsDuRattrapage {
        @Override
        public boolean seanceRealisee(java.time.LocalDate jour) {
            return true;
        }

        @Override
        public boolean dejaUtilisee(java.time.LocalDate jour) {
            return false;
        }
    }
}
