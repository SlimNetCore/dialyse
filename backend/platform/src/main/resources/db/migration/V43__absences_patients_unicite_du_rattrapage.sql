-- Migration V43 : unicité du rattrapage d'une absence de patient (slice A2, KB-ABS-11, RG-ABS-021).
--
-- ADDITIVE : un seul index unique PARTIEL, aucun DROP, aucun DELETE, aucune donnée modifiée. Une séance de rattrapage (une date,
-- pour un patient d'un centre) ne rattrape qu'UNE absence rattrapée. L'index ne vise que les absences RATTRAPEE : une absence
-- annulée dont la date de rattrapage a été conservée (KB-ABS-10, annulation d'une rattrapée possible avant A2) ne réserve rien
-- et ne bloque rien.
--
-- PRÉREQUIS (décision 6 du 2026-10-21) : aucun doublon en production. Le contrôle SELECT-only est le point 24.1 (BLOQUANT) de
-- .ddd/migration/verifications-production.md et doit rendre 0 ligne AVANT de déployer ; sinon cette migration échoue (Flyway
-- l'annule, aucune donnée n'est touchée) ET L'APPLICATION NE DÉMARRE PAS (Flyway s'exécute au démarrage) : les doublons sont
-- à traiter avec les experts métier avant le déploiement.
-- PostgreSQL seulement (index partiel) : la base H2 des tests n'a pas cet index, l'application tient l'invariant par un verrou
-- sur toutes les absences du patient (voir AbsencesJdbc.verrouillerPourRattrapage) et le contrat de concurrence l'exécute sur
-- PostgreSQL 16.

CREATE UNIQUE INDEX IF NOT EXISTS uq_absence_patient_rattrapage
    ON absence_patient (center_id, patient_id, date_rattrapage)
    WHERE statut = 'RATTRAPEE' AND date_rattrapage IS NOT NULL;
