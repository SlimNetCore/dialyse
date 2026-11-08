package com.hemodialyse.backend.domain.seance.port;

import com.hemodialyse.backend.domain.seance.model.DerogationPlanning;
import com.hemodialyse.backend.domain.seance.model.Seance;
import com.hemodialyse.backend.domain.seance.model.SeanceArticleConsumption;
import com.hemodialyse.backend.domain.seance.model.SeanceDetails;
import com.hemodialyse.backend.domain.seance.model.SeanceListItem;
import com.hemodialyse.backend.domain.seance.model.SeanceRecap;
import com.hemodialyse.backend.domain.seance.model.SeanceSearch;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface SeanceUseCase {
    Seance create(CenterId centerId, UUID patientId, LocalDate dateSeance);

    Seance createFromQr(CenterId centerId, String qrCode);

    /**
     * Scan d'un patient par l'infirmier : la séance du jour est <b>validée directement</b>. Une séance « créée »
     * existante passe à « validée » ; sans séance, elle est créée puis validée ; une séance déjà validée, signée ou
     * facturée est renvoyée telle quelle (idempotent : un second scan ne retraite rien).
     * <p>
     * Un patient non programmé ce jour-là n'est pas enregistré sans {@code derogation} (motif confirmé par
     * l'infirmier) ; un jour de fermeture du centre n'est franchi que par une dérogation d'administrateur.
     *
     * @param derogation confirmation d'une séance hors planning, ou {@code null} sans confirmation
     */
    ScanResult scanAndValidate(CenterId centerId, String qrCode, String userId, DerogationPlanning derogation);

    /**
     * Résultat d'un scan.
     *
     * @param created          la séance a été créée par ce scan
     * @param validatedNow     la séance a été validée par ce scan
     * @param alreadyValidated la séance du jour était déjà validée, signée ou facturée
     */
    record ScanResult(Seance seance, boolean created, boolean validatedNow, boolean alreadyValidated) {
    }

    SeanceDetails getDetails(CenterId centerId, UUID seanceId);

    int RECENT_MAX = 10;

    /**
     * Les dernières séances d'un patient (strictement avant {@code before}), de la plus récente à la plus ancienne,
     * avec leurs constantes ; {@code limit} est borné entre 1 et {@value #RECENT_MAX}.
     */
    List<SeanceRecap> recentByPatient(CenterId centerId, UUID patientId, LocalDate before, int limit);

    int SEARCH_MAX_SIZE = 500;

    /**
     * Historique paginé des séances du centre, filtré (période, statut, texte patient) et trié en base ;
     * {@code size} est borné à {@value #SEARCH_MAX_SIZE}.
     */
    PagedResult<SeanceListItem> search(CenterId centerId, SeanceSearch criteria, int page, int size);

    // SE6 (ADR 0093) : le changement de jour vit dans contexts/seance (ChangerLeJourDUneSeance).
    // SE5 (ADR 0092) : l'imposition d'un forfait vit dans contexts/seance (ImposerUnForfait).
    // SE2 (ADR 0089) : validate, signByMedecin et unlockForRegularisation vivent dans contexts/seance (ValiderUneSeance,
    // SignerUneSeance, DeverrouillerUneSeance). SE3 (ADR 0090) : l'ajout, la modification et le retrait d'un consommable vivent
    // dans contexts/seance (AjouterUnConsommable, ModifierLaQuantiteDUnConsommable, RetirerUnConsommable).
}



