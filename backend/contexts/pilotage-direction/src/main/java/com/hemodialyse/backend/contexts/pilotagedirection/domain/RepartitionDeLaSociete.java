package com.hemodialyse.backend.contexts.pilotagedirection.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Les répartitions de la direction d'une société, par centre : patients par sexe et par tranche d'âge, patients / séances / chiffre
 * d'affaires HT par caisse d'assurance, traitement de l'anémie. Agrégats anonymes : tout effectif de patients inférieur au seuil est
 * publié masqué ({@link SeuilDAnonymat}) ; les séances, le chiffre d'affaires et les administrations ne sont jamais masqués.
 * <p>
 * Règles (RG-DIR) : le sexe est lu à l'initiale (M, F, sinon autre, la casse et les blancs ignorés) ; la caisse d'un patient sans caisse a
 * le code vide ; les lignes de caisse sont triées par NOM de centre (sans tenir compte de la casse) puis par chiffre d'affaires HT
 * décroissant ; les totaux par caisse, par chiffre d'affaires HT décroissant, sont publiés sans effectif de patients dès qu'un centre porte
 * cette caisse avec un effectif masqué (suppression complémentaire, KB-DIR-01 corrigé le 2026-11-09) ; une société sans centre n'a
 * aucune répartition.
 */
