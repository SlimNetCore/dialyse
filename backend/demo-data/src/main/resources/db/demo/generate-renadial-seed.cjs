// Générateur du jeu de test « RENADIAL » : 1 société, 17 centres, 12 mois de données, pour tester le
// tableau de bord de la direction. Sortie : un unique fichier SQL (H2/PostgreSQL compatible), pas de PL/pgSQL,
// pas de fonction spécifique à un moteur (AGENTS.md §10).
'use strict';
const fs = require('fs');
const path = require('path');

// ───────────────────────────── PRNG déterministe (reproductible) ─────────────────────────────
let seed = 42;

function rnd() { // [0,1)
    seed = (seed * 1103515245 + 12345) & 0x7fffffff;
    return seed / 0x7fffffff;
}

function rint(min, max) {
    return Math.floor(rnd() * (max - min + 1)) + min;
}

function pick(arr) {
    return arr[rint(0, arr.length - 1)];
}

function chance(p) {
    return rnd() < p;
}

// ───────────────────────────── UUID v4 (aléatoire cryptographique — jamais de collision) ─────────────────────────────
const crypto = require('crypto');

function uuid() {
    return crypto.randomUUID();
}

// ───────────────────────────── Dates ─────────────────────────────
function fmt(d) {
    return d.toISOString().slice(0, 10);
}

function addMonths(d, n) {
    const r = new Date(d);
    r.setMonth(r.getMonth() + n);
    return r;
}

function addDays(d, n) {
    const r = new Date(d);
    r.setDate(r.getDate() + n);
    return r;
}

function endOfMonth(d) {
    return new Date(d.getFullYear(), d.getMonth() + 1, 0);
}

function startOfMonth(d) {
    return new Date(d.getFullYear(), d.getMonth(), 1);
}

const TODAY = new Date();
const MONTHS = []; // 12 mois écoulés, du plus ancien au plus récent (mois courant inclus)
for (let i = 11; i >= 0; i--) MONTHS.push(startOfMonth(addMonths(TODAY, -i)));

// ───────────────────────────── Sortie SQL ─────────────────────────────
const out = [];
const esc = (s) => s === null || s === undefined ? 'NULL' : `'${String(s).replace(/'/g, "''")}'`;
const q = (s) => `'${s}'`;
const num = (n) => n === null || n === undefined ? 'NULL' : String(n);

function insert(table, cols, rows) {
    if (rows.length === 0) return;
    out.push(`INSERT INTO ${table} (${cols.join(', ')})
              VALUES`);
    out.push(rows.map((r, i) => `  (${r.join(', ')})${i === rows.length - 1 ? ';' : ','}`).join('\n'));
}

// ───────────────────────────── Société + centres ─────────────────────────────
const SOC = uuid();
out.push('-- ═══════════════════════════════════════════════════════════════════');
out.push('-- Jeu de test RENADIAL : 1 société, 17 centres, 12 mois de données.');
out.push(`-- Généré le ${fmt(TODAY)} — fenêtre de données : ${fmt(MONTHS[0])} au ${fmt(TODAY)}.`);
out.push('-- Rejouable : chaque objet est préfixé RENADIAL / RD- pour un nettoyage ciblé (voir le script de purge).');
out.push('-- ═══════════════════════════════════════════════════════════════════\n');

insert('societes', ['id', 'code', 'raison_sociale', 'adresse', 'ville', 'wilaya', 'telephone', 'email', 'nif', 'nis', 'rc', 'actif', 'created_at'],
    [[q(SOC), q('RENADIAL'), q('RENADIAL — Réseau National de Dialyse'), q('12 rue des Frères Bouadou'), q('Alger'), q('Alger'),
        q('021 00 00 00'), q('contact@renadial.dz'), q('000116001234567'), q('000116099887766'), q('16/00-1234567 A 26'), 'TRUE', 'CURRENT_TIMESTAMP']]);

