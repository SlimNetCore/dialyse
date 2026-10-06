package com.hemodialyse.backend.application.seance;

import com.hemodialyse.backend.domain.medical.anemie.port.AdministrationTraitementRepositoryPort;
import com.hemodialyse.backend.domain.seance.model.MotifSuppressionSeance;
import com.hemodialyse.backend.domain.seance.model.Seance;
import com.hemodialyse.backend.domain.seance.model.SuppressionSeance;
import com.hemodialyse.backend.domain.seance.port.SeanceRepositoryPort;
import com.hemodialyse.backend.domain.seance.port.SuppressionSeanceJournalPort;
import com.hemodialyse.backend.domain.seance.port.VoletMedicalRepositoryPort;
import com.hemodialyse.backend.domain.seance.port.VoletParamedicalRepositoryPort;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.domain.stock.port.BonSortieUseCase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * Suppression d'une séance par l'administrateur, tout ou rien (une transaction) : la séance doit exister dans le centre
 * et ne pas être facturée ; un motif est exigé. Les consommables et administrations EPO/fer sortis du stock sont
 * restitués, les volets paramédical et médical et les administrations supprimés, puis la suppression est journalisée.
 */
@Service
public class SuppressionSeanceService {

    private final SeanceRepositoryPort seances;
    private final VoletParamedicalRepositoryPort voletsParamedicaux;
    private final VoletMedicalRepositoryPort voletsMedicaux;
    private final AdministrationTraitementRepositoryPort administrations;
    private final BonSortieUseCase sorties;
    private final SuppressionSeanceJournalPort journal;
    private final Clock horloge;

    @Autowired
    public SuppressionSeanceService(SeanceRepositoryPort seances, VoletParamedicalRepositoryPort voletsParamedicaux,
                                    VoletMedicalRepositoryPort voletsMedicaux,
                                    AdministrationTraitementRepositoryPort administrations, BonSortieUseCase sorties,
                                    SuppressionSeanceJournalPort journal) {
        this(seances, voletsParamedicaux, voletsMedicaux, administrations, sorties, journal, Clock.systemUTC());
    }

    SuppressionSeanceService(SeanceRepositoryPort seances, VoletParamedicalRepositoryPort voletsParamedicaux,
                             VoletMedicalRepositoryPort voletsMedicaux,
                             AdministrationTraitementRepositoryPort administrations, BonSortieUseCase sorties,
                             SuppressionSeanceJournalPort journal, Clock horloge) {
        this.seances = seances;
        this.voletsParamedicaux = voletsParamedicaux;
        this.voletsMedicaux = voletsMedicaux;
        this.administrations = administrations;
        this.sorties = sorties;
        this.journal = journal;
        this.horloge = horloge;
    }

    /**
     * @throws BusinessException {@code SEANCE_INTROUVABLE}, {@code SEANCE_FACTUREE_NON_SUPPRIMABLE} ou
     *                           {@code SEANCE_SUPPRESSION_MOTIF_INVALIDE}
     */
    @Transactional
    public SuppressionSeance supprimer(CenterId centerId, UUID seanceId, String motif, String utilisateur) {
        Seance seance = seances.findById(seanceId, centerId)
                .orElseThrow(() -> new BusinessException("SEANCE_INTROUVABLE", "Séance introuvable"));
        SuppressionSeance suppression = SuppressionSeance.de(seance, new MotifSuppressionSeance(motif), utilisateur,
                horloge.instant());
        sorties.annulerSortiesSeance(centerId, seanceId, suppression.supprimePar());
        administrations.deleteBySeanceId(seanceId, centerId);
        voletsParamedicaux.deleteBySeanceId(seanceId, centerId);
        voletsMedicaux.deleteBySeanceId(seanceId, centerId);
        seances.delete(centerId, seanceId);
        journal.enregistrer(suppression);
        return suppression;
    }
}
