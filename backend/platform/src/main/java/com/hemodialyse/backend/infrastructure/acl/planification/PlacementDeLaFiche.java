package com.hemodialyse.backend.infrastructure.acl.planification;

import com.hemodialyse.backend.contexts.planification.publishedlanguage.PlacementDesPatients;
import com.hemodialyse.backend.domain.planning.model.JourSemaine;
import com.hemodialyse.backend.infrastructure.security.CenterAccessGuard;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.DayOfWeek;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * ACL de la fiche du patient (legacy) vers le langage publié {@link PlacementDesPatients} de la Planification (P5, seam Patient
 * autorisé le 2026-10-27). Elle : (1) applique la garde de centre (le {@code centerId} du corps doit être celui du jeton, sinon 403,
 * KB-PLN-52) ; (2) traduit les jours de la fiche dans le langage publié ; (3) exécute le contrôle de la place <b>et</b> l'écriture de la
 * fiche dans une même transaction, pour que le verrou du centre tienne jusqu'à l'enregistrement (KB-PLN-53). Le refus de la
 * Planification (422 {@code PLACEMENT_*}) et l'écriture concurrente (409) sont rendus par les conseils du module. La fiche écrit encore
 * les colonnes de place elle-même : session 3.
 */
@Component
public class PlacementDeLaFiche {

    private final PlacementDesPatients placements;
    private final CenterAccessGuard garde;
    private final TransactionTemplate transaction;

    public PlacementDeLaFiche(PlacementDesPatients placements, CenterAccessGuard garde, PlatformTransactionManager gestionnaire) {
        this.placements = placements;
        this.garde = garde;
        this.transaction = new TransactionTemplate(gestionnaire);
    }

    /**
     * Contrôle la place demandée (tient le verrou du centre) puis exécute l'enregistrement de la fiche, dans une seule transaction.
     *
     * @param patientId {@code null} à la création de la fiche
     */
    public <T> T verifierPuisEnregistrer(UUID centerId, UUID patientId, UUID salleId, UUID creneauId, UUID generateurId,
                                         Set<JourSemaine> jours, Supplier<T> enregistrement) {
        UUID centre = garde.requireCenter(centerId).value();
        Set<DayOfWeek> joursCivils = EnumSet.noneOf(DayOfWeek.class);
        if (jours != null) {
            for (JourSemaine jour : jours) {
                joursCivils.add(switch (jour) {
                    case DIMANCHE -> DayOfWeek.SUNDAY;
                    case LUNDI -> DayOfWeek.MONDAY;
                    case MARDI -> DayOfWeek.TUESDAY;
                    case MERCREDI -> DayOfWeek.WEDNESDAY;
                    case JEUDI -> DayOfWeek.THURSDAY;
                    case VENDREDI -> DayOfWeek.FRIDAY;
                    case SAMEDI -> DayOfWeek.SATURDAY;
                });
            }
        }
        return transaction.execute(statut -> {
            placements.verifier(centre, patientId, salleId, creneauId, generateurId, joursCivils);
            return enregistrement.get();
        });
    }
}
