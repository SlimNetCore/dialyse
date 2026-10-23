package com.hemodialyse.backend.contexts.absencespatients.application;

import com.hemodialyse.backend.contexts.absencespatients.application.BilanDuControle.Echec;
import com.hemodialyse.backend.contexts.absencespatients.domain.AbsencePatient;
import com.hemodialyse.backend.contexts.absencespatients.domain.AbsencesRepository;
import com.hemodialyse.backend.contexts.absencespatients.domain.EcritureConcurrente;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.AbsenceId;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.absencespatients.domain.MoisDAbsencesOuverts;
import com.hemodialyse.backend.contexts.absencespatients.domain.PeriodeDeDetection;
import com.hemodialyse.backend.contexts.absencespatients.domain.PeriodesFacturees;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Politique de réconciliation (RG-ABS-040) : une absence détectée, encore « à qualifier », hors période facturée et dont la
 * séance a finalement été réalisée est annulée (saisie tardive de la séance). Tout se traite en un seul passage, page par page
 * sur un curseur d'identifiant (KB-ABS-23 corrigé). Une absence = une transaction, relue sous verrou ; un échec n'arrête que
 * cette absence. Les jours d'un mois d'absences clos sont laissés.
 */
public class ReconciliationAbsences {

    static final int TAILLE_DE_PAGE = 200;

    private final UniteDeTravail uniteDeTravail;
    private final AbsencesRepository absences;
    private final SeancesRealisees seances;
    private final PeriodesFacturees periodes;
    private final MoisDAbsencesOuverts mois;

    public ReconciliationAbsences(UniteDeTravail uniteDeTravail, AbsencesRepository absences, SeancesRealisees seances,
                                  PeriodesFacturees periodes, MoisDAbsencesOuverts mois) {
        this.uniteDeTravail = uniteDeTravail;
        this.absences = absences;
        this.seances = seances;
        this.periodes = periodes;
        this.mois = mois;
    }

    public BilanDuControle executer(CentreId centre, PeriodeDeDetection periode, Instant maintenant) {
        int annulees = 0;
        List<Echec> echecs = new ArrayList<>();
        List<LocalDate> ignores = new ArrayList<>();
        Map<YearMonth, Boolean> ouverts = new HashMap<>();
        AbsenceId curseur = null;
        List<AbsencePatient> page;
        do {
            page = absences.detecteesAQualifierEntre(centre, periode.du(), periode.au(), curseur, TAILLE_DE_PAGE);
            for (AbsencePatient lue : page) {
                curseur = lue.id();
                YearMonth moisDeLAbsence = YearMonth.from(lue.dateDeSeance());
                if (!ouverts.computeIfAbsent(moisDeLAbsence, m -> mois.estOuvert(centre, m))) {
                    if (!ignores.contains(lue.dateDeSeance())) ignores.add(lue.dateDeSeance());
                    continue;
                }
                if (!seances.realisee(centre, lue.patient(), lue.dateDeSeance())) {
                    continue;
                }
                try {
                    if (reconcilier(centre, lue.id(), maintenant)) annulees++;
                } catch (RuntimeException e) {
                    echecs.add(new Echec(lue.patient(), lue.dateDeSeance(), "réconciliation", cause(e)));
                }
            }
        } while (page.size() == TAILLE_DE_PAGE);
        return new BilanDuControle(0, annulees, echecs, ignores);
    }

    private boolean reconcilier(CentreId centre, AbsenceId id, Instant maintenant) {
        return uniteDeTravail.executer(() -> {
            AbsencePatient absence = absences.verrouiller(centre, id).orElse(null);
            // relue sous verrou : qualifiée, annulée ou facturée depuis la lecture, elle n'est plus touchée
            // le mois est relu sous verrou : clos depuis la lecture (clôture concurrente), l'absence est laissée
            if (absence == null || !mois.estOuvertSousVerrou(centre, YearMonth.from(absence.dateDeSeance()))
                    || !absence.reconciliable(periodes)) {
                return false;
            }
            absence.reconcilier(periodes, maintenant);
            absences.enregistrer(absence);
            return true;
        });
    }

    static String cause(RuntimeException e) {
        return e instanceof EcritureConcurrente ? "écriture concurrente" : e.getClass().getSimpleName() + " : " + e.getMessage();
    }
}
