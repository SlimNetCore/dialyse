package com.hemodialyse.backend.domain.medical.greffe.service;

import com.hemodialyse.backend.domain.medical.greffe.valueobject.CategorieEtapeGreffe;

import java.util.List;

/**
 * Liste standard des étapes d'un bilan pré-greffe rénale (protocole usuel), utilisée pour
 * générer automatiquement la checklist d'un patient. Le médecin garde la main pour ajouter ou
 * retirer des étapes ensuite — cette liste n'est qu'un point de départ, pas une contrainte.
 */
public final class EtapeBilanPreGreffeTemplate {

    public static final List<EtapeStandard> ETAPES_STANDARD = List.of(
            new EtapeStandard(CategorieEtapeGreffe.CARDIOLOGIQUE, "ECG"),
            new EtapeStandard(CategorieEtapeGreffe.CARDIOLOGIQUE, "Échographie cardiaque"),
            new EtapeStandard(CategorieEtapeGreffe.CARDIOLOGIQUE, "Épreuve d'effort / scintigraphie myocardique"),
            new EtapeStandard(CategorieEtapeGreffe.PNEUMOLOGIQUE, "Radiographie thoracique"),
            new EtapeStandard(CategorieEtapeGreffe.PNEUMOLOGIQUE, "Explorations fonctionnelles respiratoires"),
            new EtapeStandard(CategorieEtapeGreffe.DENTAIRE, "Panoramique dentaire et consultation dentaire"),
            new EtapeStandard(CategorieEtapeGreffe.ORL, "Consultation ORL"),
            new EtapeStandard(CategorieEtapeGreffe.GYNECOLOGIQUE, "Frottis cervico-vaginal / mammographie"),
            new EtapeStandard(CategorieEtapeGreffe.UROLOGIQUE, "PSA / bilan urologique"),
            new EtapeStandard(CategorieEtapeGreffe.DIGESTIF, "Échographie abdominale"),
            new EtapeStandard(CategorieEtapeGreffe.ONCOLOGIQUE, "Coloscopie (si indiquée selon l'âge)"),
            new EtapeStandard(CategorieEtapeGreffe.PSYCHIATRIQUE, "Évaluation psychiatrique / psychologique"),
            new EtapeStandard(CategorieEtapeGreffe.VIROLOGIQUE, "Sérologie VIH"),
            new EtapeStandard(CategorieEtapeGreffe.VIROLOGIQUE, "Sérologies hépatite B et C"),
            new EtapeStandard(CategorieEtapeGreffe.VIROLOGIQUE, "Sérologie CMV"),
            new EtapeStandard(CategorieEtapeGreffe.VIROLOGIQUE, "Sérologie EBV"),
            new EtapeStandard(CategorieEtapeGreffe.VIROLOGIQUE, "Sérologie toxoplasmose"),
            new EtapeStandard(CategorieEtapeGreffe.IMMUNOLOGIQUE, "Groupe sanguin et phénotype érythrocytaire"),
            new EtapeStandard(CategorieEtapeGreffe.IMMUNOLOGIQUE, "Typage HLA"),
            new EtapeStandard(CategorieEtapeGreffe.IMMUNOLOGIQUE, "Recherche d'anticorps anti-HLA (PRA)"),
            new EtapeStandard(CategorieEtapeGreffe.VACCINATION, "Statut vaccinal hépatite B"),
            new EtapeStandard(CategorieEtapeGreffe.VACCINATION, "Statut vaccinal pneumocoque / grippe"),
            new EtapeStandard(CategorieEtapeGreffe.NUTRITIONNEL, "Évaluation nutritionnelle")
    );

    private EtapeBilanPreGreffeTemplate() {
    }

    public record EtapeStandard(CategorieEtapeGreffe categorie, String libelle) {
    }
}
