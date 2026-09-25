# D3 — Diagramme de séquence : « Marquer sa présence »

Candidate : Tedjou Nguimzi Cyntia — Matricule 265. Référence : EF2, RG1, RG3, H6, H9 (`docs/CAHIER_DES_CHARGES.md`) et `POST /api/presences` (`api/contrat.yaml`).

Le diagramme couvre le cas nominal (`201`) et les deux erreurs métier `409 DEJA_PRESENT` et `410 CODE_EXPIRE`.

```mermaid
sequenceDiagram
    autonumber
    actor E as Étudiant
    participant F as Frontend React
    participant C as API REST (contrôleur)
    participant S as Service Présence
    participant DB as PostgreSQL

    E->>F: Saisit le code de présence (ex. K7P2QX)
    F->>C: POST /api/presences {code, etudiantId}
    C->>S: marquerPresence(code, etudiantId)
    S->>DB: Rechercher la session par code
    DB-->>S: Session trouvée (expirationAt, statut)
    Note over S,DB: Si aucune session ne correspond au code : 400 CODE_INCONNU (hors diagramme)
    S->>DB: Rechercher l'étudiant (etudiantId)
    DB-->>S: Étudiant trouvé (promotion)
    Note over S,DB: Étudiant inconnu ou d'une autre promotion : 400 REQUETE_INVALIDE (H9, hors diagramme)
    S->>DB: Rechercher une présence (sessionId, etudiantId)
    DB-->>S: Résultat de la recherche

    alt Cas nominal : aucune présence et maintenant ≤ expirationAt et session OUVERTE
        S->>DB: INSERT presence (source = CODE, marquee_at = maintenant)
        DB-->>S: Présence créée (id)
        S-->>C: Présence
        C-->>F: 201 Created {id, sessionId, etudiantId, source}
        F-->>E: « Présence enregistrée »
        S--)S: Attribuer les exercices en attente de relecteur (EF4)
    else Erreur 409 : une présence existe déjà (RG3)
        S-->>C: Refus DEJA_PRESENT
        C-->>F: 409 Conflict {code: DEJA_PRESENT, message}
        F-->>E: « Votre présence est déjà enregistrée »
    else Erreur 410 : maintenant > expirationAt ou session clôturée (RG1)
        S-->>C: Refus CODE_EXPIRE
        C-->>F: 410 Gone {code: CODE_EXPIRE, message}
        F-->>E: « Ce code a expiré, adressez-vous au formateur »
    end
```

## Notes

- **Ordre des contrôles (H6) :** `CODE_INCONNU`, puis le contrôle de l'étudiant (`REQUETE_INVALIDE` s'il est inconnu ou d'une autre promotion, H9), puis `DEJA_PRESENT`, puis `CODE_EXPIRE`. Un étudiant déjà présent qui ressaisit un code expiré reçoit `409` : c'est l'information la plus utile pour lui.
- **Envois simultanés :** si deux requêtes identiques passent le contrôle au même instant, la contrainte `UNIQUE (session_id, etudiant_id)` fait échouer la seconde insertion. Cette violation est traduite en `409 DEJA_PRESENT` (ENF3).
- **Horloge :** « maintenant » est l'heure du serveur en UTC. L'heure du navigateur n'est jamais utilisée (ENF6).
- **Format d'erreur :** toutes les réponses d'erreur suivent le format unique `{code, message}` (RG12). Le frontend affiche le champ `message`.
