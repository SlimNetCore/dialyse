package com.hemodialyse.backend.contexts.seance.contrat;

import com.hemodialyse.backend.contexts.seance.publishedlanguage.ForfaitsImposesAuxSeancesPublies;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.SeancesDeLaReprisePubliees;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.SeancesDuCahierPubliees;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.SeancesRealiseesDesCentresPubliees;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.StatistiquesDesSeancesPubliees;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Double en mémoire des ports publiés SE7e (ADR 0099, corrigé par les ADR 0117 et 0118 : caisses sur le même prédicat que la vue d'ensemble,
 * centre payeur et volet bornés au centre, statistiques des séances réalisées), fidèle au contrat : statut réalisé en casse exacte, bornes
 * incluses, clés de l'évolution du poids en minuscules, moyennes à 12 décimales (10 garanties), tri date, création connue d'abord puis
 * identifiant (KB-SEA-86), séance annulée ignorée par la recherche d'une séance déjà présente (KB-SEA-89).
 */
public final class PilotageEtRapportsDesSeancesEnMemoire implements SeancesRealiseesDesCentresPubliees, StatistiquesDesSeancesPubliees,
        SeancesDuCahierPubliees, ForfaitsImposesAuxSeancesPublies, SeancesDeLaReprisePubliees {

    private static final Set<String> REALISES = Set.of("VALIDEE", "SIGNEE", "FACTUREE");
    private static final Set<String> CHAMPS = Set.of("patientId", "dateSeance", "statut");

    private static final class Ligne {
        final UUID id;
        final UUID centre;
        UUID patient;
        LocalDate date;
        String statut;
        final Instant creeLe;
        UUID forfait;
        UUID facture;

        Ligne(UUID id, UUID centre, UUID patient, LocalDate date, String statut, Instant creeLe) {
            this.id = id;
            this.centre = centre;
            this.patient = patient;
            this.date = date;
            this.statut = statut;
            this.creeLe = creeLe;
        }
    }

    private record Fiche(UUID centre, UUID payeur) {
    }

    /** {@code centre} : celui du centre payeur ; {@code centreDeLAgence} : celui de l'agence et de la caisse (L8). */
    private record Caisse(UUID centre, UUID centreDeLAgence, String code, String nom) {
    }

    private record Volet(UUID centre, BigDecimal poidsAvant, BigDecimal poidsApres, String tensionAvant, BigDecimal ultrafiltration) {
    }

    private record Prescription(UUID id, UUID centre, UUID patient, LocalDate date, BigDecimal poidsSec, Instant majLe) {
    }

    private final Map<UUID, Ligne> seances = new LinkedHashMap<>();
    private final Map<UUID, Fiche> fiches = new HashMap<>();
    private final Map<UUID, Caisse> payeurs = new HashMap<>();
    private final Map<UUID, Volet> volets = new HashMap<>();

    /** Le volet d'une séance, seulement s'il est posé sous le centre de la séance (KB-SEA-85 a). */
    private Volet voletDe(Ligne l) {
        Volet v = volets.get(l.id);
        return v != null && v.centre().equals(l.centre) ? v : null;
    }
    private final List<Prescription> prescriptions = new ArrayList<>();

    // ---- semis et inspection

    public void payeur(UUID payeur, UUID centre, String codeCaisse, String nomCaisse) {
        payeurs.put(payeur, new Caisse(centre, centre, codeCaisse, nomCaisse));
    }

    public void payeurDontLAgenceEstDUnAutreCentre(UUID payeur, UUID centreDuPayeur, UUID centreDeLAgence, String codeCaisse, String nomCaisse) {
        payeurs.put(payeur, new Caisse(centreDuPayeur, centreDeLAgence, codeCaisse, nomCaisse));
    }

    public void patient(UUID id, UUID centre, UUID payeur) {
        fiches.put(id, new Fiche(centre, payeur));
    }

    public void seance(UUID id, UUID centre, UUID patient, LocalDate date, String statut, Instant creeLe) {
        seances.put(id, new Ligne(id, centre, patient, date, statut, creeLe));
    }

    public void volet(UUID centre, UUID seance, BigDecimal poidsAvant, BigDecimal poidsApres, String tensionAvant, BigDecimal ultrafiltration) {
        volets.put(seance, new Volet(centre, poidsAvant, poidsApres, tensionAvant, ultrafiltration));
    }

    public void prescription(UUID id, UUID centre, UUID patient, LocalDate date, BigDecimal poidsSec, Instant majLe) {
        prescriptions.add(new Prescription(id, centre, patient, date, poidsSec, majLe));
    }

    public void forfaitImpose(UUID seance, UUID forfait) {
        seances.get(seance).forfait = forfait;
    }

    public void facture(UUID seance, UUID facture) {
        seances.get(seance).facture = facture;
    }

    public String statutDe(UUID seance) {
        Ligne l = seances.get(seance);
        return l == null ? null : l.statut;
    }

    public UUID patientDe(UUID seance) {
        return seances.get(seance).patient;
    }

    // ---- pilotage

    private Stream<Ligne> realisees(List<UUID> centres, LocalDate du, LocalDate au) {
        return seances.values().stream().filter(l -> centres.contains(l.centre) && REALISES.contains(l.statut))
                .filter(l -> !l.date.isBefore(du) && !l.date.isAfter(au));
    }

    @Override
    public Map<UUID, Long> parCentre(List<UUID> centres, LocalDate du, LocalDate au) {
        Map<UUID, Long> n = new HashMap<>();
        realisees(centres, du, au).forEach(l -> n.merge(l.centre, 1L, Long::sum));
        return n;
    }

    @Override
    public long auTotal(List<UUID> centres, LocalDate du, LocalDate au) {
        return realisees(centres, du, au).count();
    }

    @Override
    public List<MoisRealisePublie> parMois(List<UUID> centres, LocalDate du, LocalDate au) {
        Map<List<Object>, Long> n = new LinkedHashMap<>();
        realisees(centres, du, au).forEach(l -> n.merge(List.of(l.centre, l.date.getYear(), l.date.getMonthValue()), 1L, Long::sum));
        return n.entrySet().stream().map(e -> new MoisRealisePublie((UUID) e.getKey().get(0), (Integer) e.getKey().get(1),
                (Integer) e.getKey().get(2), e.getValue())).toList();
    }

    @Override
    public List<CaisseRealiseePubliee> parCaisse(List<UUID> centres, LocalDate du, LocalDate au) {
        Map<List<Object>, Long> n = new LinkedHashMap<>();
        Map<List<Object>, String> noms = new HashMap<>();
        realisees(centres, du, au).forEach(l -> {
            Fiche f = fiches.get(l.patient);
            boolean ficheDuCentre = f != null && f.centre().equals(l.centre);
            Caisse c = ficheDuCentre && f.payeur() != null ? payeurs.get(f.payeur()) : null;
            if (c != null && (!c.centre().equals(l.centre) || !c.centreDeLAgence().equals(l.centre))) c = null;
            List<Object> cle = List.of(l.centre, c == null ? "" : c.code());
            n.merge(cle, 1L, Long::sum);
            if (c != null) noms.put(cle, c.nom());
        });
        return n.entrySet().stream().map(e -> new CaisseRealiseePubliee((UUID) e.getKey().get(0), (String) e.getKey().get(1),
                noms.get(e.getKey()), e.getValue())).toList();
    }

    // ---- statistiques

    private List<Ligne> duPatient(UUID centre, UUID patient, LocalDate du, LocalDate au) {
        return seances.values().stream().filter(l -> l.centre.equals(centre) && l.patient.equals(patient) && REALISES.contains(l.statut))
                .filter(l -> (du == null || !l.date.isBefore(du)) && (au == null || !l.date.isAfter(au)))
                .sorted(Comparator.<Ligne, LocalDate>comparing(l -> l.date)
                        .thenComparing(l -> l.creeLe, Comparator.nullsLast(Comparator.<Instant>naturalOrder()))
                        .thenComparing((x, y) -> SeancesRealiseesEtConstantesEnMemoire.enOctetsNonSignes(x.id, y.id)))
                .toList();
    }

    private static BigDecimal uf(Volet v) {
        if (v == null) return null;
        if (v.ultrafiltration() != null) return v.ultrafiltration();
        if (v.poidsAvant() == null || v.poidsApres() == null) return null;
        return v.poidsAvant().subtract(v.poidsApres()).multiply(BigDecimal.valueOf(1000));
    }

    private Double moyenne(UUID centre, UUID patient, LocalDate du, LocalDate au, Function<Volet, BigDecimal> mesure) {
        List<BigDecimal> valeurs = duPatient(centre, patient, du, au).stream().map(this::voletDe)
                .map(v -> v == null ? null : mesure.apply(v)).filter(Objects::nonNull).toList();
        if (valeurs.isEmpty()) return null;
        BigDecimal somme = valeurs.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return somme.divide(BigDecimal.valueOf(valeurs.size()), 12, RoundingMode.HALF_UP).doubleValue();
    }

    @Override
    public long nombreDeSeances(UUID centre, UUID patient, LocalDate du, LocalDate au) {
        return duPatient(centre, patient, du, au).size();
    }

    @Override
    public Double moyenneDuPoidsAvant(UUID centre, UUID patient, LocalDate du, LocalDate au) {
        return moyenne(centre, patient, du, au, Volet::poidsAvant);
    }

    @Override
    public Double moyenneDuPoidsApres(UUID centre, UUID patient, LocalDate du, LocalDate au) {
        return moyenne(centre, patient, du, au, Volet::poidsApres);
    }

    @Override
    public Double moyenneDeLUltrafiltration(UUID centre, UUID patient, LocalDate du, LocalDate au) {
        return moyenne(centre, patient, du, au, PilotageEtRapportsDesSeancesEnMemoire::uf);
    }

    private BigDecimal poidsSec(Ligne l) {
        return prescriptions.stream().filter(p -> p.patient().equals(l.patient) && p.centre().equals(l.centre) && p.poidsSec() != null)
                .filter(p -> !p.date().isAfter(l.date))
                .max(Comparator.comparing(Prescription::date)
                        .thenComparing(Prescription::majLe, Comparator.nullsFirst(Comparator.<Instant>naturalOrder()))
                        .thenComparing((x, y) -> SeancesRealiseesEtConstantesEnMemoire.enOctetsNonSignes(x.id(), y.id())))
                .map(Prescription::poidsSec).orElse(null);
    }

    @Override
    public List<Map<String, Object>> evolutionDuPoids(UUID centre, UUID patient, LocalDate du, LocalDate au) {
        List<Map<String, Object>> lignes = new ArrayList<>();
        for (Ligne l : duPatient(centre, patient, du, au)) {
            Volet v = voletDe(l);
            Map<String, Object> ligne = new LinkedHashMap<>();
            ligne.put("date_seance", java.sql.Date.valueOf(l.date));
            ligne.put("poids_avant_kg", v == null ? null : v.poidsAvant());
            ligne.put("poids_apres_kg", v == null ? null : v.poidsApres());
            ligne.put("poids_sec_cible_kg", poidsSec(l));
            ligne.put("uf_reelle_ml", uf(v));
            lignes.add(ligne);
        }
        return lignes;
    }

    @Override
    public List<TensionsDeSeancePubliees> evolutionDeLaTension(UUID centre, UUID patient, LocalDate du, LocalDate au) {
        return duPatient(centre, patient, du, au).stream().map(l -> {
            Volet v = voletDe(l);
            return new TensionsDeSeancePubliees(l.date, v == null ? null : v.tensionAvant(), null);
        }).toList();
    }

    // ---- cahier, forfait

    @Override
    public int nombreDeSeancesAImprimer(UUID centre, UUID patient, LocalDate du, LocalDate au) {
        return (int) realisees(List.of(centre), du, au).filter(l -> l.patient.equals(patient)).count();
    }

    @Override
    public long nombreDeSeancesQuiImposentLeForfait(UUID forfait) {
        return seances.values().stream().filter(l -> forfait.equals(l.forfait)).count();
    }

    // ---- reprise

    private static void verifier(Map<String, Object> champs) {
        for (String champ : champs.keySet()) {
            if (!CHAMPS.contains(champ)) throw new IllegalArgumentException("Champ non autorisé pour seances : " + champ);
        }
    }

    private static Object valeur(Ligne l, String champ) {
        return switch (champ) {
            case "patientId" -> l.patient;
            case "dateSeance" -> l.date;
            default -> l.statut;
        };
    }

    @Override
    public Optional<UUID> seanceDejaPresente(UUID centre, Map<String, Object> cle) {
        verifier(cle);
        return seances.values().stream().filter(l -> l.centre.equals(centre)).filter(l -> !"ANNULEE".equals(l.statut))
                .filter(l -> cle.entrySet().stream().allMatch(e -> Objects.equals(valeur(l, e.getKey()), e.getValue())))
                .min(Comparator.comparing(l -> l.creeLe)).map(l -> l.id);
    }

    @Override
    public UUID reprendre(UUID centre, Map<String, Object> valeurs) {
        verifier(valeurs);
        UUID id = UUID.randomUUID();
        seance(id, centre, (UUID) valeurs.get("patientId"), (LocalDate) valeurs.get("dateSeance"), (String) valeurs.get("statut"), Instant.now());
        return id;
    }

    @Override
    public void modifier(UUID centre, UUID seance, Map<String, Object> valeurs) {
        verifier(valeurs);
        Ligne l = seances.get(seance);
        if (l == null || !l.centre.equals(centre)) return;
        valeurs.forEach((champ, v) -> {
            if (champ.equals("dateSeance")) l.date = (LocalDate) v;
            if (champ.equals("statut")) l.statut = (String) v;
        });
    }

    @Override
    public List<String> seancesDuPatient(UUID patient) {
        return seances.values().stream().filter(l -> l.patient.equals(patient)).map(l -> l.id.toString()).toList();
    }

    @Override
    public List<String> factureDeLaSeance(UUID seance) {
        Ligne l = seances.get(seance);
        return l == null || l.facture == null ? List.of() : List.of(l.facture.toString());
    }

    @Override
    public void retirer(UUID centre, UUID seance) {
        Ligne l = seances.get(seance);
        if (l != null && l.centre.equals(centre)) seances.remove(seance);
    }
}