public record RepartitionDeLaSociete(SocieteSuivie societe, Periode periode, OffsetDateTime genereLe, List<ProfilDuCentre> profils,
                                     List<CaisseDuCentre> caisses, List<CaisseDeLaSociete> caissesDeLaSociete,
                                     List<TraitementDeLAnemieDuCentre> anemie) {

    public RepartitionDeLaSociete {
        profils = List.copyOf(profils);
        caisses = List.copyOf(caisses);
        caissesDeLaSociete = List.copyOf(caissesDeLaSociete);
        anemie = List.copyOf(anemie);
    }

    public static RepartitionDeLaSociete sansCentre(SocieteSuivie societe, Periode periode, OffsetDateTime genereLe) {
        return new RepartitionDeLaSociete(societe, periode, genereLe, List.of(), List.of(), List.of(), List.of());
    }

    /**
     * @param profils             un élément par patient de l'effectif de la période (sexe et naissance, sans identité)
     * @param patientsParCaisse   patients de l'effectif par centre et par caisse
     * @param seancesParCaisse    séances réalisées par centre et par caisse
     * @param chiffreParCaisse    chiffre d'affaires HT par centre et par caisse
     * @param administrations     administrations de l'anémie par centre
     * @param prescriptions       patients sous EPO / fer prescrits par centre
     */
    public static RepartitionDeLaSociete constituer(SocieteSuivie societe, Periode periode, LocalDate aujourdhui, OffsetDateTime genereLe,
                                                    List<ProfilDUnPatient> profils, List<PatientsDeLaCaisse> patientsParCaisse,
                                                    List<SeancesDeLaCaisse> seancesParCaisse, List<ChiffreDeLaCaisse> chiffreParCaisse,
                                                    Map<UUID, AdministrationsDeLAnemie> administrations,
                                                    Map<UUID, PrescriptionsDeLAnemie> prescriptions) {
        Map<UUID, long[]> sexes = new HashMap<>();
        Map<UUID, Map<TrancheDAge, Long>> ages = new HashMap<>();
        for (ProfilDUnPatient p : profils) {
            sexes.computeIfAbsent(p.centreId(), k -> new long[3])[p.indiceDuSexe()]++;
            ages.computeIfAbsent(p.centreId(), k -> new EnumMap<>(TrancheDAge.class))
                    .merge(TrancheDAge.de(p.naissance(), aujourdhui), 1L, Long::sum);
        }

        Map<String, Cellule> cellules = new LinkedHashMap<>();
        patientsParCaisse.forEach(c -> cellule(cellules, c.centreId(), c.code(), c.nom()).patients += c.patients());
        seancesParCaisse.forEach(c -> cellule(cellules, c.centreId(), c.code(), c.nom()).seances += c.seances());
        chiffreParCaisse.forEach(c -> {
            Cellule cellule = cellule(cellules, c.centreId(), c.code(), c.nom());
            cellule.caHt = cellule.caHt.add(c.caHt());
        });

        Map<UUID, String> noms = new HashMap<>();
        societe.centres().forEach(c -> noms.put(c.id(), c.nom()));

        List<ProfilDuCentre> profilsDesCentres = new ArrayList<>();
        List<TraitementDeLAnemieDuCentre> anemies = new ArrayList<>();
        for (CentreDeLaSociete centre : societe.centres()) {
            long[] s = sexes.getOrDefault(centre.id(), new long[3]);
            profilsDesCentres.add(new ProfilDuCentre(centre.id(), centre.nom(), s[0], s[1], s[2],
                    ages.getOrDefault(centre.id(), new EnumMap<>(TrancheDAge.class))));
            AdministrationsDeLAnemie a = administrations.getOrDefault(centre.id(), AdministrationsDeLAnemie.aucune());
            PrescriptionsDeLAnemie p = prescriptions.getOrDefault(centre.id(), PrescriptionsDeLAnemie.aucune());
            anemies.add(new TraitementDeLAnemieDuCentre(centre.id(), centre.nom(), a.patientsEpo(), a.patientsFer(),
                    a.administreesEpo(), a.administreesFer(), a.nonAdministrees(), p.sousEpo(), p.sousFer()));
        }

        List<CaisseDuCentre> lignes = new ArrayList<>();
        Map<String, Cellule> totaux = new LinkedHashMap<>();
        for (Cellule cellule : cellules.values()) {
            lignes.add(new CaisseDuCentre(cellule.centreId, noms.get(cellule.centreId), cellule.code, cellule.nom,
                    cellule.patients, cellule.seances, cellule.caHt));
            Cellule total = totaux.computeIfAbsent(cellule.code, k -> new Cellule(null, cellule.code, cellule.nom));
            total.patients += cellule.patients;
            total.composeDeMasques |= SeuilDAnonymat.estMasque(cellule.patients);
            total.seances += cellule.seances;
            total.caHt = total.caHt.add(cellule.caHt);
        }
        lignes.sort((x, y) -> {
            int parCentre = String.valueOf(x.centre()).compareToIgnoreCase(String.valueOf(y.centre()));
            return parCentre != 0 ? parCentre : y.caHt().compareTo(x.caHt());
        });
        List<CaisseDeLaSociete> parSociete = totaux.values().stream()
                .map(t -> new CaisseDeLaSociete(t.code, t.nom, t.patients, t.seances, t.caHt, t.composeDeMasques))
                .sorted((x, y) -> y.caHt().compareTo(x.caHt())).toList();
        return new RepartitionDeLaSociete(societe, periode, genereLe, profilsDesCentres, lignes, parSociete, anemies);
    }

    private static Cellule cellule(Map<String, Cellule> cellules, UUID centre, String code, String nom) {
        Cellule cellule = cellules.computeIfAbsent(centre + "|" + code, k -> new Cellule(centre, code, nom));
        if (cellule.nom == null && nom != null) cellule.nom = nom;
        return cellule;
    }

    /** Un patient de l'effectif : son centre, son sexe tel que saisi et sa date de naissance (jamais son identité). */
    public record ProfilDUnPatient(UUID centreId, String sexe, LocalDate naissance) {
        int indiceDuSexe() {
            String initiale = sexe == null ? "" : sexe.trim().toUpperCase(Locale.ROOT);
            return initiale.startsWith("M") ? 0 : initiale.startsWith("F") ? 1 : 2;
        }
    }

    public record PatientsDeLaCaisse(UUID centreId, String code, String nom, long patients) {
    }

    public record SeancesDeLaCaisse(UUID centreId, String code, String nom, long seances) {
    }

    public record ChiffreDeLaCaisse(UUID centreId, String code, String nom, BigDecimal caHt) {
    }

    public record AdministrationsDeLAnemie(long patientsEpo, long patientsFer, long administreesEpo, long administreesFer,
                                           long nonAdministrees) {
        public static AdministrationsDeLAnemie aucune() {
            return new AdministrationsDeLAnemie(0, 0, 0, 0, 0);
        }
    }

    public record PrescriptionsDeLAnemie(long sousEpo, long sousFer) {
        public static PrescriptionsDeLAnemie aucune() {
            return new PrescriptionsDeLAnemie(0, 0);
        }
    }

    /** Sexe et âge des patients d'un centre (effectifs bruts ; {@link #masculinPublie()}… les masquent). */
    public record ProfilDuCentre(UUID centreId, String nom, long masculin, long feminin, long autre, Map<TrancheDAge, Long> ages) {
        public Long masculinPublie() {
            return SeuilDAnonymat.masquer(masculin);
        }

        public Long femininPublie() {
            return SeuilDAnonymat.masquer(feminin);
        }

        public Long autrePublie() {
            return SeuilDAnonymat.masquer(autre);
        }

        /** Les six tranches dans l'ordre d'affichage, effectifs masqués. */
        public Map<TrancheDAge, Long> tranchesPubliees() {
            Map<TrancheDAge, Long> tranches = new LinkedHashMap<>();
            for (TrancheDAge t : TrancheDAge.values()) tranches.put(t, SeuilDAnonymat.masquer(ages.getOrDefault(t, 0L)));
            return tranches;
        }
    }

    public record CaisseDuCentre(UUID centreId, String centre, String code, String nom, long patients, long seances, BigDecimal caHt) {
        public Long patientsPublies() {
            return SeuilDAnonymat.masquer(patients);
        }
    }

    /**
     * Le total d'une caisse sur la société. Suppression complémentaire (KB-DIR-01, corrigé le 2026-11-09) : {@code patientsComposesDeMasques}
     * est vrai si l'un des centres porte cette caisse avec un effectif masqué ; le total est alors publié {@code null}.
     */
    public record CaisseDeLaSociete(String code, String nom, long patients, long seances, BigDecimal caHt,
                                    boolean patientsComposesDeMasques) {
        public Long patientsPublies() {
            return patientsComposesDeMasques ? null : SeuilDAnonymat.masquer(patients);
        }
    }

    public record TraitementDeLAnemieDuCentre(UUID centreId, String nom, long patientsEpo, long patientsFer, long administreesEpo,
                                              long administreesFer, long nonAdministrees, long sousEpo, long sousFer) {
        /** Part des administrations effectives sur toutes les administrations prévues, à une décimale ; absente sans administration. */
        public BigDecimal tauxAdministration() {
            long total = administreesEpo + administreesFer + nonAdministrees;
            return total == 0 ? null
                    : BigDecimal.valueOf((administreesEpo + administreesFer) * 100.0 / total).setScale(1, RoundingMode.HALF_UP);
        }

        public Long patientsEpoPublies() {
            return SeuilDAnonymat.masquer(patientsEpo);
        }

        public Long patientsFerPublies() {
            return SeuilDAnonymat.masquer(patientsFer);
        }

        public Long sousEpoPublies() {
            return SeuilDAnonymat.masquer(sousEpo);
        }

        public Long sousFerPublies() {
            return SeuilDAnonymat.masquer(sousFer);
        }
    }

    private static final class Cellule {
        final UUID centreId;
        final String code;
        String nom;
        long patients;
        boolean composeDeMasques;
        long seances;
        BigDecimal caHt = ChiffresFinanciers.montantNul();

        Cellule(UUID centreId, String code, String nom) {
            this.centreId = centreId;
            this.code = code;
            this.nom = nom;
        }
    }
}
