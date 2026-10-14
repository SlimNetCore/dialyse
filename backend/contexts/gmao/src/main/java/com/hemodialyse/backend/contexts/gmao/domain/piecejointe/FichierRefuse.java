package com.hemodialyse.backend.contexts.gmao.domain.piecejointe;

/**
 * R22 : le fichier proposé n'est pas acceptable. Étend {@link IllegalArgumentException} : HTTP 400 BAD_REQUEST observé. Les
 * trois premiers messages sont ceux du legacy ; « altéré » et « extension incohérente » sont nés de la correction de
 * KB-GMAO-59 (décision du 2026-10-12).
 */
public class FichierRefuse extends IllegalArgumentException {

    private FichierRefuse(String message) {
        super(message);
    }

    static FichierRefuse vide() {
        return new FichierRefuse("Fichier vide");
    }

    static FichierRefuse tropVolumineux() {
        return new FichierRefuse("Fichier trop volumineux (5 Mo maximum)");
    }

    static FichierRefuse formatNonAccepte() {
        return new FichierRefuse("Format non accepté (PDF, PNG ou JPEG uniquement)");
    }

    static FichierRefuse altere(FormatReconnu format) {
        return new FichierRefuse("Fichier " + format + " incomplet ou altéré");
    }

    static FichierRefuse extensionIncoherente(String extension, FormatReconnu format) {
        return new FichierRefuse("L'extension « ." + extension + " » ne correspond pas au contenu du fichier (" + format + ")");
    }
}
