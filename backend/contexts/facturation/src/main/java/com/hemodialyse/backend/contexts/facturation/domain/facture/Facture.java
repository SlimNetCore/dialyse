package com.hemodialyse.backend.contexts.facturation.domain.facture;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.FactureSimulee;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.PatientFacture;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.PeriodeDeFacturation;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.SeanceFacturable;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.TauxDeTva;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Facture émise (RG-FAC-030 à 032) : le document probant qui fige un patient, son payeur, une période, les forfaits facturés avec
 * leur prix, le taux de TVA et les séances qu'il couvre. Elle est immuable après l'émission (F7, F8) : ni recalcul rétroactif, ni
 * modification. Elle porte au moins une ligne ; ses totaux se déduisent des lignes et du taux figé (F5) : la TVA est arrondie
 * (HALF_UP) sur le total hors taxe de la facture, pas ligne par ligne.
 */
public final class Facture {

    private final UUID id;
    private final CentreId centre;
    private final PatientFacture patient;
    private final String numero;
    private final PeriodeDeFacturation periode;
    private final LocalDate dateDeFacturation;
    private final TauxDeTva taux;
    private final List<LigneFacture> lignes;
    private final List<UUID> seances;
    private final UUID emisePar;
    private final BigDecimal totalHt;
    private final BigDecimal totalTva;
    private final BigDecimal totalTtc;

    private Facture(UUID id, CentreId centre, PatientFacture patient, String numero, PeriodeDeFacturation periode,
                    LocalDate dateDeFacturation, TauxDeTva taux, List<LigneFacture> lignes, List<UUID> seances, UUID emisePar) {
        if (id == null || centre == null || patient == null) {
            throw new IllegalArgumentException("Les identifiants facture, centre et patient sont obligatoires");
        }
        if (numero == null || numero.isBlank()) {
            throw new IllegalArgumentException("Le numero de facture est obligatoire");
        }
        if (periode == null || dateDeFacturation == null || taux == null) {
            throw new IllegalArgumentException("La periode, la date de facturation et la TVA sont obligatoires");
        }
        if (lignes == null || lignes.isEmpty()) {
            throw new IllegalArgumentException("Une facture doit contenir au moins une ligne");
        }
        if (seances == null || seances.isEmpty()) {
            throw new IllegalArgumentException("Une facture couvre au moins une seance");
        }
        this.id = id;
        this.centre = centre;
        this.patient = patient;
        this.numero = numero;
        this.periode = periode;
        this.dateDeFacturation = dateDeFacturation;
        this.taux = new TauxDeTva(taux.pourcentage().setScale(2, RoundingMode.HALF_UP));
        this.lignes = List.copyOf(lignes);
        this.seances = List.copyOf(seances);
        this.emisePar = emisePar;
        this.totalHt = this.lignes.stream().map(LigneFacture::montantHt).reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        this.totalTva = this.taux.tvaSur(totalHt);
        this.totalTtc = totalHt.add(totalTva).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Refuse l’émission (KB-FAC-32) tant qu’une séance n’a pas de forfait : aucune facture ne se fige avec un « Forfait non defini » à 0.
     *
     * @throws SeancesSansForfait avec le nombre de séances concernées
     */
    public static void exigerUnForfaitPourChaqueSeance(List<SeanceFacturable> seances) {
        long sansForfait = seances.stream().filter(seance -> !seance.forfait().estDefini()).count();
        if (sansForfait > 0) {
            throw new SeancesSansForfait((int) sansForfait);
        }
    }

    /**
     * Émet la facture d'un groupe de séances de la simulation, sous ce numéro, à cette date et à ce taux de TVA (celui de la fin
     * de la période facturée). Les lignes sont celles de la simulation ; les totaux sont recalculés ici.
     *
     * @param emisePar l'utilisateur qui valide la facturation ; {@code null} si la session ne le donne pas
     */
    public static Facture emettre(UUID id, CentreId centre, FactureSimulee groupe, String numero, PeriodeDeFacturation periode,
                                  LocalDate dateDeFacturation, TauxDeTva taux, UUID emisePar) {
        return new Facture(id, centre, groupe.patient(), numero, periode, dateDeFacturation, taux,
                groupe.lignes().stream().map(LigneFacture::depuis).toList(),
                groupe.seances().stream().map(SeanceFacturable::id).toList(), emisePar);
    }

    public UUID id() {
        return id;
    }

    public CentreId centre() {
        return centre;
    }

    public PatientFacture patient() {
        return patient;
    }

    public String numero() {
        return numero;
    }

    public PeriodeDeFacturation periode() {
        return periode;
    }

    public LocalDate dateDeFacturation() {
        return dateDeFacturation;
    }

    public TauxDeTva taux() {
        return taux;
    }

    public List<LigneFacture> lignes() {
        return lignes;
    }

    /**
     * Les séances que la facture couvre, par identité.
     */
    public List<UUID> seances() {
        return seances;
    }

    public UUID emisePar() {
        return emisePar;
    }

    public BigDecimal totalHt() {
        return totalHt;
    }

    public BigDecimal totalTva() {
        return totalTva;
    }

    public BigDecimal totalTtc() {
        return totalTtc;
    }
}
