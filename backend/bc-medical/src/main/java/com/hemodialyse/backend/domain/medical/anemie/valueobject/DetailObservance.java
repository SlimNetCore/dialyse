package com.hemodialyse.backend.domain.medical.anemie.valueobject;

/**
 * Ce qui explique une alerte d'observance : la prescription en vigueur (dose par administration et fréquence) et l'unité
 * dans laquelle {@code dosesAttendues} et {@code dosesAdministrees} de l'alerte sont exprimées. Sans unité de dose, ces
 * deux nombres comptent des administrations (alertes antérieures à cette information, prescription sans dose).
 *
 * @param uniteDose       unité de la dose prescrite (« UI », « mg »), ou {@code null} : les nombres comptent des administrations
 * @param dosePrescrite   dose à administrer à chaque fois (dans {@code uniteDose}), ou {@code null}
 * @param frequenceValeur nombre d'administrations prescrites par période, ou {@code null}
 * @param frequenceUnite  unité de la fréquence (HEURE, JOUR, SEMAINE, MOIS, ANNEE), ou {@code null}
 */
public record DetailObservance(String uniteDose, Integer dosePrescrite, Integer frequenceValeur,
                               String frequenceUnite) {

    public static final DetailObservance AUCUN = new DetailObservance(null, null, null, null);

    /**
     * Les nombres de l'alerte sont des quantités de dose (et non un nombre d'administrations).
     */
    public boolean enDose() {
        return uniteDose != null && !uniteDose.isBlank();
    }
}
