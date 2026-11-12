package com.hemodialyse.backend.contexts.dossiermedical.infrastructure.acl.seance;

import com.hemodialyse.backend.contexts.dossiermedical.application.SeancesDuPatient;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PeriodeDeStatistiques;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Tension;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.StatistiquesDesSeancesPubliees;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * ACL du Dossier medical vers le langage publie de la Seance ({@code seance.v1}, ADR 0099, SE7e ; dependance directe, sans cycle Maven) : les
 * seances et le volet parametical d'un patient viennent de {@link StatistiquesDesSeancesPubliees}. Elle traduit les tensions brutes (texte)
 * en {@link Tension} ; les moyennes restent nulles sans mesure (le zero est une regle du cas d'usage).
 */
public class SeancesDuPatientPubliees implements SeancesDuPatient {

    private final StatistiquesDesSeancesPubliees seances;

    public SeancesDuPatientPubliees(StatistiquesDesSeancesPubliees seances) {
        this.seances = seances;
    }

    @Override
    public MesuresDesSeances lire(CentreId centre, PatientId patient, PeriodeDeStatistiques periode) {
        UUID c = centre.valeur();
        UUID p = patient.valeur();
        LocalDate du = periode.du();
        LocalDate au = periode.au();
        List<TensionsDeSeance> tensions = seances.evolutionDeLaTension(c, p, du, au).stream()
                .map(t -> new TensionsDeSeance(t.date(), Tension.lire(t.tensionAvant()), Tension.lire(t.tensionApres()))).toList();
        return new MesuresDesSeances(seances.nombreDeSeances(c, p, du, au), seances.moyenneDuPoidsAvant(c, p, du, au),
                seances.moyenneDuPoidsApres(c, p, du, au), seances.moyenneDeLUltrafiltration(c, p, du, au),
                seances.evolutionDuPoids(c, p, du, au), tensions);
    }
}
