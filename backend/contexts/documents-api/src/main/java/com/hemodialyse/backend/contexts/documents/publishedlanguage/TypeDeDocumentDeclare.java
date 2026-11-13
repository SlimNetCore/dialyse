package com.hemodialyse.backend.contexts.documents.publishedlanguage;

import java.util.List;

/**
 * Un type de document déclaré à Documents par le contexte qui possède sa mise en page (documents.v1, ADR 0140 D2).
 *
 * @param code                code stable, en majuscules (ex. {@code CAHIER_DIALYSE}) ; sert aussi de code du modèle dans chaque centre
 * @param libelle             libellé affiché dans « Modèles de documents »
 * @param gabaritParDefaut    chemin du gabarit livré (ex. {@code reports/cahier_dialyse.jrxml})
 * @param parametresAttendus  noms des paramètres que le contexte fournit au rendu (le contrat que tout gabarit personnalisé doit respecter)
 * @param versionDuContrat    version du contrat de paramètres, incrémentée à tout changement incompatible (au moins 1)
 * @param description         description du modèle provisionné pour chaque centre (le libellé si elle est absente)
 */
public record TypeDeDocumentDeclare(String code, String libelle, String gabaritParDefaut, List<String> parametresAttendus, int versionDuContrat,
                                    String description) {

    public TypeDeDocumentDeclare {
        if (code == null || code.isBlank() || !code.equals(code.trim().toUpperCase(java.util.Locale.ROOT))) {
            throw new IllegalArgumentException("Le code d'un type de document est non vide et en majuscules");
        }
        if (libelle == null || libelle.isBlank()) throw new IllegalArgumentException("Le libellé d'un type de document est obligatoire");
        if (gabaritParDefaut == null || gabaritParDefaut.isBlank()) throw new IllegalArgumentException("Le gabarit par défaut est obligatoire");
        if (versionDuContrat < 1) throw new IllegalArgumentException("La version du contrat est au moins 1");
        parametresAttendus = parametresAttendus == null ? List.of() : List.copyOf(parametresAttendus);
        description = description == null || description.isBlank() ? libelle : description;
    }

    public TypeDeDocumentDeclare(String code, String libelle, String gabaritParDefaut, List<String> parametresAttendus, int versionDuContrat) {
        this(code, libelle, gabaritParDefaut, parametresAttendus, versionDuContrat, null);
    }
}
