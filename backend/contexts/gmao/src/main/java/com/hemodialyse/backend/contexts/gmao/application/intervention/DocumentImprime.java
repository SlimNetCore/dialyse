package com.hemodialyse.backend.contexts.gmao.application.intervention;

/** Un document produit par le moteur d'impression : son format (celui du modèle de document du centre) et son contenu. */
public record DocumentImprime(String format, byte[] contenu) {
}
