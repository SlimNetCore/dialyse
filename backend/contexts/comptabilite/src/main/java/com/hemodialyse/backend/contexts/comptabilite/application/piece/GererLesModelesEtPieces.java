package com.hemodialyse.backend.contexts.comptabilite.application.piece;

import com.hemodialyse.backend.contexts.comptabilite.application.PageDe;
import com.hemodialyse.backend.contexts.comptabilite.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.ecriture.AxeAnalytique;
import com.hemodialyse.backend.contexts.comptabilite.domain.ecriture.EcritureComptable;
import com.hemodialyse.backend.contexts.comptabilite.domain.ecriture.EcritureComptableRepository;
import com.hemodialyse.backend.contexts.comptabilite.domain.ecriture.EcritureDesequilibree;
import com.hemodialyse.backend.contexts.comptabilite.domain.ecriture.LigneEcriture;
import com.hemodialyse.backend.contexts.comptabilite.domain.ecriture.NumeroDePiece;
import com.hemodialyse.backend.contexts.comptabilite.domain.ecriture.SequenceDesNumerosDePieces;
import com.hemodialyse.backend.contexts.comptabilite.domain.ecriture.StatutEcriture;
import com.hemodialyse.backend.contexts.comptabilite.domain.journal.CodeJournal;
import com.hemodialyse.backend.contexts.comptabilite.domain.periode.PeriodeComptableRepository;
import com.hemodialyse.backend.contexts.comptabilite.domain.piece.ModeleDePiece;
import com.hemodialyse.backend.contexts.comptabilite.domain.piece.ModeleDePieceRepository;
import com.hemodialyse.backend.contexts.comptabilite.publishedlanguage.JournauxDuCentre;
import com.hemodialyse.backend.contexts.comptabilite.publishedlanguage.PlanComptableEnLecture;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Cas d’usage C4 : gestion des modèles, saisie et extourne de pièces comptables.
 */
public final class GererLesModelesEtPieces {

    private final ModeleDePieceRepository modeles;
    private final EcritureComptableRepository ecritures;
    private final PeriodeComptableRepository periodes;
    private final SequenceDesNumerosDePieces numerotation;
    private final PlanComptableEnLecture plan;
    private final JournauxDuCentre journaux;
    private final UniteDeTravail uniteDeTravail;
    private final Clock horloge;

    public GererLesModelesEtPieces(ModeleDePieceRepository modeles, EcritureComptableRepository ecritures,
                                   PeriodeComptableRepository periodes, SequenceDesNumerosDePieces numerotation,
                                   PlanComptableEnLecture plan, JournauxDuCentre journaux,
                                   UniteDeTravail uniteDeTravail, Clock horloge) {
        this.modeles = modeles;
        this.ecritures = ecritures;
        this.periodes = periodes;
        this.numerotation = numerotation;
        this.plan = plan;
        this.journaux = journaux;
        this.uniteDeTravail = uniteDeTravail;
        this.horloge = horloge;
    }

