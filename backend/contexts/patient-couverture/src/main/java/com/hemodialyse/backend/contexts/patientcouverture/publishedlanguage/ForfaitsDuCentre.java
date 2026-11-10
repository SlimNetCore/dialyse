package com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Langage publié {@code patientcouverture.v1} : le catalogue de forfaits d'un centre (RF2), pour les contextes qui nomment ou
 * tarifent un forfait (séance, facturation, absences, listes de choix). Lecture seule, toujours bornée au centre indiqué ; aucune
 * règle d'accès (le consommateur a déjà garanti le centre). Remplace les lectures SQL dupliquées de la Séance et de la Facturation.
 */
public interface ForfaitsDuCentre {

    /** Les forfaits du centre, par code. */
    List<ForfaitPublie> lister(UUID centre);

    /** Le forfait de ce centre ; vide s'il est inconnu ou d'un autre centre. */
    Optional<ForfaitPublie> trouver(UUID centre, UUID id);

    /** Le forfait de ce code dans ce centre (casse et espaces répétés ignorés). */
    Optional<ForfaitPublie> parCode(UUID centre, String code);

    /** Le prix du forfait ; vide si le forfait est inconnu de ce centre OU s'il n'a pas de prix. */
    Optional<BigDecimal> prix(UUID centre, UUID id);

    /** Ce forfait existe-t-il dans ce centre ? */
    boolean existe(UUID centre, UUID id);

    /** Un forfait du catalogue ; {@code prix} peut être absent, nul (zéro), jamais négatif à la saisie (des valeurs historiques le peuvent). */
    record ForfaitPublie(UUID id, String code, String libelle, BigDecimal prix) {
    }
}
