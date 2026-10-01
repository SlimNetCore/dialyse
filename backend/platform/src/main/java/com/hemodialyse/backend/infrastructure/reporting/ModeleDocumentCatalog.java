package com.hemodialyse.backend.infrastructure.reporting;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Catalogue des documents imprimables livrés avec l'application.
 * <p>
 * <b>Règle :</b> tout rapport imprimé par l'application est un modèle de document. Il est déclaré ici (type,
 * libellé, modèle Jasper d'origine), provisionné pour chaque centre ({@link ModeleDocumentProvisioner}) et imprimé
 * via {@link ModeleDocumentPrinter} : le centre peut ainsi le désactiver ou en téléverser une version personnalisée
 * depuis « Modèles de documents ». Un test vérifie que chaque {@code reports/*.jrxml} figure dans ce catalogue.
 */
public final class ModeleDocumentCatalog {

    public static final String INVENTAIRE_STOCK = "INVENTAIRE_STOCK";
    public static final String BON_INTERVENTION = "BON_INTERVENTION";
    private static final List<Entry> ENTRIES = List.of(
            new Entry("FICHE_PATIENT", "Fiche signalétique patient", "reports/fiche_patient.jrxml",
                    "Fiche complète du patient avec ses informations personnelles et médicales"),
            new Entry("ATTESTATION", "Attestation d'ouverture de droit", "reports/attestation.jrxml",
                    "Attestation d'ouverture de droit du patient"),
            new Entry("PEC", "Prise en charge", "reports/prise_en_charge.jrxml",
                    "Document de prise en charge pour la caisse d'assurance"),
            new Entry("LISTE_PATIENTS", "Liste des patients", "reports/liste_patients.jrxml",
                    "Liste complète des patients du centre"),
            new Entry("LISTE_PEC", "Liste des prises en charge", "reports/liste_pec.jrxml",
                    "Liste de toutes les prises en charge du centre"),
            new Entry("LISTE_ATTESTATIONS", "Liste des attestations", "reports/liste_attestations.jrxml",
                    "Liste de toutes les attestations du centre"),
            new Entry("SYNTHESE_FACTURATION_MENSUELLE", "Synthèse mensuelle facturation",
                    "reports/synthese_mensuelle_facturation.jrxml",
                    "Tableau croisé facturation par forfait/caisse avec répartition graphique"),
            new Entry("ORDONNANCE", "Ordonnance médicamenteuse", "reports/ordonnance.jrxml",
                    "Ordonnance signée par le médecin, imprimable pour le patient"),
            new Entry(INVENTAIRE_STOCK, "Procès-verbal d'inventaire de stock", "reports/inventaire_stock.jrxml",
                    "Inventaire physique : détail par article et par lot, écarts, validation"),
            new Entry(BON_INTERVENTION, "Bon d'intervention (GMAO)", "reports/bon_intervention.jrxml",
                    "Bon d'intervention de maintenance : équipement, période, états, travaux, coûts, signatures"));

    private ModeleDocumentCatalog() {
    }

    public static List<Entry> entries() {
        return ENTRIES;
    }

    public static Optional<Entry> find(String type) {
        if (type == null) return Optional.empty();
        String normalized = type.trim().toUpperCase(Locale.ROOT);
        return ENTRIES.stream().filter(e -> e.type().equals(normalized)).findFirst();
    }

    /**
     * Document livré : {@code type} sert aussi de code du modèle dans chaque centre.
     */
    public record Entry(String type, String libelle, String cheminJrxml, String description) {
    }
}

