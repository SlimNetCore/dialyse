package com.hemodialyse.backend.contexts.comptabilite.application.piece;

import com.hemodialyse.backend.contexts.comptabilite.application.PageDe;
import com.hemodialyse.backend.contexts.comptabilite.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.ecriture.AxeAnalytique;
import com.hemodialyse.backend.contexts.comptabilite.domain.ecriture.EcritureComptable;
import com.hemodialyse.backend.contexts.comptabilite.domain.ecriture.EcritureComptableRepository;
import com.hemodialyse.backend.contexts.comptabilite.domain.ecriture.LigneEcriture;
import com.hemodialyse.backend.contexts.comptabilite.domain.ecriture.NumeroDePiece;
import com.hemodialyse.backend.contexts.comptabilite.domain.ecriture.PieceDejaExtournee;
import com.hemodialyse.backend.contexts.comptabilite.domain.ecriture.SequenceDesNumerosDePieces;
import com.hemodialyse.backend.contexts.comptabilite.domain.journal.CodeJournal;
import com.hemodialyse.backend.contexts.comptabilite.domain.periode.PeriodeComptableRepository;
import com.hemodialyse.backend.contexts.comptabilite.domain.periode.PeriodeOuverte;
import com.hemodialyse.backend.contexts.comptabilite.domain.piece.ModeleDePiece;
import com.hemodialyse.backend.contexts.comptabilite.domain.piece.ModeleDePieceRepository;
import com.hemodialyse.backend.contexts.comptabilite.publishedlanguage.JournauxDuCentre;
import com.hemodialyse.backend.contexts.comptabilite.publishedlanguage.PlanComptableEnLecture;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * Cas d’usage C4 : gestion des modèles, saisie et extourne de pièces comptables. Les règles de la pièce (composition,
 * période ouverte, extourne) sont portées par le modèle de pièce et l'écriture ; le cas d'usage orchestre les lectures
 * (modèle, période, journal, plan comptable) et la numérotation sous verrou.
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
            String libelle = commande.libelle() == null || commande.libelle().isBlank()
                    ? modele.libelle() : commande.libelle().trim();
            List<AxeAnalytique> axes = List.of(new AxeAnalytique("CENTRE", "Centre " + commande.centre().valeur()));
            List<LigneEcriture> lignes = modele.composerLaPiece(commande.montants(), libelle, axes, UUID::randomUUID);
            PeriodeOuverte periode = periodes.duMoisOuOuverte(commande.centre(), YearMonth.from(commande.date()))
                    .exigerOuverteALaSaisie();
            exigerJournalActif(commande.centre(), modele.journal());
            lignes.forEach(ligne -> exigerCompteActif(commande.centre(), ligne.compte()));

            EcritureComptable ecriture = EcritureComptable.saisir(UUID.randomUUID(), commande.centre(), modele.journal(),
                    modele.id(), commande.date(),
                    prochainNumero(commande.centre(), modele.journal(), commande.date().getYear()),
                    libelle, lignes, periode);
            ecritures.enregistrer(ecriture);
            return ecriture;
        });
    }

    public EcritureComptable extourner(CentreId centre, UUID id) {
        return uniteDeTravail.executer(() -> {
            EcritureComptable origine = ecritures.parId(centre, id)
                    .orElseThrow(() -> regle("ECRITURE_INTROUVABLE", "Écriture introuvable"));
            origine.exigerExtournable();
            LocalDate aujourdhui = LocalDate.now(horloge);
            UUID source = sourceExtourne(id);
            numerotation.verrouillerSource(centre, source);
            if (ecritures.parSource(centre, source).isPresent()) {
                throw PieceDejaExtournee.dejaFaite(origine.numeroDePiece());
            }
            List<String> numerosExistants = numerotation.verrouillerEtLireLesNumeros(
                    centre, origine.journal(), aujourdhui.getYear());
            PeriodeOuverte periodeDuJour = periodes.duMoisOuOuverte(centre, YearMonth.from(aujourdhui))
                    .exigerOuverteALaSaisie();
            EcritureComptable extourne = origine.extourner(UUID.randomUUID(), aujourdhui,
                    NumeroDePiece.suivant(origine.journal(), aujourdhui.getYear(), numerosExistants), source,
                    UUID::randomUUID, periodeDuJour);
            ecritures.enregistrer(extourne);
            return extourne;
        });
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
