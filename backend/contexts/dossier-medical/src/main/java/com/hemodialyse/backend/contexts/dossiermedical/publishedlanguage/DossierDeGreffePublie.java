package com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Langage publié du Dossier médical (DM9, ADR 0101) : le dossier de synthèse « préparation à la greffe rénale » d'un patient d'un centre
 * (receveur et donneurs vivants candidats), pour l'export PDF, rendu depuis PL2 par le contexte ({@code ExporterLeDossierDeGreffe}). La garde du
 * patient est dans le cas d'usage : un patient inconnu ou d'un autre centre est refusé ({@code PatientIntrouvable}, 404
 * {@code PATIENT_INTROUVABLE} par l'advice du contexte sur les contrôleurs {@link SousLaGardeDuDossierMedical}). Les énumérations sont
 * rendues par leur nom. Ordres : décisions par date de réunion décroissante ; étapes par catégorie puis libellé ; sérologies par date de
 * prélèvement décroissante ; antécédents (ACTIFS seuls) par début décroissant ; donneurs du plus récemment créé au plus ancien. Une ligne
 * illisible en base (catégorie, statut, marqueur, lien de parenté absent de l'énumération) est IGNORÉE avec un WARN portant son
 * identifiant seul (règle DM7 du contexte) ; un bilan au statut illisible est refusé (422 {@code BILAN_PRE_GREFFE_ILLISIBLE}).
 */
public interface DossierDeGreffePublie {

    DossierDeGreffe duPatient(UUID centre, UUID patient);

    /** @param bilan nul si le patient n'a pas de bilan pré-greffe */
    record DossierDeGreffe(Bilan bilan, List<Etape> etapes, List<Serologie> serologies, List<AntecedentActif> antecedentsActifs,
                           List<Donneur> donneurs) {
    }

    record Bilan(String statut, String groupeSanguinConfirme, String typageHla, BigDecimal praClasseI, BigDecimal praClasseII,
                 String contreIndications, String conclusionNephrologue, List<DecisionDeRcp> decisions) {
    }

    record DecisionDeRcp(LocalDate dateReunion, String avis, String compteRendu, LocalDate prochaineDateRevue) {
    }

    record Etape(String categorie, String libelle, String statut, LocalDate dateRealisation, String resultat) {
    }

    record Serologie(String marqueur, String resultat, LocalDate datePrelevement) {
    }

    /** @param libelle libellé d'affichage du diagnostic codé, sinon libellé libre (nul possible) */
    record AntecedentActif(String libelle) {
    }

    record Donneur(String nom, String prenom, String lienDeParente, String groupeSanguin, String statutBilan, String crossmatch) {
    }
}
