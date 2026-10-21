package com.hemodialyse.backend.infrastructure.persistence.adapter.migration;

import com.hemodialyse.backend.contexts.facturation.publishedlanguage.RepriseDesSoldes;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.RepriseDesSoldes.SoldeDOuverture;
import com.hemodialyse.backend.domain.migration.port.OpeningBalancePort;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Soldes d'ouverture de la Migration : traduit le port de la Migration vers le langage publié de la Facturation
 * ({@link RepriseDesSoldes}), qui possède les tables de factures (slice F6).
 */
@Component
public class OpeningBalanceJdbcAdapter implements OpeningBalancePort {

    private final RepriseDesSoldes reprise;

    public OpeningBalanceJdbcAdapter(RepriseDesSoldes reprise) {
        this.reprise = reprise;
    }

    static SoldeDOuverture solde(OpeningInvoice i) {
        return new SoldeDOuverture(i.patientId(), i.numero(), i.dateFacture(), i.periodStart(), i.periodEnd(),
                i.montantTtc(), i.montantRegle(), i.dateReglement(), i.libelle(), i.patientCode(), i.patientFullName(),
                i.patientStatus(), i.numeroImmatriculation(), i.centrePayeurId());
    }

    @Override
    public Optional<UUID> findByNumero(CenterId centerId, String numero) {
        return reprise.factureDuNumero(centerId.value(), numero);
    }

    @Override
    public long countPaymentsOutsideMigration(CenterId centerId, UUID factureId) {
        return reprise.reglementsHorsReprise(centerId.value(), factureId);
    }

    @Override
    public UUID create(CenterId centerId, OpeningInvoice invoice) {
        return reprise.ouvrir(centerId.value(), solde(invoice));
    }

    @Override
    public void replace(CenterId centerId, UUID factureId, OpeningInvoice invoice) {
        reprise.remplacer(centerId.value(), factureId, solde(invoice));
    }
}
