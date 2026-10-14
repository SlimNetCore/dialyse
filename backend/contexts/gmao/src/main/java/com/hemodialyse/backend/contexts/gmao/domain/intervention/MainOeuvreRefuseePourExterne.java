package com.hemodialyse.backend.contexts.gmao.domain.intervention;

/**
 * R20 : un intervenant externe est facturé en honoraires (ligne automatique à la clôture) ; on ne saisit pas de main
 * d'œuvre pour lui. Étend {@link IllegalArgumentException} : 400 BAD_REQUEST observé, message du legacy.
 */
public class MainOeuvreRefuseePourExterne extends IllegalArgumentException {
    public MainOeuvreRefuseePourExterne() {
        super("Intervenant externe : ses honoraires sont facturés automatiquement, pas de main d'œuvre à saisir");
    }
}
