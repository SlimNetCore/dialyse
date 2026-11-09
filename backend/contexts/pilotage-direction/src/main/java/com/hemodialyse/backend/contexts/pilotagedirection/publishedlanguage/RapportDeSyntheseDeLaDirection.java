package com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage;

import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.IndicateursDeLaDirectionFournis.IndicateursDeLaPeriode;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.TableauDeBordDeLaDirection.Repartition;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.TableauDeBordDeLaDirection.VueDEnsemble;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Langage publié du Pilotage direction ({@code pilotagedirection.v1}, ADR 0102, PD4) : le MODÈLE du rapport de synthèse de la direction
 * (sections, bornes, totaux, masquage). Le contexte possède le calcul ; la plateforme garde le rendu HTML puis PDF (openhtmltopdf 1.0.10,
 * en-tête et pied de la société par {@code DocumentIdentityProvider}) et la mise en forme (« &lt; seuil », « — », montants, barres).
 * <p>
 * Une valeur {@code null} est une valeur MASQUÉE ou ABSENTE : un effectif de patients nul s'imprime « &lt; seuil », une autre valeur nulle
 * « — ». Une section {@code null} est absente du document (un instantané antérieur aux répartitions n'a pas de répartition).
 * Le rapport reflète l'anonymat de la source : il n'imprime jamais un effectif que la source masque.
 */
public interface RapportDeSyntheseDeLaDirection {

    /** Le rapport en direct d'une période ; {@code from} / {@code to} nuls : défauts du 1er janvier et d'aujourd'hui. */
    Rapport enDirect(UUID societeId, LocalDate from, LocalDate to);

    /** Le rapport d'un contenu déjà constitué (un instantané mensuel figé, PD2) : rien n'est recalculé. */
    Rapport constituer(SourceDuRapport source);

    /** {@code vue} : au moins {@code totaux} et {@code centres} ; {@code repartition} nulle pour un instantané ancien. */
    record SourceDuRapport(String titre, OffsetDateTime genereLe, VueDEnsemble vue, IndicateursDeLaPeriode indicateurs,
                           Repartition repartition) {
    }

    record Rapport(String titre, String societeNom, String debut, String fin, Integer seuilAnonymat, OffsetDateTime genereLe,
                   Synthese synthese, List<Alerte> alertes, Finances finances, List<Mois> evolution, QualiteDesSoins qualite,
                   Stock stock, Repartitions repartitions) {
    }

    /** Les huit chiffres clés de la société. */
    record Synthese(Long patients, Long seances, Long factures, BigDecimal caHt, BigDecimal caTtc, BigDecimal encaisse,
                    BigDecimal resteARecouvrer, BigDecimal tauxEncaissement) {
    }

    /** {@code libelle} : le libellé métier du code d'alerte (le code lui-même s'il est inconnu). */
    record Alerte(String centre, String libelle, String severite, BigDecimal valeur) {
        public boolean critique() {
            return "CRITICAL".equals(severite);
        }
    }

    record LigneFinanciere(String nom, Long patients, Long patientsSousKt, Long seances, Long factures, BigDecimal caHt,
                           BigDecimal caTtc, BigDecimal encaisse, BigDecimal resteARecouvrer, BigDecimal tauxEncaissement) {
    }

    record Finances(List<LigneFinanciere> centres, LigneFinanciere total) {
    }

    /** Un mois civil, tous centres confondus (sommes en double, comme le legacy : les arrondis du rapport en dépendent). */
    record Mois(String mois, long seances, double caHt, double caTtc) {
    }

    record LigneClinique(String nom, BigDecimal ktV, BigDecimal hemoglobine, BigDecimal phosphore, BigDecimal pth,
                         BigDecimal albumine, Long vhbPositifs, Long vhcPositifs, Long vihPositifs,
                         Long patientsObservanceEnRetard, Long greffeListeAttente, Long greffeBilanEnCours, Long greffesPeriode) {
    }

    record QualiteDesSoins(List<LigneClinique> centres, LigneClinique total) {
    }

    record LigneStock(String nom, Long articlesActifs, Long articlesSousSeuil, Long lotsPerimes, Long lotsPeremptionProche,
                      BigDecimal valeurStock) {
    }

    record Stock(List<LigneStock> centres, LigneStock total) {
    }

    record LigneParSexe(String nom, Long masculin, Long feminin, Long autre) {
    }

    /** {@code tranches} : code de tranche -> effectif (masqué : valeur nulle) ; un code absent de la carte s'imprime « — ». */
    record LigneParAge(String nom, Map<String, Long> tranches) {
    }

    /** Une caisse d'assurance de la société : une valeur par centre du rapport (dans l'ordre de {@code Caisses.centres}). */
    record LigneDeCaisse(String caisse, List<Long> patients, Long totalPatients, List<Long> seances, Long totalSeances,
                         List<BigDecimal> caHt, BigDecimal totalCaHt) {
    }

    record Caisses(List<String> centres, List<LigneDeCaisse> lignes) {
    }

    record LigneAnemie(String nom, Long patientsSousEpo, Long patientsSousFer, Long patientsEpo, Long patientsFer,
                       Long administreesEpo, Long administreesFer, Long nonAdministrees, BigDecimal tauxAdministration) {
    }

    /** {@code caisses} nul quand la société n'a aucune caisse ; {@code anemie} vide quand aucun centre. */
    record Repartitions(List<LigneParSexe> sexe, List<LigneParAge> ages, Caisses caisses, List<LigneAnemie> anemie) {
    }
}
