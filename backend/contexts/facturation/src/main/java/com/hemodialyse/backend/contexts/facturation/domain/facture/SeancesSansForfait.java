package com.hemodialyse.backend.contexts.facturation.domain.facture;

/**
 * Des séances éligibles n'ont aucun forfait (ni surcharge, ni prise en charge) : la validation est refusée (422, KB-FAC-32) et
 * n'émet aucune facture. La simulation, elle, les affiche en « Forfait non defini ».
 */
public class SeancesSansForfait extends RuntimeException {

    private final int nombre;

    public SeancesSansForfait(int nombre) {
        super(nombre + (nombre > 1 ? " seances eligibles n'ont pas de forfait defini" : " seance eligible n'a pas de forfait defini")
                + " : donner un forfait (prise en charge ou changement de forfait) a chacune avant de valider la facturation");
        this.nombre = nombre;
    }

    public int nombre() {
        return nombre;
    }
}
