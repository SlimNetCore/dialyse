package com.hemodialyse.backend.infrastructure.acl.planification;

import com.hemodialyse.backend.contexts.planification.publishedlanguage.ReaffectationEnIsolement;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * ACL du dossier médical (sérologie, legacy) vers le langage publié {@link ReaffectationEnIsolement} de la Planification (P5, seam
 * Sérologie, extension à ratifier). L'enregistrement de la sérologie et la réaffectation s'exécutent dans <b>une seule transaction</b> :
 * un patient d'un autre centre (refus {@code PLACEMENT_PATIENT_INTROUVABLE}, 422) ou un verrou du centre non obtenu (409) annule aussi la
 * sérologie (KB-PLN-59, KB-PLN-60).
 */
@Component
public class ReaffectationEnIsolementDeLaSerologie {

    private final ReaffectationEnIsolement reaffectation;
    private final TransactionTemplate transaction;
    private final EntityManager entites;

    public ReaffectationEnIsolementDeLaSerologie(ReaffectationEnIsolement reaffectation, PlatformTransactionManager gestionnaire,
                                                 EntityManager entites) {
        this.reaffectation = reaffectation;
        this.transaction = new TransactionTemplate(gestionnaire);
        this.entites = entites;
    }

    /** Enregistre la sérologie puis réévalue la place du patient, ensemble. */
    public <T> T enregistrerPuisReaffecter(UUID centre, UUID patient, Supplier<T> enregistrement) {
        return transaction.execute(statut -> {
            T resultat = enregistrement.get();
            // La Planification lit les sérologies en SQL : la sérologie écrite par JPA doit être visible dans la transaction.
            entites.flush();
            reaffectation.siRisque(centre, patient);
            return resultat;
        });
    }
}
