# D2 — Modèle de données (PostgreSQL)

Candidate : Tedjou Nguimzi Cyntia — Matricule 265. Référence : `docs/CAHIER_DES_CHARGES.md`, sections 6 et 7.

Les tables principales du domaine sont `sessions`, `presences`, `exercices` et `relectures`. Les tables `promotions`, `formateurs` et `etudiants` sont des données de référence pré-chargées (H4).

```mermaid
erDiagram
    promotions ||--o{ etudiants : "regroupe"
    promotions ||--o{ sessions : "concerne"
    formateurs ||--o{ sessions : "ouvre"
    sessions ||--o{ presences : "enregistre"
    etudiants ||--o{ presences : "marque"
    sessions ||--o{ exercices : "recoit"
    etudiants ||--o{ exercices : "depose"
    exercices ||--o| relectures : "fait l'objet de"
    etudiants ||--o{ relectures : "relit"

    promotions {
        bigint id PK
        varchar(100) libelle UK
    }
    formateurs {
        bigint id PK
        varchar(100) nom
        varchar(100) prenom
        varchar(255) email UK
    }
    etudiants {
        bigint id PK
        varchar(20) matricule UK
        varchar(100) nom
        varchar(100) prenom
        varchar(255) email UK
        bigint promotion_id FK
    }
    sessions {
        bigint id PK
        bigint promotion_id FK
        bigint formateur_id FK
        varchar(150) titre
        char(6) code UK "A-Z et 0-9 (RG10)"
        timestamptz ouverture_at
        timestamptz expiration_at "ouverture_at + 15 min (RG1)"
        varchar(10) statut "OUVERTE ou CLOTUREE"
        timestamptz cloture_at "NULL tant que la session est ouverte"
    }
    presences {
        bigint id PK
        bigint session_id FK "unique avec etudiant_id (RG3)"
        bigint etudiant_id FK
        varchar(10) source "CODE (H1)"
        timestamptz marquee_at
    }
    exercices {
        bigint id PK
        bigint session_id FK "unique avec etudiant_id (RG4)"
        bigint etudiant_id FK "auteur"
        varchar(2048) lien "URL http ou https (RG5)"
        varchar(20) statut "cycle de vie D4"
        timestamptz depose_at
    }
    relectures {
        bigint id PK
        bigint exercice_id FK, UK "une relecture par exercice (RG8)"
        bigint relecteur_id FK "etudiant present, different de l'auteur (RG2, RG7)"
        smallint note "0 a 20, NULL tant que non rendue (RG6)"
        text commentaire
        varchar(12) statut "EN_ATTENTE ou RENDUE"
        timestamptz attribuee_at
        timestamptz rendue_at
        timestamptz modifiee_at "derniere modification avant cloture (Q10)"
    }
```

## Contraintes d'intégrité

| Table | Contrainte | Type | Règle |
|---|---|---|---|
| `sessions` | `code` unique | `UNIQUE` | RG10 |
| `sessions` | `expiration_at = ouverture_at + 15 min` | `CHECK` | RG1 |
| `sessions` | `statut` dans (`OUVERTE`, `CLOTUREE`) | `CHECK` | RG9 |
| `presences` | couple (`session_id`, `etudiant_id`) unique | `UNIQUE` | RG3 : `409 DEJA_PRESENT`, y compris en cas d'envois simultanés |
| `exercices` | couple (`session_id`, `etudiant_id`) unique | `UNIQUE` | RG4 : `409 EXERCICE_DEJA_DEPOSE` |
| `exercices` | `statut` dans (`DEPOSE`, `EN_RELECTURE`, `RELU`, `DEFINITIF`, `SANS_RELECTURE`) | `CHECK` | D4 |
| `relectures` | `exercice_id` unique | `UNIQUE` | RG8 : une seule relecture par exercice |
| `relectures` | `note` entre 0 et 20 ou `NULL` | `CHECK` | RG6 |
| `relectures` | `statut = 'RENDUE'` si et seulement si `note` et `rendue_at` sont renseignés | `CHECK` | EF5 |
| `relectures` | relecteur différent de l'auteur et présent à la session | contrôle applicatif (règle portant sur plusieurs tables) | RG2, RG7 |

## Index

- `presences (session_id)` et `exercices (session_id)` : attribution des relectures et contrôles par session.
- `relectures (relecteur_id, statut)` : calcul de `relecturesEnAttente` (RG11).
- `etudiants (promotion_id)` et `sessions (promotion_id)` : tableau récapitulatif.

## Choix de conception

- **Clôture et définitivité :** une note est définitive lorsque `sessions.statut = 'CLOTUREE'` (RG9). Cet état n'est pas dupliqué dans `relectures` : il est dérivé de la session.
- **Dates :** `timestamptz`, en UTC (ENF6). L'expiration du code se compare à l'horloge du serveur.
- **Nom de la table `sessions` :** au pluriel, comme toutes les tables, ce qui évite toute confusion avec le mot-clé SQL `SESSION`.
