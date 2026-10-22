package com.hemodialyse.backend.contexts.absencespatients.interfaces.facturation;

import com.hemodialyse.backend.contexts.absencespatients.application.DeclarerUneAbsence;
import com.hemodialyse.backend.contexts.absencespatients.application.Intervenant;
import com.hemodialyse.backend.contexts.absencespatients.application.RetirerLAbsenceDeLExclusion;
import com.hemodialyse.backend.contexts.absencespatients.domain.DroitDeCorrection;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.AbsenceId;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.UtilisateurId;
import com.hemodialyse.backend.contexts.absencespatients.domain.MotifAbsence;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.DeclarationDesAbsences;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Adapter entrant fourni à la Facturation : le langage publié {@link DeclarationDesAbsences} (ADR 0031). L'exclusion d'une
 * séance déclare une absence par le cas d'usage du contexte, donc avec ses règles (délai de déclaration, date, période déjà
 * facturée, une absence par patient et par jour, {@code AUTRE} avec commentaire) et dans la transaction de l'exclusion : un
 * refus annule l'exclusion. L'auteur est l'utilisateur de la session, sans droit de correction. La réinclusion retire
 * l'absence que l'exclusion avait déclarée, tant que l'utilisateur ne l'a pas travaillée.
 * <p>
 * Table de correspondance des motifs : la Facturation et les Absences nomment leurs motifs de la même façon.
 */
public class ExclusionDeLaFacturation implements DeclarationDesAbsences {

    private final DeclarerUneAbsence declarer;
    private final RetirerLAbsenceDeLExclusion retirer;

    public ExclusionDeLaFacturation(DeclarerUneAbsence declarer, RetirerLAbsenceDeLExclusion retirer) {
        this.declarer = declarer;
        this.retirer = retirer;
    }

    /**
     * Le motif des Absences de même nom que celui de la Facturation.
     */
    static MotifAbsence motifDesAbsences(String motifDeLaFacturation) {
        return MotifAbsence.valueOf(motifDeLaFacturation);
    }

    @Override
    public UUID declarer(UUID centre, UUID patient, LocalDate dateDeSeance, String motif, String commentaire, UUID auteur) {
        Intervenant intervenant = new Intervenant(auteur == null ? null : new UtilisateurId(auteur), DroitDeCorrection.REFUSE);
        return declarer.executer(new CentreId(centre), new DeclarerUneAbsence.Commande(new PatientId(patient), dateDeSeance,
                motifDesAbsences(motif), commentaire), intervenant).absence().id().valeur();
    }

    @Override
    public void retirer(UUID centre, UUID absence) {
        retirer.executer(new CentreId(centre), new AbsenceId(absence));
    }
}