// 17 centres, répartis sur plusieurs wilayas, tailles variées (dont certains sous le seuil d'anonymat = 5)
const CENTRES = [
    {nom: 'Centre RENADIAL Alger Centre', ville: 'Alger', patients: 52, tier: 0},
    {nom: 'Centre RENADIAL Bab Ezzouar', ville: 'Alger', patients: 45, tier: 1},
    {nom: 'Centre RENADIAL Oran', ville: 'Oran', patients: 48, tier: 0},
    {nom: 'Centre RENADIAL Es Senia', ville: 'Oran', patients: 30, tier: 2},
    {nom: 'Centre RENADIAL Constantine', ville: 'Constantine', patients: 40, tier: 1},
    {nom: 'Centre RENADIAL Annaba', ville: 'Annaba', patients: 35, tier: 0},
    {nom: 'Centre RENADIAL Sétif', ville: 'Sétif', patients: 28, tier: 1},
    {nom: 'Centre RENADIAL Batna', ville: 'Batna', patients: 25, tier: 2},
    {nom: 'Centre RENADIAL Blida', ville: 'Blida', patients: 22, tier: 0},
    {nom: 'Centre RENADIAL Tizi Ouzou', ville: 'Tizi Ouzou', patients: 20, tier: 1},
    {nom: 'Centre RENADIAL Béjaïa', ville: 'Béjaïa', patients: 18, tier: 1},
    {nom: 'Centre RENADIAL Tlemcen', ville: 'Tlemcen', patients: 15, tier: 2},
    {nom: 'Centre RENADIAL Ouargla', ville: 'Ouargla', patients: 12, tier: 2},
    {nom: 'Centre RENADIAL Béchar', ville: 'Béchar', patients: 8, tier: 1},
    {nom: 'Centre RENADIAL Tébessa', ville: 'Tébessa', patients: 4, tier: 0},
    {nom: 'Centre RENADIAL Adrar', ville: 'Adrar', patients: 3, tier: 2},
    {nom: 'Centre RENADIAL Ghardaïa (nouveau)', ville: 'Ghardaïa', patients: 2, tier: 1},
];

const centreRows = [];
CENTRES.forEach((c, i) => {
    c.id = uuid();
    c.code = `RD-${String(i + 1).padStart(2, '0')}`;
    centreRows.push([q(c.id), q(c.code), q(c.nom), q(SOC), 'TRUE']);
});
insert('centers', ['id', 'code', 'name', 'societe_id', 'actif'], centreRows);

// ───────────────────────────── Caisses d'assurance (par centre) ─────────────────────────────
const CAISSES = [
    {code: 'CNAS', nom: 'CNAS — Caisse nationale des assurés sociaux', part: 0.55},
    {code: 'CASNOS', nom: 'CASNOS — Caisse des non-salariés', part: 0.2},
    {code: 'MILITAIRE', nom: 'Caisse militaire', part: 0.1},
];
// part restante (~15 %) : sans caisse renseignée (caisseCode vide)

for (const c of CENTRES) {
    c.caisses = [];
    const caisseRows = [], agenceRows = [], payeurRows = [];
    for (const cs of CAISSES) {
        const caisseId = uuid(), agenceId = uuid(), payeurId = uuid();
        caisseRows.push([q(caisseId), q(c.id), q(cs.code), q(cs.nom)]);
        agenceRows.push([q(agenceId), q(c.id), q(caisseId), q(cs.code + '-AG1'), q('Agence ' + cs.nom)]);
        payeurRows.push([q(payeurId), q(c.id), q(agenceId), q(cs.code + '-CP1'), q('Payeur ' + cs.nom), 'NULL']);
        c.caisses.push({...cs, agenceId, payeurId});
    }
    insert('caisse_assurance', ['id', 'center_id', 'code', 'nom'], caisseRows);
    insert('agence', ['id', 'center_id', 'caisse_id', 'code', 'nom'], agenceRows);
    insert('centre_payeur', ['id', 'center_id', 'agence_id', 'code', 'nom', 'adresse'], payeurRows);
}

function pickCaisse(c) {
    const r = rnd();
    let acc = 0;
    for (const cs of c.caisses) {
        acc += cs.part;
        if (r < acc) return cs;
    }
    return null; // sans caisse
}

// ───────────────────────────── Patients ─────────────────────────────
const PRENOMS_M = ['Mohamed', 'Ahmed', 'Karim', 'Yacine', 'Sofiane', 'Riad', 'Amine', 'Nabil', 'Farid', 'Samir'];
const PRENOMS_F = ['Amel', 'Nadia', 'Samira', 'Yasmine', 'Karima', 'Leila', 'Sarah', 'Fatima', 'Meriem', 'Houria'];
const NOMS = ['Belkacem', 'Meziane', 'Boudiaf', 'Cherif', 'Hamdi', 'Zerrouki', 'Bensalem', 'Ait Ali', 'Kaci', 'Rahmani',
    'Bouzid', 'Guessoum', 'Lakhdari', 'Mansouri', 'Ouahab', 'Saidi', 'Tazir', 'Yahiaoui', 'Ziani', 'Boukhari'];

