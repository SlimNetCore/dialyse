package com.hemodialyse.backend.contexts.comptabilite.domain.mapping;

import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.journal.CodeJournal;
import com.hemodialyse.backend.contexts.comptabilite.domain.journal.Journal;
import com.hemodialyse.backend.contexts.comptabilite.domain.plan.CompteInconnu;
import com.hemodialyse.backend.contexts.comptabilite.domain.plan.NumeroDeCompte;
import com.hemodialyse.backend.contexts.comptabilite.domain.plan.NumeroDeCompteInvalide;
import com.hemodialyse.backend.contexts.comptabilite.domain.plan.PlanDuCentre;

import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Correspondance, pour un centre, entre chaque opération comptable et les comptes et le journal qu'elle utilise (R15,
 * RG-CPT-006, 010, 011). Une opération sans journal choisi garde son journal par défaut. La TVA collectée est facultative.
 * Le mapping ne réimpute jamais le passé : il s'applique aux écritures générées après lui. Le centre qui n'a rien enregistré
 * a le {@linkplain #parDefaut mapping par défaut}.
 */
public final class MappingComptable {

    private final CentreId centre;
    private final String compteVentes;
    private final String compteClientPatient;
    private final String compteClientDefaut;
    private final String compteBanque;
    private final String compteCaisse;
    private final String compteTvaCollectee;
    private final ComptesDuStock stock;
    private final Map<OperationComptable, CodeJournal> journaux;

    private MappingComptable(CentreId centre, String compteVentes, String compteClientPatient, String compteClientDefaut,
                             String compteBanque, String compteCaisse, String compteTvaCollectee, ComptesDuStock stock,
                             Map<OperationComptable, CodeJournal> journauxChoisis) {
        if (centre == null) {
            throw new IllegalArgumentException("Le centre est obligatoire");
        }
        this.centre = centre;
        this.compteVentes = compteObligatoire(compteVentes, "ventes");
        this.compteClientPatient = compteObligatoire(compteClientPatient, "client patient");
        this.compteClientDefaut = compteObligatoire(compteClientDefaut, "client par défaut");
        this.compteBanque = compteObligatoire(compteBanque, "banque");
        this.compteCaisse = compteObligatoire(compteCaisse, "caisse");
        this.compteTvaCollectee = compteTvaCollectee == null || compteTvaCollectee.isBlank() ? null : compteTvaCollectee.trim();
        this.stock = stock == null ? ComptesDuStock.parDefaut() : stock;
        Map<OperationComptable, CodeJournal> complets = new EnumMap<>(OperationComptable.class);
        for (OperationComptable operation : OperationComptable.values()) {
            CodeJournal choisi = journauxChoisis == null ? null : journauxChoisis.get(operation);
            complets.put(operation, choisi == null ? operation.journalParDefaut() : choisi);
        }
        this.journaux = Map.copyOf(complets);
    }

    static String compteObligatoire(String compte, String nom) {
        if (compte == null || compte.isBlank()) {
            throw new IllegalArgumentException("Le compte " + nom + " est obligatoire");
        }
        return compte.trim();
    }

    /**
     * Un mapping tel que la base le décrit (un choix absent vaut le journal par défaut ; les contrôles d'enregistrement ne s'y appliquent pas).
     */
    public static MappingComptable reconstitue(CentreId centre, String compteVentes, String compteClientPatient,
                                               String compteClientDefaut, String compteBanque, String compteCaisse,
                                               String compteTvaCollectee, ComptesDuStock stock,
                                               Map<OperationComptable, CodeJournal> journauxChoisis) {
        return new MappingComptable(centre, compteVentes, compteClientPatient, compteClientDefaut, compteBanque, compteCaisse,
                compteTvaCollectee, stock, journauxChoisis);
    }

    /**
     * Mapping d'un centre qui n'a rien paramétré : 706, 411100, 411500, 512, 530, 44571, stock 322/602/408/757/657, journaux VE/BQ/CA/AC/ST/ST.
     */
    public static MappingComptable parDefaut(CentreId centre) {
        return new MappingComptable(centre, "706", "411100", "411500", "512", "530", "44571", ComptesDuStock.parDefaut(), Map.of());
    }

    /**
     * Le mapping redéfini : les cinq comptes obligatoires et la TVA collectée sont remplacés tels que saisis (une TVA blanche
     * efface le compte) ; les comptes du stock et les journaux non précisés gardent leur valeur actuelle (KB-CPT-15).
     *
     * @param comptesDuStockSaisis comptes du stock saisis ; un compte absent ou blanc garde le compte actuel
     * @param journauxChoisis      journaux choisis pour certaines opérations seulement
     */
    public MappingComptable redefini(String compteVentes, String compteClientPatient, String compteClientDefaut,
                                     String compteBanque, String compteCaisse, String compteTvaCollectee,
                                     ComptesDuStockSaisis comptesDuStockSaisis, Map<OperationComptable, CodeJournal> journauxChoisis) {
        Map<OperationComptable, CodeJournal> choix = new EnumMap<>(journaux);
        choix.putAll(journauxChoisis);
        return new MappingComptable(centre, compteVentes, compteClientPatient, compteClientDefaut, compteBanque, compteCaisse,
                compteTvaCollectee, comptesDuStockSaisis.surLesComptesActuels(stock), choix);
    }

    /**
     * Refuse un journal choisi qui n'est pas un journal actif du centre ({@code JOURNAL_INCONNU}). Contrôlé avant les comptes ;
     * toutes les opérations sont revalidées, pas seulement celles qui changent (KB-CPT-15), dans l'ordre des opérations.
     */
    public void exigerJournauxActifs(Collection<Journal> journauxDuCentre) {
        for (OperationComptable operation : OperationComptable.values()) {
            CodeJournal choisi = journaux.get(operation);
            boolean actif = journauxDuCentre.stream().anyMatch(j -> j.code().equals(choisi) && j.actif());
            if (!actif) {
                throw new JournalInconnu(choisi, operation);
            }
        }
    }

    /**
     * Refuse un compte qui n'est pas un compte actif du plan du centre ({@code COMPTE_INCONNU}) ; le premier fautif est cité.
     * Un numéro en minuscules est refusé même si le plan en a la version en majuscules : le mapping garde la comparaison
     * exacte (KB-CPT-04).
     */
    public void exigerComptesDuPlan(PlanDuCentre plan) {
        for (String compte : comptes()) {
            NumeroDeCompte numero;
            try {
                numero = NumeroDeCompte.de(compte);
            } catch (NumeroDeCompteInvalide e) {
                throw new CompteInconnu(compte);
            }
            if (!numero.valeur().equals(compte)) {
                throw new CompteInconnu(compte);
            }
            plan.exigerCompteActif(centre, numero);
        }
    }

    /**
     * Tous les comptes que le mapping utilise (un tel compte ne peut être ni désactivé ni supprimé), sans doublon, dans l'ordre de contrôle.
     */
    public Set<String> comptes() {
        Set<String> comptes = new LinkedHashSet<>(List.of(compteVentes, compteClientPatient, compteClientDefaut,
                compteBanque, compteCaisse, stock.stock(), stock.consommation(), stock.facturesNonParvenues(),
                stock.boniInventaire(), stock.maliInventaire()));
        if (compteTvaCollectee != null) {
            comptes.add(compteTvaCollectee);
        }
        return comptes;
    }

    public CodeJournal journalDe(OperationComptable operation) {
        return journaux.get(operation);
    }

    public CentreId centre() {
        return centre;
    }

    public String compteVentes() {
        return compteVentes;
    }

    public String compteClientPatient() {
        return compteClientPatient;
    }

    public String compteClientDefaut() {
        return compteClientDefaut;
    }

    public String compteBanque() {
        return compteBanque;
    }

    public String compteCaisse() {
        return compteCaisse;
    }

    /**
     * Vide si le centre n'a pas de compte de TVA collectée dans ce mapping.
     */
    public Optional<String> compteTvaCollectee() {
        return Optional.ofNullable(compteTvaCollectee);
    }

    public ComptesDuStock stock() {
        return stock;
    }
}
