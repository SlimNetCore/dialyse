package com.hemodialyse.backend.contexts.comptabilite.domain.tva;

import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;

/**
 * Règle (ou « type ») de TVA : le taux de TVA d'un type de prestation sur une période (RG-FAC-001 à 003, RG-CPT-006). La
 * Comptabilité en est seule propriétaire (ADR 0030) ; elle est saisie par deux écrans ({@code /comptabilite/regles-tva} et
 * {@code /tva-types}) qui appliquent <b>la même règle</b>. Les dates de validité sont un intervalle fermé [début, fin] ; une
 * fin absente laisse la règle ouverte. Le type de prestation est une chaîne brute : {@code HEMODIALYSE} et
 * {@code hemodialyse} sont deux prestations (KB-CPT-18, reproduit).
 * <p>
 * Règle unique de saisie (création et modification) : taux de 0 à 100 %, début obligatoire, fin égale ou postérieure au
 * début, pas de chevauchement avec une autre règle <b>active</b> du même centre et du même type. Une règle désactivée est
 * ignorée par le contrôle comme par la recherche de la règle applicable. Les règles déjà en base ne sont pas revalidées.
 *
 * @param libelle libellé saisi ; {@code null} pour une règle saisie par la Comptabilité (libellé composé, voir {@link #libelle()})
 */
public record RegleTva(RegleTvaId id, CentreId centre, String libelle, String typePrestation, TauxDeTva taux,
                       boolean exonere,
                       LocalDate debutValidite, LocalDate finValidite, String texteReference, boolean active) {

    public RegleTva {
        if (id == null) {
            throw new IllegalArgumentException("Identifiant de règle de TVA requis");
        }
        if (libelle != null && libelle.isBlank()) {
            throw new IllegalArgumentException("Le libellé est obligatoire");
        }
        if (typePrestation == null || typePrestation.isBlank()) {
            throw new IllegalArgumentException("Le type de prestation est obligatoire");
        }
        if (debutValidite == null) {
            throw new IllegalArgumentException("La date de début de validité est obligatoire");
        }
    }

    /**
     * Une règle nouvelle saisie par la Comptabilité (libellé composé), active.
     */
    public static RegleTva definir(RegleTvaId id, CentreId centre, String typePrestation, BigDecimal taux, boolean exonere,
                                   LocalDate debutValidite, LocalDate finValidite, String texteReference) {
        return definir(id, centre, null, typePrestation, taux, exonere, debutValidite, finValidite, texteReference);
    }

    /**
     * Une règle nouvelle, active ; refusée si son taux sort de 0 à 100 % ou si sa fin précède son début (une fin égale au
     * début est acceptée). Le chevauchement se contrôle avec les règles du centre ({@link #exigerSansChevauchement}).
     */
    public static RegleTva definir(RegleTvaId id, CentreId centre, String libelle, String typePrestation, BigDecimal taux,
                                   boolean exonere, LocalDate debutValidite, LocalDate finValidite, String texteReference) {
        return new RegleTva(id, centre, libelle, typePrestation, TauxDeTva.saisi(taux), exonere, debutValidite, finValidite,
                texteReference, true).exigerDesDatesDansLOrdre();
    }

    /**
     * Une règle telle que la base la décrit : aucun contrôle de taux, de dates ni de chevauchement (elle existe déjà).
     */
    public static RegleTva reconstituee(RegleTvaId id, CentreId centre, String libelle, String typePrestation, BigDecimal taux,
                                        boolean exonere, LocalDate debutValidite, LocalDate finValidite, String texteReference,
                                        boolean active) {
        return new RegleTva(id, centre, libelle, typePrestation, new TauxDeTva(taux), exonere, debutValidite, finValidite,
                texteReference, active);
    }

    /**
     * La même règle (même identité, même centre) avec de nouvelles valeurs, soumises à la règle unique de saisie. Elle
     * <b>garde son état</b> : une règle désactivée le reste (KB-FAC-03 corrigé).
     */
    public RegleTva modifier(String nouveauLibelle, String nouveauType, BigDecimal nouveauTaux, boolean nouvelleExoneration,
                             LocalDate nouveauDebut, LocalDate nouvelleFin, String nouveauTexte) {
        return new RegleTva(id, centre, nouveauLibelle, nouveauType, TauxDeTva.saisi(nouveauTaux), nouvelleExoneration,
                nouveauDebut, nouvelleFin, nouveauTexte, active).exigerDesDatesDansLOrdre();
    }

    /**
     * La règle désactivée : elle ne s'applique plus et ne bloque plus aucune autre règle ; jamais supprimée.
     */
    public RegleTva desactiver() {
        return new RegleTva(id, centre, libelle, typePrestation, taux, exonere, debutValidite, finValidite, texteReference, false);
    }

    private RegleTva exigerDesDatesDansLOrdre() {
        if (finValidite != null && finValidite.isBefore(debutValidite)) {
            throw new DatesDeValiditeInversees(debutValidite, finValidite);
        }
        return this;
    }

    /**
     * Libellé de la règle : celui saisi, sinon « type taux% » (règles saisies par la Comptabilité, comme le legacy).
     */
    @Override
    public String libelle() {
        return libelle != null ? libelle : typePrestation + " " + taux + "%";
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
     * Refuse la règle, si elle est active, quand elle chevauche une <b>autre</b> règle active du même type parmi celles du
     * centre ; la première trouvée est citée. Une règle désactivée ne bloque rien et n'est bloquée par rien.
     */
    public void exigerSansChevauchement(Collection<RegleTva> reglesDuCentre) {
        if (!active) {
            return;
        }
        for (RegleTva existante : reglesDuCentre) {
            if (existante.active && !existante.id.equals(id) && existante.centre.equals(centre)
                    && existante.estDuType(typePrestation) && chevauche(existante)) {
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
