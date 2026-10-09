package com.hemodialyse.backend.domain.comptabilite.valueobject;

import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Paramétrage comptable d'un centre : comptes SCF et journal de chaque opération.
 * Configurable par centerId — jamais codé en dur dans le domaine.
 * <p>
 * Le compte client d'une facture est celui de son <b>payeur</b> (paramétré payeur par payeur, voir
 * {@code ComptePayeurRepositoryPort}) ; à défaut {@link #compteClientDefaut}. Un patient qui paie lui-même relève de
 * {@link #compteClientPatient}.
 *
 * @param compteClientPatient compte client des patients qui paient eux-mêmes (facture sans payeur)
 * @param compteClientDefaut  compte client des payeurs qui n'ont pas de compte propre
 * @param compteTVACollectee  facultatif ({@code null} si le centre ne collecte pas de TVA)
 * @param stock               comptes du stock (inventaire permanent)
 * @param journaux            journal de chaque opération ; une opération absente utilise son journal par défaut
 */
public record MappingComptable(
        UUID centerId,
        String compteVentes,          // ex. 706
        String compteClientPatient,   // ex. 411100
        String compteClientDefaut,    // ex. 411500
        String compteBanque,          // ex. 512
        String compteCaisse,          // ex. 530
        String compteTVACollectee,    // ex. 44571
        ComptesStock stock,
        Map<OperationComptable, JournalCode> journaux
) {
    public MappingComptable {
        if (centerId == null) throw new IllegalArgumentException("Le centerId est obligatoire");
        compteVentes = exiger(compteVentes, "ventes");
        compteClientPatient = exiger(compteClientPatient, "client patient");
        compteClientDefaut = exiger(compteClientDefaut, "client par défaut");
        compteBanque = exiger(compteBanque, "banque");
        compteCaisse = exiger(compteCaisse, "caisse");
        compteTVACollectee = compteTVACollectee == null || compteTVACollectee.isBlank()
                ? null : compteTVACollectee.trim();
        stock = stock == null ? ComptesStock.parDefaut() : stock;
        Map<OperationComptable, JournalCode> complets = new EnumMap<>(OperationComptable.class);
        for (OperationComptable operation : OperationComptable.values()) {
            JournalCode choisi = journaux == null ? null : journaux.get(operation);
            complets.put(operation, choisi == null ? operation.journalParDefaut() : choisi);
        }
        journaux = Map.copyOf(complets);
    }

    private static String exiger(String compte, String nom) {
        if (compte == null || compte.isBlank()) {
            throw new IllegalArgumentException("Le compte " + nom + " est obligatoire");
        }
        return compte.trim();
    }

    /**
     * Mapping par défaut — utilisé tant qu'un centre n'a pas personnalisé son plan comptable.
     */
    public static MappingComptable defaultFor(UUID centerId) {
        return new MappingComptable(centerId, "706", "411100", "411500", "512", "530", "44571",
                ComptesStock.parDefaut(), Map.of());
    }

    /**
     * Journal dans lequel s'écrit une opération.
     */
    public JournalCode journalDe(OperationComptable operation) {
        return journaux.get(operation);
    }

    /**
     * Tous les comptes que ce paramétrage utilise (un tel compte ne peut être ni désactivé ni supprimé).
     */
    public Set<String> comptes() {
        Set<String> comptes = new LinkedHashSet<>(List.of(compteVentes, compteClientPatient, compteClientDefaut,
                compteBanque, compteCaisse, stock.stock(), stock.consommation(), stock.facturesNonParvenues(),
                stock.boniInventaire(), stock.maliInventaire()));
        if (compteTVACollectee != null) comptes.add(compteTVACollectee);
        return comptes;
    }
}