    private static UUID sourceExtourne(UUID id) {
        return UUID.nameUUIDFromBytes(("EXTOURNE|" + id).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static ModeleDePiece.ReglePiece regle(String code, String message) {
        return new ModeleDePiece.ReglePiece(code, message);
    }

    private static void throwRegle(String code, String message) {
        throw regle(code, message);
    }

    public PageDe<ModeleDePiece> lister(CentreId centre, boolean actifs, int page, int taille) {
        return uniteDeTravail.executer(() -> {
            int pageSure = Math.max(0, page);
            int tailleSure = Math.max(1, Math.min(taille, 100));
            ModeleDePieceRepository.PageModeles resultat = modeles.lister(centre, actifs, pageSure, tailleSure);
            return new PageDe<>(resultat.items(), resultat.total(), resultat.page(), resultat.taille());
        });
    }

    public ModeleDePiece enregistrer(ModeleDePiece modele) {
        return uniteDeTravail.executer(() -> {
            modeles.parCode(modele.centre(), modele.code())
                    .filter(existant -> !existant.id().equals(modele.id()))
                    .ifPresent(existant -> throwRegle("MODELE_CODE_EXISTANT",
                            "Un modèle de pièce porte déjà le code " + modele.code()));
            exigerJournalActif(modele.centre(), modele.journal());
            modele.lignes().forEach(ligne -> exigerCompteActif(modele.centre(), ligne.compte()));
            modeles.enregistrer(modele);
            return modele;
        });
    }

    public void supprimer(CentreId centre, UUID id) {
        uniteDeTravail.executer(() -> {
            modeles.parId(centre, id).orElseThrow(() -> regle("MODELE_INTROUVABLE", "Modèle de pièce introuvable"));
            modeles.supprimer(centre, id);
            return null;
        });
    }

    public EcritureComptable saisir(CommandeSaisie commande) {
        return uniteDeTravail.executer(() -> {
            ModeleDePiece modele = modeles.parId(commande.centre(), commande.modeleId())
                    .orElseThrow(() -> regle("MODELE_INTROUVABLE", "Modèle de pièce introuvable"));
            if (!modele.actif()) throw regle("MODELE_INACTIF", "Le modèle " + modele.code() + " est désactivé");
            if (commande.montants().size() != modele.lignes().size()
                    || commande.montants().stream().anyMatch(m -> m.signum() < 0)) {
                throw regle("PIECE_MONTANTS_INVALIDES", "Saisissez un montant positif ou nul pour chaque ligne du modèle");
            }
            exigerPeriodeOuverte(commande.centre(), commande.date());
            exigerJournalActif(commande.centre(), modele.journal());

            String libelle = commande.libelle() == null || commande.libelle().isBlank()
                    ? modele.libelle() : commande.libelle().trim();
            List<AxeAnalytique> axes = List.of(new AxeAnalytique("CENTRE", "Centre " + commande.centre().valeur()));
            List<LigneEcriture> lignes = new ArrayList<>();
            for (int i = 0; i < modele.lignes().size(); i++) {
                ModeleDePiece.Ligne ligne = modele.lignes().get(i);
                BigDecimal montant = commande.montants().get(i);
                if (montant.signum() == 0) continue;
                exigerCompteActif(commande.centre(), ligne.compte());
                String libelleLigne = ligne.libelle() == null ? libelle : ligne.libelle();
                lignes.add(new LigneEcriture(UUID.randomUUID(), ligne.compte(), libelleLigne,
                        ligne.sens() == ModeleDePiece.Sens.DEBIT ? montant : BigDecimal.ZERO,
                        ligne.sens() == ModeleDePiece.Sens.CREDIT ? montant : BigDecimal.ZERO,
                        null, axes));
            }
            if (lignes.size() < 2) {
                throw regle("PIECE_MONTANTS_INVALIDES", "Une pièce comporte au moins un montant au débit et un montant au crédit");
            }
            UUID id = UUID.randomUUID();
            EcritureComptable ecriture;
            try {
                ecriture = EcritureComptable.reconstituer(id, commande.centre(), modele.journal(), commande.date(),
                        commande.date(), prochainNumero(commande.centre(), modele.journal(), commande.date().getYear()),
                        libelle, lignes, StatutEcriture.VALIDEE, id, modele.id(), null, null);
            } catch (EcritureDesequilibree e) {
                throw regle("ECRITURE_DESEQUILIBREE", e.getMessage());
            }
            ecritures.enregistrer(ecriture);
            return ecriture;
        });
    }

    public EcritureComptable extourner(CentreId centre, UUID id) {
        return uniteDeTravail.executer(() -> {
            EcritureComptable origine = ecritures.parId(centre, id)
                    .orElseThrow(() -> regle("ECRITURE_INTROUVABLE", "Écriture introuvable"));
            if (!origine.estSaisie()) {
                throw regle("PIECE_NON_SAISIE", "Seule une pièce saisie peut être extournée : cette écriture est générée par le système");
            }
            if (origine.source() != null && !origine.source().equals(origine.id())) {
                throw regle("PIECE_DEJA_EXTOURNEE", "Une extourne ne s'extourne pas : saisissez une nouvelle pièce");
            }
            LocalDate aujourdhui = LocalDate.now(horloge);
            UUID source = sourceExtourne(id);
            numerotation.verrouillerSource(centre, source);
            if (ecritures.parSource(centre, source).isPresent()) {
                throw regle("PIECE_DEJA_EXTOURNEE", "La pièce " + origine.numeroDePiece() + " est déjà extournée");
            }
            List<String> numerosExistants = numerotation.verrouillerEtLireLesNumeros(
                    centre, origine.journal(), aujourdhui.getYear());
            exigerPeriodeOuverte(centre, aujourdhui);
            String libelle = "Extourne " + origine.numeroDePiece() + " - " + origine.libelle();
            List<LigneEcriture> inverses = origine.lignes().stream()
                    .map(l -> new LigneEcriture(UUID.randomUUID(), l.compte(), libelle, l.credit(), l.debit(), l.tiers(), l.axes()))
                    .toList();
            EcritureComptable extourne = EcritureComptable.reconstituer(UUID.randomUUID(), centre, origine.journal(),
                    aujourdhui, origine.datePiece(), NumeroDePiece.suivant(origine.journal(), aujourdhui.getYear(), numerosExistants),
                    libelle, inverses, StatutEcriture.VALIDEE, source, origine.modele(), null, null);
            ecritures.enregistrer(extourne);
            return extourne;
        });
    }

    private void exigerPeriodeOuverte(CentreId centre, LocalDate date) {
        periodes.duMois(centre, YearMonth.from(date)).filter(p -> p.estCloturee())
                .ifPresent(p -> throwRegle("PERIODE_CLOTUREE", "La période " + p.mois() + " est clôturée"));
    }

    private NumeroDePiece prochainNumero(CentreId centre, CodeJournal journal, int annee) {
        List<String> existants = numerotation.verrouillerEtLireLesNumeros(centre, journal, annee);
        return NumeroDePiece.suivant(journal, annee, existants);
    }

    private void exigerJournalActif(CentreId centre, CodeJournal journal) {
        boolean actif = journaux.journaux(centre.valeur()).stream()
                .anyMatch(j -> j.code().equals(journal.valeur()) && j.actif());
        if (!actif) throw regle("JOURNAL_INCONNU", "Le journal " + journal + " n'existe pas ou est désactivé");
    }

    private void exigerCompteActif(CentreId centre, String compte) {
        if (!plan.estUnCompteActif(centre.valeur(), compte)) {
            throw regle("COMPTE_INCONNU", "Le compte " + compte + " n'existe pas dans le plan comptable du centre ou est désactivé");
        }
    }

    public record CommandeSaisie(CentreId centre, UUID modeleId, LocalDate date, String libelle,
                                 List<BigDecimal> montants) {
        public CommandeSaisie {
            if (centre == null || modeleId == null)
                throw new IllegalArgumentException("Le centre et le modèle sont obligatoires");
            if (date == null) throw new IllegalArgumentException("La date de la pièce est obligatoire");
            montants = montants == null ? List.of() : montants.stream().map(m -> m == null ? BigDecimal.ZERO : m).toList();
        }
    }
}
