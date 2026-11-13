package com.hemodialyse.backend.contexts.dossiermedical.contrat;

import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.DonneesMedicalesDeLaReprise;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Le double en mémoire des données médicales d'un lot de reprise passe le contrat. */
class DonneesMedicalesDeLaRepriseEnMemoireContratTest extends DonneesMedicalesDeLaRepriseContrat {

    private record Ligne(String nature, UUID id, UUID centre, UUID patient) {
    }

    private final List<Ligne> lignes = new ArrayList<>();

    private final DonneesMedicalesDeLaReprise reprise = new DonneesMedicalesDeLaReprise() {
        @Override
        public List<String> lignesDuPatient(UUID patient, String nature) {
            if (!toutes().contains(nature)) throw new IllegalArgumentException(nature);
            return lignes.stream().filter(l -> l.nature().equals(nature) && l.patient().equals(patient)).map(l -> l.id().toString()).toList();
        }

        @Override
        public void supprimerLaDonneeDuLot(UUID centre, String nature, UUID donnee) {
            if (!NATURES_DU_LOT.contains(nature)) throw new IllegalArgumentException(nature);
            lignes.removeIf(l -> l.nature().equals(nature) && l.id().equals(donnee) && l.centre().equals(centre));
        }
    };

    private static List<String> toutes() {
        List<String> natures = new ArrayList<>(DonneesMedicalesDeLaReprise.NATURES_CONTROLEES_AVANT_LES_BONS_DE_SORTIE);
        natures.addAll(DonneesMedicalesDeLaReprise.NATURES_CONTROLEES_APRES_LES_BONS_DE_SORTIE);
        return natures;
    }

    @Override
    protected DonneesMedicalesDeLaReprise reprise() {
        return reprise;
    }

    @Override
    protected void donnee(String nature, UUID id, UUID centre, UUID patient) {
        lignes.add(new Ligne(nature, id, centre, patient));
    }

    @Override
    protected boolean existe(String nature, UUID id) {
        return lignes.stream().anyMatch(l -> l.nature().equals(nature) && l.id().equals(id));
    }
}
