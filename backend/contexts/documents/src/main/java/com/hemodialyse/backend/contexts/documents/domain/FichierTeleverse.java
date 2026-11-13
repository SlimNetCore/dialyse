package com.hemodialyse.backend.contexts.documents.domain;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Le fichier de gabarit qu'un administrateur dépose : son nom se termine par {@code .jrxml} (sans tenir compte de la casse), il n'est
 * pas vide, ne dépasse pas la taille maximale et se lit en UTF-8 strict. Ces contrôles de FORME précèdent ceux de SÉCURITÉ (liste blanche,
 * compilation à l'essai), qui restent ceux de {@code JrxmlSecurityValidator} : aucun n'est assoupli ici.
 */
public final class FichierTeleverse {

    private final byte[] octets;

    private FichierTeleverse(byte[] octets) {
        this.octets = octets;
    }

    /**
     * @throws GabaritRefuse {@code FILE_TYPE} (nom sans l'extension attendue), {@code EMPTY}, {@code TOO_LARGE}
     */
    public static FichierTeleverse accepter(String nom, byte[] octets, int tailleMaximale) {
        if (nom == null || !nom.toLowerCase().endsWith(".jrxml")) {
            throw new GabaritRefuse("FILE_TYPE", "Extension .jrxml attendue.");
        }
        if (octets == null || octets.length == 0) {
            throw new GabaritRefuse("EMPTY", "Le fichier est vide.");
        }
        if (octets.length > tailleMaximale) {
            throw new GabaritRefuse("TOO_LARGE", "Taille maximale : " + tailleMaximale / 1024 + " Ko.");
        }
        return new FichierTeleverse(octets.clone());
    }

    /** Le contenu en texte ; @throws GabaritRefuse {@code ENCODING} si le fichier n'est pas de l'UTF-8 valide. */
    public String contenu() {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(octets)).toString();
        } catch (CharacterCodingException e) {
            throw new GabaritRefuse("ENCODING", "Le fichier doit être encodé en UTF-8.");
        }
    }

    public int tailleEnOctets() {
        return octets.length;
    }

    /** Empreinte SHA-256 des octets déposés (hexadécimal minuscule). */
    public String empreinte() {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(octets));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