function randomBirth(minAge, maxAge) {
    const age = rint(minAge, maxAge);
    const d = new Date(TODAY.getFullYear() - age, rint(0, 11), rint(1, 28));
    return d;
}

function ageBand() {
    // Répartition volontairement variée pour couvrir toutes les tranches du tableau de bord.
    const r = rnd();
    if (r < 0.03) return [1, 17];
    if (r < 0.15) return [18, 29];
    if (r < 0.40) return [30, 44];
    if (r < 0.72) return [45, 59];
    return [60, 85];
}

let patientSeq = 0;
for (const c of CENTRES) {
    c.patientList = [];
    const rows = [];
    for (let i = 0; i < c.patients; i++) {
        patientSeq++;
        const id = uuid();
        const sexe = chance(0.52) ? 'M' : 'F';
        const prenom = sexe === 'M' ? pick(PRENOMS_M) : pick(PRENOMS_F);
        const nom = pick(NOMS);
        const [minA, maxA] = ageBand();
        const naissance = randomBirth(minA, maxA);
        // Admission : la plupart avant la fenêtre de 12 mois, certains arrivés en cours de fenêtre (activité croissante).
        const admission = chance(0.75) ? addDays(MONTHS[0], -rint(30, 2000)) : addDays(MONTHS[rint(0, 10)], rint(0, 27));
        const sousKt = chance(0.18);
        const epo = chance(0.6);
        const fer = chance(0.32);
        const caisse = pickCaisse(c);
        const patient = {
            id, sexe, naissance, admission: startOfMonth(admission) < startOfMonth(MONTHS[0]) ? MONTHS[0] : admission,
            admissionReelle: admission, sousKt, epo, fer, caisse, code: `RD-P-${String(patientSeq).padStart(5, '0')}`,
        };
        c.patientList.push(patient);
        rows.push([
            q(id), q(c.id), q(patient.code), q(nom), q(prenom), q(sexe), q(fmt(naissance)),
            q(`ASS-${String(patientSeq).padStart(6, '0')}`), q(fmt(admission)), q('PERMANENT'), q('ACTIF'), q('ASSURE'),
            sousKt ? 'TRUE' : 'FALSE', epo ? 'TRUE' : 'FALSE', fer ? 'TRUE' : 'FALSE',
            caisse ? q(caisse.payeurId) : 'NULL', 'CURRENT_TIMESTAMP',
        ]);
    }
    insert('patients', ['id', 'center_id', 'code_patient', 'nom', 'prenom', 'sexe', 'date_naissance', 'numero_assurance',
        'date_admission', 'type_patient', 'etat_patient', 'qualite_assure', 'sous_kt', 'epo_enabled', 'fer_enabled',
        'centre_payeur_id', 'created_at'], rows);
}

// ───────────────────────────── Séances + factures + règlements ─────────────────────────────
const PRIX_SEANCE_HT = 2500; // DA, forfait moyen par séance
const TVA = 19.00;
let factureSeq = 0;

