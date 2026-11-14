package com.hemodialyse.backend.contexts.dossiermedical.infrastructure.acl.comptabilite;

import com.hemodialyse.backend.contexts.comptabilite.publishedlanguage.NumerotationDesDocuments;
import com.hemodialyse.backend.contexts.dossiermedical.application.NumerotationDesOrdonnances;
import com.hemodialyse.backend.contexts.dossiermedical.domain.EcritureConcurrente;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.UncategorizedSQLException;

/**
 * ACL du Dossier médical vers la numérotation des documents de la Comptabilité (S-DDL2, ADR 0185, décision D7) : le numéro
 * {@code ORD-nnnnn} d'une ordonnance est le type {@code SEQ_ORD} du centre, réservé dans la transaction de la signature (un refus de la
 * signature ne consomme aucun numéro). Le Dossier médical ne fait plus de SQL sur {@code app_settings}. Un verrou de ligne refusé
 * (état SQL 55P03, délai dépassé) reste traduit en {@link EcritureConcurrente}, comme avant la slice.
 */
public class NumerotationDesOrdonnancesParLaComptabilite implements NumerotationDesOrdonnances {

    private static final String TYPE = "SEQ_ORD";
    private static final String PREFIXE = "ORD-";

    private final NumerotationDesDocuments numerotation;

    public NumerotationDesOrdonnancesParLaComptabilite(NumerotationDesDocuments numerotation) {
        this.numerotation = numerotation;
    }

    @Override
    public String suivant(CentreId centre) {
        try {
            return numerotation.suivant(centre.valeur(), TYPE, PREFIXE);
        } catch (PessimisticLockingFailureException | QueryTimeoutException | UncategorizedSQLException e) {
            if (e instanceof UncategorizedSQLException technique && !"55P03".equals(technique.getSQLException().getSQLState())) {
                throw e;
            }
            throw new EcritureConcurrente();
        }
    }
}
