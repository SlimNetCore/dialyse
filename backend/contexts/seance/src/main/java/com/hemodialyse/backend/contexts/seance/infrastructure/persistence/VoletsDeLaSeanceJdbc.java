package com.hemodialyse.backend.contexts.seance.infrastructure.persistence;

import com.hemodialyse.backend.contexts.seance.application.VoletsDeLaSeance;
import com.hemodialyse.backend.contexts.seance.domain.MesuresDeSeance;
import com.hemodialyse.backend.contexts.seance.domain.ObservationsMedicales;
import com.hemodialyse.backend.contexts.seance.domain.SeanceId;
import com.hemodialyse.backend.contexts.seance.domain.VoletsMedicaux;
import com.hemodialyse.backend.contexts.seance.domain.VoletsParamedicaux;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;

/**
 * Lecture des deux volets d'une séance pour le détail et les séances récentes (SE7f, ADR 0100) : remplace le fournisseur de plateforme
 * {@code VoletsFournisLegacy}. Mêmes dépôts que les saisies, donc même cloisonnement : un volet posé sous un autre centre que la séance
 * n'est pas vu (KB-SEA-94, reproduit, golden {@code detail_volets_au_centre}).
 */
public class VoletsDeLaSeanceJdbc implements VoletsDeLaSeance {

    private final VoletsParamedicaux paramedicaux;
    private final VoletsMedicaux medicaux;

    public VoletsDeLaSeanceJdbc(JdbcTemplate jdbc) {
        this(new VoletsParamedicauxJdbc(jdbc), new VoletsMedicauxJdbc(jdbc));
    }

    public VoletsDeLaSeanceJdbc(VoletsParamedicaux paramedicaux, VoletsMedicaux medicaux) {
        this.paramedicaux = paramedicaux;
        this.medicaux = medicaux;
    }

    @Override
    public Optional<VoletParamedical> paramedical(CentreId centre, SeanceId seance) {
        return paramedicaux.deLaSeance(centre, seance).map(v -> {
            MesuresDeSeance m = v.mesures();
            return new VoletParamedical(m.poidsAvantKg(), m.poidsApresKg(), m.tensionAvant(), m.tensionApres(), m.dureeMinutes(),
                    m.debitSangMlMin(), m.ultrafiltrationMl(), m.anticoagulant(), m.typeDeDialysat(), m.incidents());
        });
    }

    @Override
    public Optional<VoletMedical> medical(CentreId centre, SeanceId seance) {
        return medicaux.deLaSeance(centre, seance).map(v -> {
            ObservationsMedicales o = v.observations();
            return new VoletMedical(o.prescription(), o.toleranceDeLaSeance(), o.examenClinique(), o.resultatsBiologiques(),
                    o.ajustementsTherapeutiques(), o.conclusionMedicale());
        });
    }
}