for (const c of CENTRES) {
    const seanceRows = [], factureRows = [], reglementRows = [];
    for (const p of c.patientList) {
        for (let mi = 0; mi < MONTHS.length; mi++) {
            const month = MONTHS[mi];
            if (month < startOfMonth(p.admissionReelle)) continue; // pas encore admis ce mois-là
            const isCurrentMonth = mi === MONTHS.length - 1;
            const nbSeances = rint(2, 4); // ~3 séances/semaine ramené à 2-4/mois pour un volume de jeu de test raisonnable
            const seanceDates = [];
            for (let s = 0; s < nbSeances; s++) {
                const day = rint(1, Math.min(28, endOfMonth(month).getDate()));
                const date = new Date(month.getFullYear(), month.getMonth(), day);
                if (date > TODAY) continue;
                seanceDates.push(date);
                const statut = isCurrentMonth ? pick(['VALIDEE', 'SIGNEE']) : 'FACTUREE';
                seanceRows.push([uuid(), q(p.id), q(c.id), q(fmt(date)), q(statut), 'CURRENT_TIMESTAMP'].map((v, idx) =>
                    idx === 0 ? q(v) : v));
            }
            if (seanceDates.length === 0) continue;
            // Une facture mensuelle par patient, au prorata des séances réalisées ce mois-là.
            factureSeq++;
            const ht = PRIX_SEANCE_HT * seanceDates.length;
            const tvaMontant = Math.round(ht * TVA) / 100;
            const ttc = ht + tvaMontant;
            const dateFacturation = endOfMonth(month) > TODAY ? TODAY : endOfMonth(month);
            const numero = `RD-F-${c.code}-${month.getFullYear()}${String(month.getMonth() + 1).padStart(2, '0')}-${factureSeq}`;
            const factureId = uuid();
            factureRows.push([
                q(factureId), q(c.id), q(p.id), q(numero), q(fmt(startOfMonth(month))), q(fmt(endOfMonth(month))),
                q(fmt(dateFacturation)), num(TVA), num(ht), num(tvaMontant), num(ttc),
                p.caisse ? q(p.caisse.agenceId) : 'NULL',
            ]);
            // Encaissement : 78 % payées intégralement, 14 % partiellement, 8 % impayées — pour varier le taux par centre.
            const r = rnd();
            let montantRegle = 0;
            if (r < 0.78) montantRegle = ttc;
            else if (r < 0.92) montantRegle = Math.round(ttc * (0.3 + rnd() * 0.5) * 100) / 100;
            if (montantRegle > 0 && !isCurrentMonth) {
                reglementRows.push([uuid(), q(factureId), q(c.id), num(montantRegle),
                    q(fmt(addDays(dateFacturation, rint(1, 25))))].map((v, idx) => idx === 0 ? q(v) : v));
            }
        }
    }
    insert('seances', ['id', 'patient_id', 'center_id', 'date_seance', 'statut', 'created_at'], seanceRows);
    insert('factures', ['id', 'center_id', 'patient_id', 'numero_facture', 'period_start', 'period_end',
        'date_facturation', 'tva_rate', 'total_ht', 'total_tva', 'total_ttc', 'agence_id_snapshot'], factureRows);
    insert('facture_reglements', ['id', 'facture_id', 'center_id', 'montant', 'date_reglement'], reglementRows);
}

// ───────────────────────────── Résultats de biologie (cibles KDIGO) ─────────────────────────────
// tier 0 = centre performant, 1 = moyen, 2 = en difficulté — pour des indicateurs KDIGO contrastés entre centres.
function biologyFor(tier) {
    const inTarget = tier === 0 ? 0.85 : tier === 1 ? 0.6 : 0.35;
    const ktV = chance(inTarget) ? (1.3 + rnd() * 0.5) : (0.7 + rnd() * 0.45);
    const hb = chance(inTarget) ? (10 + rnd() * 1.4) : chance(0.5) ? (7 + rnd() * 2.5) : (12 + rnd() * 2);
    const phosphore = chance(inTarget) ? (2.6 + rnd() * 1.8) : chance(0.5) ? (1.2 + rnd() * 1.2) : (5 + rnd() * 3);
    const pth = chance(inTarget) ? (150 + rnd() * 400) : chance(0.5) ? (40 + rnd() * 80) : (650 + rnd() * 600);
    const albumine = chance(inTarget) ? (4 + rnd() * 0.8) : (2.8 + rnd() * 1.1);
    return {ktV, hb, phosphore, pth, albumine};
}

for (const c of CENTRES) {
    const rows = [];
    for (const p of c.patientList) {
        for (let mi = 0; mi < MONTHS.length; mi++) {
            const month = MONTHS[mi];
            if (month < startOfMonth(p.admissionReelle)) continue;
            if (chance(0.1)) continue; // bilan non fait ce mois-là (réalisme)
            const day = rint(1, Math.min(28, endOfMonth(month).getDate()));
            const date = new Date(month.getFullYear(), month.getMonth(), day);
            if (date > TODAY) continue;
            const b = biologyFor(c.tier);
            rows.push([uuid(), q(p.id), q(c.id), q(fmt(date)), num(b.hb.toFixed(1)), num(b.ktV.toFixed(2)),
                num(b.phosphore.toFixed(2)), num(b.pth.toFixed(1)), num(b.albumine.toFixed(2)), 'CURRENT_TIMESTAMP',
                'CURRENT_TIMESTAMP'].map((v, idx) => idx === 0 ? q(v) : v));
        }
    }
    insert('resultats_analyses', ['id', 'patient_id', 'center_id', 'date_prelevement', 'hb_g_dl', 'kt_v_mensuel',
        'phosphore_mg_dl', 'pth_pg_ml', 'albumine_g_dl', 'created_at', 'updated_at'], rows);
}

