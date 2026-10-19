package com.hemodialyse.backend.contexts.comptabilite.domain.ecriture;

import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.journal.CodeJournal;
import com.hemodialyse.backend.contexts.comptabilite.domain.periode.PeriodeOuverte;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Écriture comptable : une pièce équilibrée au centime, d'au moins deux lignes, dans un journal. Racine d'agrégat (RG-CPT-003,
 * RG-CPT-004). Cycle de vie {@code BROUILLON → VALIDEE → EXPORTEE} ; une écriture exportée ne se modifie plus (toute correction
 * passe par une extourne) et garde la date et l'auteur de son export. Les lignes sont immuables : aucune méthode ne les change.
 * <p>
 * L'origine (système, pièce saisie, extourne) se lit dans {@code source} (idempotence : une seule écriture par source) et
 * {@code modele} (le modèle d'une pièce saisie) ; la création et la révision des écritures relèvent des slices C4 à C6.
 */
public final class EcritureComptable {

    private final UUID id;
    private final CentreId centre;
    private final CodeJournal journal;
    private final LocalDate dateEcriture;
    private final LocalDate datePiece;
    private final NumeroDePiece numeroDePiece;
    private final String libelle;
    private final List<LigneEcriture> lignes;
    private final UUID source;
    private final UUID modele;
    private StatutEcriture statut;
    private Instant exporteeLe;
    private String exporteePar;

    private EcritureComptable(UUID id, CentreId centre, CodeJournal journal, LocalDate dateEcriture, LocalDate datePiece,
                              NumeroDePiece numeroDePiece, String libelle, List<LigneEcriture> lignes, StatutEcriture statut,
                              UUID source, UUID modele, Instant exporteeLe, String exporteePar) {
        this.id = id;
        this.centre = centre;
        this.journal = journal;
        this.dateEcriture = dateEcriture;
        this.datePiece = datePiece;
        this.numeroDePiece = numeroDePiece;
        this.libelle = libelle != null ? libelle.trim() : "";
        this.lignes = List.copyOf(lignes);
        this.statut = statut;
        this.source = source;
        this.modele = modele;
        this.exporteeLe = exporteeLe;
        this.exporteePar = exporteePar;
    }

    /**
     * Reconstitue une écriture connue (lue en base ou reçue d'une slice voisine) en revérifiant ses invariants : une écriture
     * illisible (une seule ligne, déséquilibrée) est refusée. <b>Réservée aux adaptateurs</b> : une écriture nouvelle naît par
     * {@link #generer}, {@link #saisir} ou {@link #extourner}, qui exigent une période ouverte (R7).
     *
     * @param source l'opération qui l'a produite (facture, règlement, journée de stock...) ; nulle si aucune
     * @param modele le modèle de pièce d'une pièce saisie ; nul pour une écriture générée par le système
     */
    public static EcritureComptable reconstituer(UUID id, CentreId centre, CodeJournal journal, LocalDate dateEcriture,
                                                 LocalDate datePiece, NumeroDePiece numeroDePiece, String libelle,
                                                 List<LigneEcriture> lignes, StatutEcriture statut, UUID source, UUID modele,
                                                 Instant exporteeLe, String exporteePar) {
        if (id == null || centre == null) {
            throw new IllegalArgumentException("L'identifiant et le centerId sont obligatoires");
        }
        if (journal == null) throw new IllegalArgumentException("Le code journal est obligatoire");
        if (dateEcriture == null || datePiece == null)
            throw new IllegalArgumentException("Les dates sont obligatoires");
        if (numeroDePiece == null) throw new IllegalArgumentException("Le numéro de pièce est obligatoire");
        if (lignes == null || lignes.size() < 2) {
            throw new IllegalArgumentException("Une écriture doit contenir au moins 2 lignes");
        }
        BigDecimal totalDebit = lignes.stream().map(LigneEcriture::debit).reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalCredit = lignes.stream().map(LigneEcriture::credit).reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        if (totalDebit.compareTo(totalCredit) != 0) {
            throw new EcritureDesequilibree(totalDebit, totalCredit);
        }
        return new EcritureComptable(id, centre, journal, dateEcriture, datePiece, numeroDePiece, libelle, lignes,
                statut != null ? statut : StatutEcriture.BROUILLON, source, modele, exporteeLe, exporteePar);
    }

    // ------------------------------------------------------------------ naissance d'une écriture (R7)

    /**
     * Écriture générée par le système (ventes, règlement, stock) pour sa source, validée d'emblée, dans un mois ouvert.
     */
    public static EcritureComptable generer(UUID id, CentreId centre, CodeJournal journal, LocalDate dateEcriture,
                                            LocalDate datePiece, NumeroDePiece numero, String libelle,
                                            List<LigneEcriture> lignes, UUID source, PeriodeOuverte periode) {
        exigerCouverte(periode, centre, dateEcriture);
        return reconstituer(id, centre, journal, dateEcriture, datePiece, numero, libelle, lignes, StatutEcriture.VALIDEE,
                source, null, null, null);
    }

    /**
     * Pièce saisie à partir d'un modèle (RG-CPT-021) : au moins deux lignes une fois les montants nuls omis, équilibrée,
     * validée d'emblée, dans un mois ouvert. Sa source est elle-même : une pièce saisie n'est enregistrée qu'une fois.
     */
    public static EcritureComptable saisir(UUID id, CentreId centre, CodeJournal journal, UUID modele, LocalDate date,
                                           NumeroDePiece numero, String libelle, List<LigneEcriture> lignes,
                                           PeriodeOuverte periode) {
        if (modele == null) throw new IllegalArgumentException("Une pièce saisie a un modèle");
        exigerCouverte(periode, centre, date);
        if (lignes == null || lignes.size() < 2) {
            throw new PieceIncomplete();
        }
        return reconstituer(id, centre, journal, date, date, numero, libelle, lignes, StatutEcriture.VALIDEE, id, modele,
                null, null);
    }

    private static void exigerCouverte(PeriodeOuverte periode, CentreId centre, LocalDate date) {
        if (periode == null || !periode.couvre(centre, date)) {
            throw new IllegalArgumentException("La période ouverte fournie ne couvre pas le " + date);
        }
    }

    /**
     * Refuse d'extourner ce qui ne s'extourne pas : une écriture générée par le système ({@code PIECE_NON_SAISIE}) ou une
     * extourne ({@code PIECE_DEJA_EXTOURNEE}). Le cas d'usage l'appelle avant tout autre contrôle (ordre des refus).
     */
    public void exigerExtournable() {
        if (!estSaisie()) {
            throw new PieceNonSaisie();
        }
        if (estUneExtourne()) {
            throw PieceDejaExtournee.carCestUneExtourne();
        }
    }

    /**
     * Annule cette pièce saisie par une écriture inverse (RG-CPT-022) : même journal, lignes inversées, datée du jour dans un
     * mois ouvert, date de pièce d'origine ; l'écriture d'origine n'est pas modifiée.
     *
     * @param source       la source de l'extourne (une seule extourne par pièce, R6)
     * @param idsDesLignes fournit l'identifiant de chaque ligne inverse
     */
    public EcritureComptable extourner(UUID idExtourne, LocalDate aujourdhui, NumeroDePiece numero, UUID source,
                                       Supplier<UUID> idsDesLignes, PeriodeOuverte periodeDuJour) {
        exigerExtournable();
        exigerCouverte(periodeDuJour, centre, aujourdhui);
        String libelleExtourne = "Extourne " + numeroDePiece + " - " + libelle;
        List<LigneEcriture> inverses = lignes.stream()
                .map(l -> new LigneEcriture(idsDesLignes.get(), l.compte(), libelleExtourne, l.credit(), l.debit(),
                        l.tiers(), l.axes()))
                .toList();
        return reconstituer(idExtourne, centre, journal, aujourdhui, datePiece, numero, libelleExtourne, inverses,
                StatutEcriture.VALIDEE, source, modele, null, null);
    }

    /**
     * Révise en place l'écriture des sorties d'une journée (RG-CPT-013, R21) : mêmes identifiant, numéro, dates, libellé et
     * statut, nouvelles lignes. Refusé si l'écriture est exportée ; le mois de l'écriture doit être ouvert.
     */
    public EcritureComptable reviserLesSorties(List<LigneEcriture> nouvellesLignes, PeriodeOuverte periode) {
        if (estExportee()) {
            throw new EcritureExporteeImmuable();
        }
        exigerCouverte(periode, centre, dateEcriture);
        return reconstituer(id, centre, journal, dateEcriture, datePiece, numeroDePiece, libelle, nouvellesLignes, statut,
                source, modele, exporteeLe, exporteePar);
    }

    /**
     * Vrai pour l'extourne d'une pièce : elle porte un modèle mais sa source n'est pas elle-même.
     */
    public boolean estUneExtourne() {
        return modele != null && source != null && !source.equals(id);
    }

    /**
     * Valide l'écriture. Valider une écriture déjà validée ne change rien (200, B11) ; une écriture exportée ne se revalide pas.
     * Aucun contrôle de période ni de journal (RG-CPT-004, reproduit).
     */
    public void valider() {
        if (statut == StatutEcriture.EXPORTEE) {
            throw new EcritureExporteeImmuable();
        }
        statut = StatutEcriture.VALIDEE;
    }

    /**
     * Marque l'écriture validée comme exportée, avec la date et l'auteur de l'export ; elle est ensuite immuable.
     */
    public void marquerExportee(Instant le, String par) {
        if (statut != StatutEcriture.VALIDEE) {
            throw new EcritureNonValidee();
        }
        statut = StatutEcriture.EXPORTEE;
        exporteeLe = le;
        exporteePar = par;
    }

    public BigDecimal totalDebit() {
        return lignes.stream().map(LigneEcriture::debit).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Vrai pour une pièce saisie à partir d'un modèle (par opposition à une écriture générée par le système).
     */
    public boolean estSaisie() {
        return modele != null;
    }

    public boolean estExportee() {
        return statut == StatutEcriture.EXPORTEE;
    }

    public UUID id() {
        return id;
    }

    public CentreId centre() {
        return centre;
    }

    public CodeJournal journal() {
        return journal;
    }

    public LocalDate dateEcriture() {
        return dateEcriture;
    }

    public LocalDate datePiece() {
        return datePiece;
    }

    public NumeroDePiece numeroDePiece() {
        return numeroDePiece;
    }

    public String libelle() {
        return libelle;
    }

    public List<LigneEcriture> lignes() {
        return lignes;
    }

    public StatutEcriture statut() {
        return statut;
    }

    public UUID source() {
        return source;
    }

    public UUID modele() {
        return modele;
    }

    /**
     * Quand l'écriture a été exportée ; nul si elle ne l'est pas, ou l'a été avant que l'export soit daté.
     */
    public Instant exporteeLe() {
        return exporteeLe;
    }

    /**
     * Qui a exporté l'écriture ; nul comme {@link #exporteeLe()}.
     */
    public String exporteePar() {
        return exporteePar;
    }
}
