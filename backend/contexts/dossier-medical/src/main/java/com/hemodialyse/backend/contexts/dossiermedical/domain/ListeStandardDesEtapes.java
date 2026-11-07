package com.hemodialyse.backend.contexts.dossiermedical.domain;

import java.util.List;

/**
 * Liste standard des étapes d'un bilan pré-greffe rénale (protocole usuel, 23 étapes), REPRODUITE du legacy mot pour mot : point de
 * départ, jamais une contrainte. La casse du libellé compte (« consultation orl » n'est pas « Consultation ORL »).
 */
public final class ListeStandardDesEtapes {

    public record EtapeStandard(CategorieDEtape categorie, String libelle) {
        public String cle() {
            return categorie.name() + "|" + libelle;
        }
    }

    public static final List<EtapeStandard> ETAPES = List.of(
            new EtapeStandard(CategorieDEtape.CARDIOLOGIQUE, "ECG"),
            new EtapeStandard(CategorieDEtape.CARDIOLOGIQUE, "Échographie cardiaque"),
            new EtapeStandard(CategorieDEtape.CARDIOLOGIQUE, "Épreuve d'effort / scintigraphie myocardique"),
            new EtapeStandard(CategorieDEtape.PNEUMOLOGIQUE, "Radiographie thoracique"),
            new EtapeStandard(CategorieDEtape.PNEUMOLOGIQUE, "Explorations fonctionnelles respiratoires"),
            new EtapeStandard(CategorieDEtape.DENTAIRE, "Panoramique dentaire et consultation dentaire"),
            new EtapeStandard(CategorieDEtape.ORL, "Consultation ORL"),
            new EtapeStandard(CategorieDEtape.GYNECOLOGIQUE, "Frottis cervico-vaginal / mammographie"),
            new EtapeStandard(CategorieDEtape.UROLOGIQUE, "PSA / bilan urologique"),
            new EtapeStandard(CategorieDEtape.DIGESTIF, "Échographie abdominale"),
            new EtapeStandard(CategorieDEtape.ONCOLOGIQUE, "Coloscopie (si indiquée selon l'âge)"),
            new EtapeStandard(CategorieDEtape.PSYCHIATRIQUE, "Évaluation psychiatrique / psychologique"),
            new EtapeStandard(CategorieDEtape.VIROLOGIQUE, "Sérologie VIH"),
            new EtapeStandard(CategorieDEtape.VIROLOGIQUE, "Sérologies hépatite B et C"),
            new EtapeStandard(CategorieDEtape.VIROLOGIQUE, "Sérologie CMV"),
            new EtapeStandard(CategorieDEtape.VIROLOGIQUE, "Sérologie EBV"),
            new EtapeStandard(CategorieDEtape.VIROLOGIQUE, "Sérologie toxoplasmose"),
            new EtapeStandard(CategorieDEtape.IMMUNOLOGIQUE, "Groupe sanguin et phénotype érythrocytaire"),
            new EtapeStandard(CategorieDEtape.IMMUNOLOGIQUE, "Typage HLA"),
            new EtapeStandard(CategorieDEtape.IMMUNOLOGIQUE, "Recherche d'anticorps anti-HLA (PRA)"),
            new EtapeStandard(CategorieDEtape.VACCINATION, "Statut vaccinal hépatite B"),
            new EtapeStandard(CategorieDEtape.VACCINATION, "Statut vaccinal pneumocoque / grippe"),
            new EtapeStandard(CategorieDEtape.NUTRITIONNEL, "Évaluation nutritionnelle"));

    private ListeStandardDesEtapes() {
    }
}
