package com.hemodialyse.backend.contexts.pilotagedirection.infrastructure.acl.patientcouverture;

import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.EffectifDesPatients;
import com.hemodialyse.backend.contexts.pilotagedirection.application.CaissesDesCentresPayeurs;
import com.hemodialyse.backend.contexts.pilotagedirection.application.EffectifsDesPatients;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.EffectifDuCentre;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.Periode;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.RepartitionDeLaSociete.PatientsDeLaCaisse;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.RepartitionDeLaSociete.PrescriptionsDeLAnemie;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.RepartitionDeLaSociete.ProfilDUnPatient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ACL vers Patient et couverture (LEC-PAT sous-slice iii, ADR 0178) : l'effectif d'une période et ses agrégats lus par
 * {@link EffectifDesPatients} (règle de présence RG-PAT-032 portée par Patient et couverture) ; remplace les requêtes SQL de
 * {@code PatientsDesCentresJdbc} (ADR 0102). Traduction du vocabulaire du Pilotage et regroupement par caisse, que Patient et couverture ne
 * connaît pas : la caisse de chaque centre payeur vient de l'annuaire des payeurs ({@link CaissesDesCentresPayeurs}, KB-SEA-84, ADR 0118 :
 * bornée au centre du patient, une chaîne d'un autre centre = caisse vide). Le seuil d'anonymat reste dans le domaine du Pilotage.
 */
public final class EffectifsParLaFiche implements EffectifsDesPatients {

    private final EffectifDesPatients effectif;
    private final CaissesDesCentresPayeurs caisses;

    public EffectifsParLaFiche(EffectifDesPatients effectif, CaissesDesCentresPayeurs caisses) {
        this.effectif = effectif;
        this.caisses = caisses;
    }

    @Override
    public Map<UUID, EffectifDuCentre> parCentre(List<UUID> centres, Periode periode) {
        Map<UUID, EffectifDuCentre> effectifs = new HashMap<>();
        effectif.parCentre(centres, periode.debut(), periode.fin())
                .forEach((centre, e) -> effectifs.put(centre, new EffectifDuCentre(e.patients(), e.sousKt())));
        return effectifs;
    }

    @Override
    public List<ProfilDUnPatient> profils(List<UUID> centres, Periode periode) {
        return effectif.profils(centres, periode.debut(), periode.fin()).stream()
                .map(p -> new ProfilDUnPatient(p.centre(), p.sexe(), p.dateNaissance())).toList();
    }

    /**
     * Patients par caisse : regroupés par centre payeur par Patient et couverture, puis par caisse ici. Nom : le plus grand dans l'ordre des
     * chaînes Java ({@code compareTo}), indépendant de la base ; le {@code MAX(ca.nom)} d'origine suivait la collation du SGBD (non définie
     * par le code), le résultat peut donc différer À COLLATION PRÈS, seulement si deux caisses d'un centre partagent un code sans partager
     * leur nom (aucune contrainte en base : KB-RF5-01). Ordre de sortie garanti (RF5) : centre (texte de l'identifiant), puis code.
     */
    @Override
    public List<PatientsDeLaCaisse> parCaisse(List<UUID> centres, Periode periode) {
        record Cle(UUID centre, String code) {
        }
        Map<UUID, Map<UUID, CaissesDesCentresPayeurs.CaisseDuPayeur>> caissesParCentre = new HashMap<>();
        Map<Cle, long[]> nombres = new LinkedHashMap<>();
        Map<Cle, String> noms = new HashMap<>();
        for (EffectifDesPatients.PatientsDuCentrePayeur ligne : effectif.parCentrePayeur(centres, periode.debut(), periode.fin())) {
            UUID centre = ligne.centre();
            UUID centrePayeur = ligne.centrePayeur();
            CaissesDesCentresPayeurs.CaisseDuPayeur caisse = centrePayeur == null ? null
                    : caissesParCentre.computeIfAbsent(centre, caisses::parCentrePayeur).get(centrePayeur);
            Cle cle = new Cle(centre, caisse == null ? "" : caisse.code());
            nombres.computeIfAbsent(cle, k -> new long[1])[0] += ligne.patients();
            if (caisse != null && caisse.nom() != null) noms.merge(cle, caisse.nom(), (a, b) -> a.compareTo(b) >= 0 ? a : b);
        }
        List<PatientsDeLaCaisse> parCaisse = new ArrayList<>();
        nombres.forEach((cle, nombre) -> parCaisse.add(new PatientsDeLaCaisse(cle.centre(), cle.code(), noms.get(cle), nombre[0])));
        // RF5 (revue complémentaire, constat 1) : le regroupement n'ordonne rien ; tri final en Java, identique sur H2 et PostgreSQL
        parCaisse.sort(Comparator.comparing((PatientsDeLaCaisse c) -> c.centreId().toString()).thenComparing(PatientsDeLaCaisse::code));
        return parCaisse;
    }

    @Override
    public Map<UUID, PrescriptionsDeLAnemie> prescriptionsDeLAnemie(List<UUID> centres, Periode periode) {
        Map<UUID, PrescriptionsDeLAnemie> prescriptions = new HashMap<>();
        effectif.prescriptionsDeLAnemie(centres, periode.debut(), periode.fin())
                .forEach((centre, p) -> prescriptions.put(centre, new PrescriptionsDeLAnemie(p.sousEpo(), p.sousFer())));
        return prescriptions;
    }
}
