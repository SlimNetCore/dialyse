package com.hemodialyse.backend.contexts.dossiermedical.contrat;

import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.RepriseDeLHistoriqueMedical;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Le double en mémoire de l'écriture de l'historique médical d'un lot de reprise passe le contrat. */
class RepriseDeLHistoriqueMedicalEnMemoireContratTest extends RepriseDeLHistoriqueMedicalContrat {

    private static final class Ligne {
        final String nature;
        final UUID id = UUID.randomUUID();
        final UUID centre;
        final Map<String, Object> valeurs = new HashMap<>();
        boolean cree = true;
        boolean modifie;

        Ligne(String nature, UUID centre) {
            this.nature = nature;
            this.centre = centre;
        }
    }

    private final List<Ligne> lignes = new ArrayList<>();

    private static void controler(String nature, Iterable<String> champs) {
        if (!RepriseDeLHistoriqueMedical.NATURES.contains(nature)) throw new IllegalArgumentException(nature);
        for (String champ : champs) {
            if (!champs(nature).contains(champ)) throw new IllegalArgumentException(champ);
        }
    }

    private final RepriseDeLHistoriqueMedical reprise = new RepriseDeLHistoriqueMedical() {
        @Override
        public Optional<UUID> dejaPresente(UUID centre, String nature, Map<String, Object> cle) {
            controler(nature, cle.keySet());
            return lignes.stream().filter(l -> l.nature.equals(nature) && l.centre.equals(centre))
                    .filter(l -> cle.entrySet().stream().allMatch(e -> Objects.equals(l.valeurs.get(e.getKey()), e.getValue())))
                    .map(l -> l.id).findFirst();
        }

        @Override
        public UUID reprendre(UUID centre, String nature, Map<String, Object> valeurs) {
            controler(nature, valeurs.keySet());
            Ligne ligne = new Ligne(nature, centre);
            ligne.valeurs.putAll(valeurs);
            ligne.modifie = !ABORDS_VASCULAIRES.equals(nature);
            lignes.add(ligne);
            return ligne.id;
        }

        @Override
        public void completer(UUID centre, String nature, UUID donnee, Map<String, Object> valeurs) {
            controler(nature, valeurs.keySet());
            boolean ecrit = false;
            for (Ligne l : lignes) {
                if (!l.id.equals(donnee) || !l.centre.equals(centre) || !l.nature.equals(nature)) continue;
                for (Map.Entry<String, Object> e : valeurs.entrySet()) {
                    if (e.getKey().equals("patientId")) continue;
                    l.valeurs.put(e.getKey(), e.getValue());
                    ecrit = true;
                }
                l.modifie = !ABORDS_VASCULAIRES.equals(nature) && (ecrit || l.modifie);
            }
        }
    };

    @Override
    protected RepriseDeLHistoriqueMedical reprise() {
        return reprise;
    }

    private Ligne ligne(UUID id) {
        return lignes.stream().filter(l -> l.id.equals(id)).findFirst().orElseThrow();
    }

    @Override
    protected String valeur(String nature, UUID id, String champ) {
        Object v = ligne(id).valeurs.get(champ);
        return v == null ? null : String.valueOf(v);
    }

    @Override
    protected boolean horodate(String nature, UUID id, String colonne) {
        return colonne.equals("created_at") ? ligne(id).cree : ligne(id).modifie;
    }
}
