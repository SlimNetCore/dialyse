package com.hemodialyse.backend.contexts.patientcouverture.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Un maillon de la chaîne des payeurs d'un centre : caisse d'assurance, agence ou centre payeur. Chacun porte un code (clé naturelle
 * dans le centre et le niveau) et un nom.
 */
public sealed interface Payeur permits CaisseDAssurance, Agence, CentrePayeur {

    UUID id();

    CentreId centre();

    String code();

    String nom();

    NiveauDePayeur niveau();

    /** Clé naturelle dans le centre : le code sans casse ni espaces répétés. */
    default String cle() {
        return cleDe(code());
    }

    /** La clé naturelle d'un code saisi : espaces extrêmes retirés, espaces répétés réduits, casse ignorée ; vide si absent. */
    static String cleDe(String code) {
        return code == null ? "" : code.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    /** Refuse ce payeur si un AUTRE payeur de la liste (la première occurrence de la clé) porte déjà la même clé. */
    default void verifierLUnicite(List<? extends Payeur> duCentre) {
        String cle = cle();
        duCentre.stream().filter(autre -> autre.cle().equals(cle)).findFirst().ifPresent(premier -> {
            if (!premier.id().equals(id())) throw new PayeurDejaExistant(niveau(), code());
        });
    }

    /**
     * Le parent désigné par une saisie : son identifiant (s'il figure dans la liste du centre) ou, à défaut, son code (clé naturelle).
     * Le parent d'un autre centre n'est jamais trouvé : la liste donnée est celle du centre.
     */
    static Optional<UUID> resoudre(String saisie, List<? extends Payeur> parentsDuCentre) {
        try {
            UUID id = UUID.fromString(saisie.trim());
            if (parentsDuCentre.stream().anyMatch(p -> p.id().equals(id))) return Optional.of(id);
        } catch (IllegalArgumentException pasUnIdentifiant) {
            // la valeur est un code
        }
        String cle = cleDe(saisie);
        return parentsDuCentre.stream().filter(p -> p.cle().equals(cle)).findFirst().map(Payeur::id);
    }
}