// ───────────────────────────── Sérologies (VHB/VHC/VIH) ─────────────────────────────
for (const c of CENTRES) {
    const rows = [];
    for (const p of c.patientList) {
        const date = addDays(TODAY, -rint(10, 300));
        for (const [marqueur, tauxPositif] of [['AG_HBS', 0.04], ['AC_VHC', 0.06], ['VIH_AC', 0.01]]) {
            const resultat = chance(tauxPositif) ? 'POSITIF' : 'NEGATIF';
            rows.push([uuid(), q(p.id), q(c.id), q(marqueur), q(resultat), q(fmt(date)), 'CURRENT_TIMESTAMP',
                'CURRENT_TIMESTAMP'].map((v, idx) => idx === 0 ? q(v) : v));
        }
    }
    insert('serologies_patient', ['id', 'patient_id', 'center_id', 'marqueur', 'resultat', 'date_prelevement',
        'created_at', 'updated_at'], rows);
}

// ───────────────────────────── Anémie : administrations EPO / Fer ─────────────────────────────
for (const c of CENTRES) {
    const rows = [];
    for (const p of c.patientList) {
        if (!p.epo && !p.fer) continue;
        for (let mi = 0; mi < MONTHS.length; mi++) {
            const month = MONTHS[mi];
            if (month < startOfMonth(p.admissionReelle)) continue;
            const day = rint(1, Math.min(28, endOfMonth(month).getDate()));
            const date = new Date(month.getFullYear(), month.getMonth(), day);
            if (date > TODAY) continue;
            if (p.epo) {
                const administree = chance(0.92);
                rows.push([uuid(), q(p.id), q(c.id), q('EPO'), q(fmt(date)), administree ? 'TRUE' : 'FALSE',
                    'CURRENT_TIMESTAMP'].map((v, idx) => idx === 0 ? q(v) : v));
            }
            if (p.fer && chance(0.5)) {
                const administree = chance(0.9);
                rows.push([uuid(), q(p.id), q(c.id), q('FER_INJECTABLE'), q(fmt(date)), administree ? 'TRUE' : 'FALSE',
                    'CURRENT_TIMESTAMP'].map((v, idx) => idx === 0 ? q(v) : v));
            }
        }
    }
    insert('administrations_anemie', ['id', 'patient_id', 'center_id', 'type_traitement', 'date_administration',
        'administree', 'created_at'], rows);
}

// ───────────────────────────── Observance en retard (alertes ouvertes) ─────────────────────────────
for (const c of CENTRES) {
    const rows = [];
    const candidats = c.patientList.filter((p) => p.epo || p.fer);
    const nb = Math.min(candidats.length, c.tier === 2 ? 3 : c.tier === 1 ? 1 : 0);
    for (let i = 0; i < nb; i++) {
        const p = candidats[i];
        const debut = addDays(TODAY, -21);
        rows.push([uuid(), q(p.id), q(c.id), q(p.epo ? 'EPO' : 'FER_INJECTABLE'), q('RETARD_CONSTATE'),
            q(fmt(debut)), q(fmt(TODAY)), num(4), num(1), q('Retard constaté sur le traitement en cours'),
            'CURRENT_TIMESTAMP'].map((v, idx) => idx === 0 ? q(v) : v));
    }
    insert('alertes_observance', ['id', 'patient_id', 'center_id', 'type_traitement', 'type_alerte', 'periode_debut',
        'periode_fin', 'doses_attendues', 'doses_administrees', 'message', 'created_at'], rows);
}

// ───────────────────────────── Stock : articles + lots ─────────────────────────────
const ARTICLES = [
    {code: 'DIALYSEUR', libelle: 'Dialyseur haute performance', unite: 'unité', seuil: 40, prix: 850},
    {code: 'LIGNE-SANG', libelle: 'Ligne à sang (set complet)', unite: 'unité', seuil: 40, prix: 320},
    {code: 'AIGUILLE-FAV', libelle: 'Aiguille de fistule', unite: 'boîte', seuil: 20, prix: 180},
    {code: 'CONC-ACIDE', libelle: 'Concentré acide', unite: 'bidon', seuil: 15, prix: 210},
    {code: 'CONC-BICAR', libelle: 'Concentré bicarbonate', unite: 'sac', seuil: 15, prix: 190},
    {code: 'HEPARINE', libelle: 'Héparine sodique', unite: 'flacon', seuil: 25, prix: 95},
    {code: 'COMPRESSES', libelle: 'Compresses stériles', unite: 'boîte', seuil: 30, prix: 45},
    {code: 'EPO-INJ', libelle: 'EPO injectable', unite: 'flacon', seuil: 20, prix: 1200},
    {code: 'FER-INJ', libelle: 'Fer injectable', unite: 'flacon', seuil: 15, prix: 680},
];

