package com.hemodialyse.backend.contexts.seance.domain;

import java.util.Optional;
import java.util.UUID;

/**
 * Contenu d'un QR scanné pour identifier un patient (objet-valeur). Formes acceptées, reproduites du legacy : identifiant du patient
 * (UUID), code patient (casse ignorée), numéro d'assurance, ou comparaison tolérante (casse, espaces et ponctuation ignorés) ; un
 * préfixe avant le premier deux-points est ignoré pour le code et le numéro. KB-SEA-06 corrigé : un QR sans aucun caractère
 * alphanumérique (« - ») est refusé, il ne désigne plus un patient arbitraire.
 */
public final class CodeScanne {

    private final String brut;
    private final String jeton;
    private final String comparable;

    private CodeScanne(String brut, String jeton, String comparable) {
        this.brut = brut;
        this.jeton = jeton;
        this.comparable = comparable;
    }

    public static CodeScanne lire(String qr) {
        if (qr == null || qr.isBlank()) throw new SaisieInvalide("QR invalide");
        String brut = qr.trim();
        String jeton = brut.toUpperCase();
        int separateur = brut.indexOf(':');
        if (separateur > 0 && separateur < brut.length() - 1) {
            jeton = brut.substring(separateur + 1).trim().toUpperCase();
        }
        String comparable = pourComparer(brut);
        if (comparable.isEmpty()) throw new SaisieInvalide("QR invalide : aucun caractère alphanumérique");
        return new CodeScanne(brut, jeton, comparable);
    }

    /** L'identifiant du patient si le QR en est un (lu tel quel, sinon après retrait du préfixe). */
    public Optional<UUID> identifiant() {
        Optional<UUID> direct = versUuid(brut);
        return direct.isPresent() ? direct : versUuid(jeton);
    }

    /** Le code patient cherché (majuscules, préfixe retiré). */
    public String codePatient() {
        return jeton;
    }

    /** Le numéro d'assurance cherché (majuscules, préfixe retiré). */
    public String numeroDAssurance() {
        return jeton;
    }

    /** Vrai si le QR désigne, à la tolérance près, l'une des trois références du patient. Une référence absente ne correspond jamais. */
    public boolean correspond(String codePatient, String numeroDAssurance, String numeroDAssuranceDeLAssure) {
        return comparable.equals(pourComparer(codePatient))
                || comparable.equals(pourComparer(numeroDAssurance))
                || comparable.equals(pourComparer(numeroDAssuranceDeLAssure));
    }

    private static String pourComparer(String valeur) {
        return valeur == null ? "" : valeur.trim().toUpperCase().replaceAll("[^A-Z0-9]", "");
    }

    private static Optional<UUID> versUuid(String valeur) {
        try {
            return Optional.of(UUID.fromString(valeur));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
