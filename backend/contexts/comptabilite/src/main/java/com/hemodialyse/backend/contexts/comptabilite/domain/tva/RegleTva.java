package com.hemodialyse.backend.contexts.comptabilite.domain.tva;

import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;

/**
 * Règle de TVA comptable : le taux de TVA d'un type de prestation à partir d'une date (RG-CPT-006). Les dates de validité sont
 * un intervalle fermé [début, fin] ; une fin absente laisse la règle ouverte. Le type de prestation est une chaîne brute :
 * {@code HEMODIALYSE} et {@code hemodialyse} sont deux prestations (KB-CPT-18, reproduit).
 * <p>
 * Un centre n'a qu'une règle applicable par type et par date : une règle nouvelle dont la fin précède le début, ou qui chevauche
 * une règle <b>active</b> du même type, est refusée (KB-CPT-16). Une règle désactivée par la Facturation est ignorée par le
 * contrôle, comme par la recherche de la règle applicable. Les règles déjà en base ne sont pas revalidées.
 */
public record RegleTva(CentreId centre, String typePrestation, TauxDeTva taux, boolean exonere, LocalDate debutValidite,
                       LocalDate finValidite, String texteReference, boolean active) {

    private RegleTva(CentreId centre, String typePrestation, BigDecimal taux, boolean exonere, LocalDate debutValidite,
                     LocalDate finValidite, String texteReference, boolean active) {
        if (typePrestation == null || typePrestation.isBlank()) {
            throw new IllegalArgumentException("Le type de prestation est obligatoire");
        }
        this(centre, typePrestation, new TauxDeTva(taux), exonere, debutValidite, finValidite, texteReference, active);
        if (debutValidite == null) {
            throw new IllegalArgumentException("La date de début de validité est obligatoire");
        }
        this.centre = centre;
        this.typePrestation = typePrestation;
        this.exonere = exonere;
        this.debutValidite = debutValidite;
        this.finValidite = finValidite;
        this.texteReference = texteReference;
        this.active = active;
    }

    /**
     * Une règle nouvelle, active ; refusée si sa fin précède son début (une fin égale au début est acceptée).
     */
    public static RegleTva definir(CentreId centre, String typePrestation, BigDecimal taux, boolean exonere,
                                   LocalDate debutValidite, LocalDate finValidite, String texteReference) {
        RegleTva regle = new RegleTva(centre, typePrestation, taux, exonere, debutValidite, finValidite, texteReference, true);
        if (finValidite != null && finValidite.isBefore(debutValidite)) {
            throw new DatesDeValiditeInversees(debutValidite, finValidite);
        }
        return regle;
    }

    /**
     * Une règle telle que la base la décrit : aucun contrôle de dates ni de chevauchement (elle existe déjà).
     */
    public static RegleTva reconstituee(CentreId centre, String typePrestation, BigDecimal taux, boolean exonere,
                                        LocalDate debutValidite, LocalDate finValidite, String texteReference, boolean active) {
        return new RegleTva(centre, typePrestation, taux, exonere, debutValidite, finValidite, texteReference, active);
    }

    /**
     * Vrai si la règle est active et que la date est dans son intervalle fermé (début et fin inclus).
     */
    public boolean estApplicableLe(LocalDate date) {
        return active && !date.isBefore(debutValidite) && (finValidite == null || !date.isAfter(finValidite));
    }

    public boolean estDuType(String type) {
        return typePrestation.equals(type);
    }

    /**
     * Refuse la règle si elle chevauche une règle active du même type parmi celles du centre ; la première trouvée est citée.
     */
    public void exigerSansChevauchement(Collection<RegleTva> reglesDuCentre) {
        for (RegleTva existante : reglesDuCentre) {
            if (existante.active && existante.centre.equals(centre) && existante.estDuType(typePrestation) && chevauche(existante)) {
                throw new RegleTvaEnChevauchement(this, existante);
            }
        }
    }

    private boolean chevauche(RegleTva autre) {
        boolean cetteReglePrecedeLAutre = finValidite != null && finValidite.isBefore(autre.debutValidite);
        boolean lAutrePrecedeCetteRegle = autre.finValidite != null && autre.finValidite.isBefore(debutValidite);
        return !cetteReglePrecedeLAutre && !lAutrePrecedeCetteRegle;
    }

    /**
     * Fin de validité ; {@code null} si la règle est ouverte.
     */
    @Override
    public LocalDate finValidite() {
        return finValidite;
    }

    /**
     * Texte de référence (loi, décret) ; {@code null} s'il n'y en a pas.
     */
    @Override
    public String texteReference() {
        return texteReference;
    }
}