for (const c of CENTRES) {
    const articleRows = [], lotRows = [];
    const articleIds = [];
    ARTICLES.forEach((a, idx) => {
        const articleId = uuid();
        articleIds.push(articleId);
        // Un ou deux articles sous le seuil par centre en difficulté (tier 2), un seul pour les autres, réalisme.
        const sousLeSeuil = (c.tier === 2 && idx < 2) || (c.tier === 1 && idx === 0);
        const stock = sousLeSeuil ? rint(0, a.seuil - 1) : rint(a.seuil + 10, a.seuil + 200);
        articleRows.push([q(articleId), q(c.id), q(a.code), q(a.libelle), q(a.unite), num(stock), num(a.seuil),
            num(a.prix), 'TRUE', 'TRUE', 'CURRENT_TIMESTAMP']);

        // Lots : un lot valide longue durée, et pour les deux premiers articles un lot périmé / proche péremption
        // (réalisme du contrôle de péremption), uniquement sur les centres tier 1/2 pour varier les alertes.
        lotRows.push([uuid(), q(c.id), q(articleId), q(a.code + '-L1'), q(fmt(addMonths(TODAY, 18))),
            num(rint(100, 400)), num(rint(50, 300)), num(a.prix)].map((v, i2) => i2 === 0 ? q(v) : v));
        if (idx === 0 && c.tier >= 1) {
            lotRows.push([uuid(), q(c.id), q(articleId), q(a.code + '-EXP'), q(fmt(addDays(TODAY, -rint(5, 60)))),
                num(50), num(rint(5, 30)), num(a.prix)].map((v, i2) => i2 === 0 ? q(v) : v));
        }
        if (idx === 1 && c.tier >= 1) {
            lotRows.push([uuid(), q(c.id), q(articleId), q(a.code + '-SOON'), q(fmt(addDays(TODAY, rint(10, 80)))),
                num(50), num(rint(5, 30)), num(a.prix)].map((v, i2) => i2 === 0 ? q(v) : v));
        }
    });
    insert('articles', ['id', 'center_id', 'code', 'libelle', 'unite', 'stock_quantity', 'seuil_alerte', 'pmp_courant',
        'gere_par_lot', 'active', 'created_at'], articleRows);
    insert('lots', ['id', 'center_id', 'article_id', 'numero_lot', 'date_peremption', 'quantite_initiale',
        'quantite_restante', 'pmp'], lotRows);
}

// ───────────────────────────── Bilan pré-greffe (léger, réalisme) ─────────────────────────────
for (const c of CENTRES) {
    const rows = [];
    const candidats = c.patientList.filter((p) => !p.sousKt).slice(0, c.tier === 0 ? 4 : 2);
    for (const p of candidats) {
        const statut = chance(0.5) ? 'BILAN_EN_COURS' : 'INSCRIT_LISTE_ATTENTE';
        const dateGreffe = statut === 'INSCRIT_LISTE_ATTENTE' && chance(0.45) ? addDays(TODAY, -rint(5, 300)) : null;
        rows.push([uuid(), q(p.id), q(c.id), q(dateGreffe ? 'GREFFE_REALISEE' : statut),
            q(fmt(addDays(TODAY, -rint(60, 500)))), statut === 'INSCRIT_LISTE_ATTENTE' ? q(fmt(addDays(TODAY, -rint(10, 200)))) : 'NULL',
            dateGreffe ? q(fmt(dateGreffe)) : 'NULL', 'CURRENT_TIMESTAMP', 'CURRENT_TIMESTAMP']
            .map((v, i2) => i2 === 0 ? q(v) : v));
    }
    insert('bilans_pre_greffe', ['id', 'patient_id', 'center_id', 'statut', 'date_debut_bilan',
        'date_inscription_liste_attente', 'date_greffe', 'created_at', 'updated_at'], rows);
}

// ───────────────────────────── Écriture ─────────────────────────────
const outPath = path.join(__dirname, 'renadial-seed.sql');
fs.writeFileSync(outPath, out.join('\n') + '\n', 'utf8');
console.log('Écrit :', outPath, `(${(fs.statSync(outPath).size / 1024).toFixed(0)} Ko)`);
console.log('Centres :', CENTRES.length, '— Patients :', CENTRES.reduce((s, c) => s + c.patients, 0));
