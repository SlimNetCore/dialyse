package com.hemodialyse.backend.infrastructure.acl.reprise;

import com.hemodialyse.backend.contexts.organisation.publishedlanguage.GestionDesCreneaux;
import com.hemodialyse.backend.contexts.organisation.publishedlanguage.GestionDesMedecins;
import com.hemodialyse.backend.contexts.organisation.publishedlanguage.GestionDesSalles;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.ForfaitsDuCentre;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.GestionDesPayeurs;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.Transporteurs;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.infrastructure.acl.organisation.ReferentielsDeLOrganisation;
import com.hemodialyse.backend.infrastructure.acl.patientcouverture.ReferentielsDePatientCouverture;
import com.hemodialyse.backend.infrastructure.migration.port.AppariementDeReferentiels;
import com.hemodialyse.backend.infrastructure.referential.admin.model.ReferentialEntry;
import com.hemodialyse.backend.infrastructure.referential.admin.model.ReferentialKind;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Appariement de la reprise par les lectures publiées des contextes propriétaires (PL11-5, ADR 0160) : salles, créneaux et médecins de
 * l'Organisation ; transporteurs, forfaits et chaîne des payeurs de Patient et couverture. Aucune table n'est lue directement.
 */
public class AppariementParLesContextes implements AppariementDeReferentiels {

    private final GestionDesSalles salles;
    private final GestionDesCreneaux creneaux;
    private final GestionDesMedecins medecins;
    private final Transporteurs transporteurs;
    private final ForfaitsDuCentre forfaits;
    private final GestionDesPayeurs payeurs;

    public AppariementParLesContextes(GestionDesSalles salles, GestionDesCreneaux creneaux, GestionDesMedecins medecins,
                                      Transporteurs transporteurs, ForfaitsDuCentre forfaits, GestionDesPayeurs payeurs) {
        this.salles = salles;
        this.creneaux = creneaux;
        this.medecins = medecins;
        this.transporteurs = transporteurs;
        this.forfaits = forfaits;
        this.payeurs = payeurs;
    }

    @Override
    public List<ReferentialEntry> proposes(CentreId centre, ReferentialKind kind) {
        if (kind.equals(ReferentielsDeLOrganisation.SALLE)) {
            return salles.proposees(centre.valeur()).stream().map(s -> {
                Map<String, String> valeurs = new LinkedHashMap<>();
                valeurs.put("code", s.code());
                valeurs.put("nom", s.nom());
                valeurs.put("isolement", s.isolement());
                valeurs.put("capacite", s.capacite() == null ? null : String.valueOf(s.capacite()));
                return new ReferentialEntry(s.id(), valeurs, Map.of());
            }).toList();
        }
        if (kind.equals(ReferentielsDeLOrganisation.CRENEAU)) {
            return creneaux.proposes(centre.valeur()).stream().map(c -> {
                Map<String, String> valeurs = new LinkedHashMap<>();
                valeurs.put("code", c.code());
                valeurs.put("libelle", c.libelle());
                return new ReferentialEntry(c.id(), valeurs, Map.of());
            }).toList();
        }
        if (kind.equals(ReferentielsDeLOrganisation.MEDECIN)) {
            return medecins.proposes(centre.valeur()).stream().map(m -> {
                Map<String, String> valeurs = new LinkedHashMap<>();
                valeurs.put("nom", m.nom());
                valeurs.put("prenom", m.prenom());
                valeurs.put("specialite", m.specialite());
                return new ReferentialEntry(m.id(), valeurs, Map.of());
            }).toList();
        }
        if (kind.equals(ReferentielsDePatientCouverture.TRANSPORTEUR)) {
            return transporteurs.lister(centre.valeur()).stream()
                    .map(t -> new ReferentialEntry(t.id(), Collections.singletonMap("nom", t.nom()), Map.of())).toList();
        }
        if (kind.equals(ReferentielsDePatientCouverture.FORFAIT)) {
            return forfaits.lister(centre.valeur()).stream().map(f -> {
                Map<String, String> valeurs = new LinkedHashMap<>();
                valeurs.put("code", f.code());
                valeurs.put("libelle", f.libelle());
                valeurs.put("prix", f.prix() == null ? null : f.prix().toPlainString());
                return new ReferentialEntry(f.id(), valeurs, Map.of());
            }).toList();
        }
        if (ReferentielsDePatientCouverture.PAYEURS.contains(kind)) {
            GestionDesPayeurs.Niveau niveau = kind.equals(ReferentielsDePatientCouverture.CAISSE) ? GestionDesPayeurs.Niveau.CAISSE
                    : kind.equals(ReferentielsDePatientCouverture.AGENCE) ? GestionDesPayeurs.Niveau.AGENCE
                    : GestionDesPayeurs.Niveau.CENTRE_PAYEUR;
            return payeurs.toutes(centre.valeur(), niveau).stream().map(p -> {
                Map<String, String> valeurs = new LinkedHashMap<>();
                valeurs.put("code", p.code());
                valeurs.put("nom", p.nom());
                if (kind.equals(ReferentielsDePatientCouverture.CAISSE)) valeurs.put("typeCaisse", p.typeCaisse());
                if (kind.equals(ReferentielsDePatientCouverture.AGENCE)) {
                    valeurs.put("caisse", p.parent() == null ? null : p.parent().toString());
                }
                if (kind.equals(ReferentielsDePatientCouverture.CENTRE_PAYEUR)) {
                    valeurs.put("adresse", p.adresse());
                    valeurs.put("agence", p.parent() == null ? null : p.parent().toString());
                }
                return new ReferentialEntry(p.id(), valeurs, Map.of());
            }).toList();
        }
        throw new IllegalArgumentException("Référentiel inconnu de la reprise : " + kind);
    }
}
