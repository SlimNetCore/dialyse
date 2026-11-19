package com.hemodialyse.backend.contexts.facturation.application.consultation;

import com.hemodialyse.backend.contexts.facturation.application.cloture.FuseauxDesCentres;
import com.hemodialyse.backend.contexts.facturation.domain.facture.AttributionDesForfaits;
import com.hemodialyse.backend.contexts.facturation.domain.facture.AttributionDesForfaits.SeanceAAttribuer;
import com.hemodialyse.backend.contexts.facturation.domain.facture.LigneFacture;
import com.hemodialyse.backend.contexts.facturation.domain.facture.SeanceAttribuee;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Consultation des factures émises (lecture seule) : liste filtrée, triée, paginée et totalisée EN MÉMOIRE sur la période (RG-FAC-046,
 * comme {@code SituationsDesFacturesJdbc}), et détail d'une facture avec ses séances et le forfait attribué à chacune (RG-FAC-047, 048).
 */
public final class ConsulterLesFactures {

    static final int TAILLE_PAR_DEFAUT = 20;
    static final int TAILLE_MAXIMALE = 100;
    static final long JOURS_MAXIMUM = 366;

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);
    private static final BigDecimal CENT = BigDecimal.valueOf(100);

    /** Départage toujours appliqué : date de facturation décroissante, numéro décroissant, identifiant. */
    private static final Comparator<FactureListee> DEPARTAGE = Comparator.comparing(FactureListee::dateFacturation).reversed()
            .thenComparing(Comparator.comparing(FactureListee::numeroFacture).reversed())
            .thenComparing(f -> f.factureId().toString());

    private final ListeDesFactures liste;
    private final SeancesDeLaFacture seances;
    private final FuseauxDesCentres fuseaux;
    private final Clock horloge;

    public ConsulterLesFactures(ListeDesFactures liste, SeancesDeLaFacture seances, FuseauxDesCentres fuseaux, Clock horloge) {
        this.liste = liste;
        this.seances = seances;
        this.fuseaux = fuseaux;
        this.horloge = horloge;
    }

    /**
     * Page négative : 0 ; taille &lt; 1 : 20 ; taille &gt; 100 : 100 (bornée, pas d'erreur).
     *
     * @throws CriteresDeFacturesInvalides {@code PERIODE_INVALIDE} (une seule borne, du &gt; au, plus de 366 jours) ou
     *                                     {@code MONTANT_INVALIDE} (min &gt; max)
     */
    public PageDeFactures rechercher(CriteresDeFactures criteres, int page, int taille) {
        int pageEffective = Math.max(page, 0);
        int tailleEffective = taille < 1 ? TAILLE_PAR_DEFAUT : Math.min(taille, TAILLE_MAXIMALE);
        LocalDate[] periode = periode(criteres);
        if (criteres.montantMin() != null && criteres.montantMax() != null
                && criteres.montantMin().compareTo(criteres.montantMax()) > 0) {
            throw new CriteresDeFacturesInvalides(CriteresDeFacturesInvalides.MONTANT_INVALIDE,
                    "Le montant minimum depasse le montant maximum");
        }
        List<FactureListee> filtrees = new ArrayList<>(liste.entre(criteres.centre(), periode[0], periode[1]).stream()
                .filter(f -> retenue(f, criteres)).toList());
        filtrees.sort(ordre(criteres.tri()));
        long debut = Math.min((long) pageEffective * tailleEffective, filtrees.size());
        long fin = Math.min(debut + tailleEffective, filtrees.size());
        return new PageDeFactures(filtrees.subList((int) debut, (int) fin), filtrees.size(), pageEffective, tailleEffective,
                periode[0], periode[1], totaux(filtrees));
    }

    /** Le détail de la facture du centre ; vide si inconnue ou d'un autre centre (aucune fuite d'existence). */
    public Optional<DetailDeFacture> detail(CentreId centre, UUID facture) {
        return liste.deLaFacture(centre, facture).map(f -> detail(centre, f));
    }

    private DetailDeFacture detail(CentreId centre, FactureListee facture) {
        List<LigneFacture> lignes = liste.lignes(centre, facture.factureId());
        BigDecimal taux = facture.tauxTva() == null ? ZERO : facture.tauxTva();
        List<LigneDeFactureDetaillee> detaillees = lignes.stream().map(l -> {
            BigDecimal ht = l.montantHt();
            BigDecimal tva = ht.multiply(taux).divide(CENT, 2, RoundingMode.HALF_UP);
            return new LigneDeFactureDetaillee(l.forfait(), l.libelle(), l.prixUnitaireHt(), l.nombreDeSeances(), ht, tva,
                    ht.add(tva).setScale(2, RoundingMode.HALF_UP));
        }).toList();
        List<SeanceAAttribuer> aAttribuer = seances.de(centre, facture.factureId()).stream()
                .map(s -> new SeanceAAttribuer(s.seance(), s.date(), s.statut(), s.forfaitSurcharge())).toList();
        List<SeanceDeFactureDetaillee> attribuees = AttributionDesForfaits.attribuer(lignes, aAttribuer).stream()
                .map(ConsulterLesFactures::seance).toList();
        return new DetailDeFacture(facture.factureId(), facture.numeroFacture(), facture.tauxTva(), detaillees, attribuees,
                new DetailDeFacture.Totaux(facture.montantHt(), facture.montantTva(), facture.montantTtc(), facture.montantRegle(),
                        facture.reste()));
    }

    private static SeanceDeFactureDetaillee seance(SeanceAttribuee s) {
        LigneFacture l = s.ligne();
        return new SeanceDeFactureDetaillee(s.seance(), s.date(), s.statut(), l == null ? null : l.forfait(),
                l == null ? null : l.libelle(), l == null ? null : l.prixUnitaireHt());
    }

    private LocalDate[] periode(CriteresDeFactures criteres) {
        LocalDate du = criteres.du();
        LocalDate au = criteres.au();
        if (du == null && au == null) {
            LocalDate aujourdHui = LocalDate.now(horloge.withZone(fuseaux.de(criteres.centre())));
            return new LocalDate[]{aujourdHui.withDayOfMonth(1), aujourdHui.with(TemporalAdjusters.lastDayOfMonth())};
        }
        if (du == null || au == null) {
            throw new CriteresDeFacturesInvalides(CriteresDeFacturesInvalides.PERIODE_INVALIDE,
                    "Les deux bornes de la periode sont requises ensemble");
        }
        if (du.isAfter(au)) {
            throw new CriteresDeFacturesInvalides(CriteresDeFacturesInvalides.PERIODE_INVALIDE,
                    "Le debut de la periode est posterieur a sa fin");
        }
        if (ChronoUnit.DAYS.between(du, au) + 1 > JOURS_MAXIMUM) {
            throw new CriteresDeFacturesInvalides(CriteresDeFacturesInvalides.PERIODE_INVALIDE,
                    "La periode depasse " + JOURS_MAXIMUM + " jours");
        }
        return new LocalDate[]{du, au};
    }

    private static boolean retenue(FactureListee f, CriteresDeFactures c) {
        if (c.numero() != null && !contient(f.numeroFacture(), c.numero())) return false;
        if (c.patient() != null && !contient(f.patientNom() + " " + f.patientPrenom(), c.patient())
                && !contient(f.numeroAssurance(), c.patient())) return false;
        if (c.caisse() != null && !c.caisse().equals(f.caisseId())) return false;
        if (c.etat() != null && c.etat() != f.etat()) return false;
        if (c.montantMin() != null && f.montantTtc().compareTo(c.montantMin()) < 0) return false;
        return c.montantMax() == null || f.montantTtc().compareTo(c.montantMax()) <= 0;
    }

    private static boolean contient(String texte, String recherche) {
        return texte != null && texte.toLowerCase(Locale.ROOT).contains(recherche.toLowerCase(Locale.ROOT));
    }

    private static Comparator<FactureListee> ordre(TriDesFactures tri) {
        if (tri == null) return DEPARTAGE;
        Comparator<FactureListee> champ = switch (tri.champ()) {
            case DATE_FACTURATION -> Comparator.comparing(FactureListee::dateFacturation);
            case NUMERO_FACTURE -> Comparator.comparing(FactureListee::numeroFacture, String.CASE_INSENSITIVE_ORDER);
            case PATIENT -> Comparator.comparing((FactureListee f) -> texte(f.patientNom()), String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(f -> texte(f.patientPrenom()), String.CASE_INSENSITIVE_ORDER);
            case CAISSE -> Comparator.comparing((FactureListee f) -> texte(f.caisse()), String.CASE_INSENSITIVE_ORDER);
            case NOMBRE_DE_SEANCES -> Comparator.comparingLong(FactureListee::nombreDeSeances);
            case MONTANT_TTC -> Comparator.comparing(FactureListee::montantTtc);
            case MONTANT_REGLE -> Comparator.comparing(FactureListee::montantRegle);
            case RESTE -> Comparator.comparing(FactureListee::reste);
            case ETAT -> Comparator.comparing(FactureListee::etat);
        };
        return (tri.descendant() ? champ.reversed() : champ).thenComparing(DEPARTAGE);
    }

    private static String texte(String valeur) {
        return valeur == null ? "" : valeur;
    }

    private static TotauxDesFactures totaux(List<FactureListee> factures) {
        BigDecimal ht = ZERO;
        BigDecimal tva = ZERO;
        BigDecimal ttc = ZERO;
        BigDecimal regle = ZERO;
        BigDecimal reste = ZERO;
        for (FactureListee f : factures) {
            ht = ht.add(f.montantHt());
            tva = tva.add(f.montantTva());
            ttc = ttc.add(f.montantTtc());
            regle = regle.add(f.montantRegle());
            reste = reste.add(f.reste());
        }
        return new TotauxDesFactures(factures.size(), ht, tva, ttc, regle, reste);
    }
}
