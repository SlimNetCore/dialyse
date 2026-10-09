package com.hemodialyse.backend.domain.comptabilite.service;

import com.hemodialyse.backend.domain.comptabilite.aggregate.EcritureComptable;
import com.hemodialyse.backend.domain.comptabilite.aggregate.ModelePiece;
import com.hemodialyse.backend.domain.comptabilite.entity.LigneEcriture;
import com.hemodialyse.backend.domain.comptabilite.port.EcritureComptableRepositoryPort;
import com.hemodialyse.backend.domain.comptabilite.port.JournalRepositoryPort;
import com.hemodialyse.backend.domain.comptabilite.port.ModelePieceRepositoryPort;
import com.hemodialyse.backend.domain.comptabilite.port.PeriodeComptableRepositoryPort;
import com.hemodialyse.backend.domain.comptabilite.port.PiecesComptablesUseCase;
import com.hemodialyse.backend.domain.comptabilite.port.PlanComptableUseCase;
import com.hemodialyse.backend.domain.comptabilite.valueobject.AxeAnalytique;
import com.hemodialyse.backend.domain.comptabilite.valueobject.Journal;
import com.hemodialyse.backend.domain.comptabilite.valueobject.JournalCode;
import com.hemodialyse.backend.domain.comptabilite.valueobject.SensEcriture;
import com.hemodialyse.backend.domain.comptabilite.valueobject.StatutEcriture;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Service de domaine pur — modèles de pièces d'un centre et pièces saisies à partir de ces modèles.
 * <p>
 * Une pièce saisie est une écriture validée dans le journal de son modèle ; elle obéit aux mêmes règles que les
 * écritures générées (équilibre, période ouverte, numéro de pièce par journal). Elle ne se supprime jamais : une erreur
 * se corrige par une extourne, écriture inverse créée une seule fois.
 */
public class PiecesComptablesService implements PiecesComptablesUseCase {

    private final ModelePieceRepositoryPort modeles;
    private final EcritureComptableRepositoryPort ecritures;
    private final PeriodeComptableRepositoryPort periodes;
    private final JournalRepositoryPort journaux;
    private final PlanComptableUseCase plan;

    public PiecesComptablesService(ModelePieceRepositoryPort modeles, EcritureComptableRepositoryPort ecritures,
                                   PeriodeComptableRepositoryPort periodes, JournalRepositoryPort journaux,
                                   PlanComptableUseCase plan) {
        this.modeles = modeles;
        this.ecritures = ecritures;
        this.periodes = periodes;
        this.journaux = journaux;
        this.plan = plan;
    }

    /**
     * Identifiant source de l'extourne d'une pièce : stable, donc une pièce ne s'extourne qu'une fois.
     */
    static UUID sourceExtourne(UUID ecritureId) {
        return UUID.nameUUIDFromBytes(("EXTOURNE|" + ecritureId).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public PagedResult<ModelePiece> listerModeles(UUID centerId, boolean actifsSeulement, int page, int size) {
        return modeles.findPaged(centerId, actifsSeulement, Math.max(0, page), Math.max(1, Math.min(size, 100)));
    }

    @Override
    public ModelePiece enregistrerModele(ModelePiece modele) {
        UUID centerId = modele.centerId();
        modeles.findByCode(centerId, modele.code()).filter(autre -> !autre.id().equals(modele.id())).ifPresent(autre -> {
            throw new BusinessException("MODELE_CODE_EXISTANT", "Un modèle de pièce porte déjà le code " + modele.code());
        });
        exigerJournalActif(centerId, modele.journal());
        plan.exigerActifs(centerId, modele.lignes().stream().map(ModelePiece.Ligne::compte).toList());
        modeles.save(modele);
        return modele;
    }

    @Override
    public void supprimerModele(UUID centerId, UUID modeleId) {
        if (modeles.findById(centerId, modeleId).isEmpty()) {
            throw new BusinessException("MODELE_INTROUVABLE", "Modèle de pièce introuvable");
        }
        modeles.delete(centerId, modeleId);
    }

    @Override
    public EcritureComptable saisir(SaisirPieceCommand cmd) {
        UUID centerId = cmd.centerId();
        ModelePiece modele = modeles.findById(centerId, cmd.modeleId())
                .orElseThrow(() -> new BusinessException("MODELE_INTROUVABLE", "Modèle de pièce introuvable"));
        if (!modele.actif()) {
            throw new BusinessException("MODELE_INACTIF", "Le modèle " + modele.code() + " est désactivé");
        }
        if (cmd.montants().size() != modele.lignes().size()
                || cmd.montants().stream().anyMatch(m -> m.signum() < 0)) {
            throw new BusinessException("PIECE_MONTANTS_INVALIDES",
                    "Saisissez un montant positif ou nul pour chaque ligne du modèle");
        }
        exigerPeriodeOuverte(centerId, cmd.date());
        exigerJournalActif(centerId, modele.journal());

        String libelle = cmd.libelle() == null || cmd.libelle().isBlank() ? modele.libelle() : cmd.libelle().trim();
        List<AxeAnalytique> axes = List.of(AxeAnalytique.centre(centerId.toString()));
        List<LigneEcriture> lignes = new ArrayList<>();
        for (int i = 0; i < modele.lignes().size(); i++) {
            ModelePiece.Ligne ligne = modele.lignes().get(i);
            BigDecimal montant = cmd.montants().get(i);
            if (montant.signum() == 0) continue;
            String libelleLigne = ligne.libelle() == null ? libelle : ligne.libelle();
            lignes.add(ligne.sens() == SensEcriture.DEBIT
                    ? LigneEcriture.debit(ligne.compte(), libelleLigne, montant, null, axes)
                    : LigneEcriture.credit(ligne.compte(), libelleLigne, montant, null, axes));
        }
        if (lignes.size() < 2) {
            throw new BusinessException("PIECE_MONTANTS_INVALIDES",
                    "Une pièce comporte au moins un montant au débit et un montant au crédit");
        }
        plan.exigerActifs(centerId, lignes.stream().map(LigneEcriture::getCompteSCF).toList());

        UUID id = UUID.randomUUID();
        EcritureComptable ecriture = new EcritureComptable(id, centerId, modele.journal(), cmd.date(), cmd.date(),
                ecritures.nextNumeroPiece(centerId, modele.journal(), cmd.date().getYear()), libelle, lignes,
                StatutEcriture.VALIDEE, id, modele.id());
        ecritures.save(ecriture);
        return ecriture;
    }

    @Override
    public EcritureComptable extourner(UUID centerId, UUID ecritureId, LocalDate aujourdhui) {
        EcritureComptable piece = ecritures.findById(ecritureId, centerId)
                .orElseThrow(() -> new BusinessException("ECRITURE_INTROUVABLE", "Écriture introuvable"));
        if (!piece.estSaisie()) {
            throw new BusinessException("PIECE_NON_SAISIE",
                    "Seule une pièce saisie peut être extournée : cette écriture est générée par le système");
        }
        UUID source = sourceExtourne(ecritureId);
        if (piece.getSourceId() != null && !piece.getSourceId().equals(piece.getId())) {
            throw new BusinessException("PIECE_DEJA_EXTOURNEE", "Une extourne ne s'extourne pas : saisissez une nouvelle pièce");
        }
        if (ecritures.findBySourceId(source, centerId).isPresent()) {
            throw new BusinessException("PIECE_DEJA_EXTOURNEE", "La pièce " + piece.getNumeroPiece() + " est déjà extournée");
        }
        exigerPeriodeOuverte(centerId, aujourdhui);

        String libelle = "Extourne " + piece.getNumeroPiece() + " - " + piece.getLibelle();
        List<LigneEcriture> inverses = piece.getLignes().stream()
                .map(l -> new LigneEcriture(UUID.randomUUID(), l.getCompteSCF(), libelle, l.getMontantCredit(),
                        l.getMontantDebit(), l.getTiersId(), l.getAxes()))
                .toList();
        EcritureComptable extourne = new EcritureComptable(UUID.randomUUID(), centerId, piece.getJournalCode(),
                aujourdhui, piece.getDatePiece(),
                ecritures.nextNumeroPiece(centerId, piece.getJournalCode(), aujourdhui.getYear()), libelle, inverses,
                StatutEcriture.VALIDEE, source, piece.getModeleId());
        ecritures.save(extourne);
        return extourne;
    }

    private void exigerPeriodeOuverte(UUID centerId, LocalDate date) {
        YearMonth periode = YearMonth.from(date);
        if (periodes.isClotured(centerId, periode)) {
            throw new BusinessException("PERIODE_CLOTUREE", "La période " + periode + " est clôturée");
        }
    }

    private void exigerJournalActif(UUID centerId, JournalCode code) {
        List<Journal> connus = journaux.findByCenter(centerId);
        List<Journal> effectifs = connus.isEmpty() ? Journal.parDefaut() : connus;
        if (effectifs.stream().noneMatch(j -> j.code().equals(code) && j.actif())) {
            throw new BusinessException("JOURNAL_INCONNU", "Le journal " + code + " n'existe pas ou est désactivé");
        }
    }
}
