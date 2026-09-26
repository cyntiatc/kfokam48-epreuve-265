# Cahier des charges — Présences et relecture par les pairs (KFOKAM48)

| Élément | Valeur |
|---|---|
| Candidate | Tedjou Nguimzi Cyntia |
| Matricule | 265 |
| Épreuve | KFOKAM48 |
| Étape | Étape 1 — Analyse, spécification et conception |
| Version | 1.2 — 25/09/2026 (ajout de l'hypothèse H9) |
| Frontend choisi | **React** |

Documents associés : diagrammes `docs/diagrammes/D1_use_cases.md` à `D4_etats.md`, contrat d'API `api/contrat.yaml`.

---

## 1. Contexte

Pendant un cours, le formateur doit à la fois relever les présences et faire pratiquer les étudiants sur des exercices. L'application KFOKAM48 réunit ces deux besoins :

- le formateur **ouvre une session de cours** et obtient un **code de présence** à durée limitée ;
- chaque étudiant **saisit ce code** pour marquer sa présence ;
- chaque étudiant **dépose le lien** de son exercice pour la session ;
- le système **attribue au hasard** à un étudiant présent l'exercice d'un pair, qu'il note (0 à 20) et commente ;
- le formateur suit l'ensemble sur un **tableau récapitulatif** par promotion.

Objectifs : fiabiliser le relevé de présence (code valable 15 minutes), développer l'évaluation par les pairs et donner au formateur une vue de synthèse immédiate.

## 2. Acteurs

| Acteur | Type | Description | Responsabilités |
|---|---|---|---|
| Formateur | Principal | Enseignant responsable d'une promotion | Ouvrir une session, communiquer le code, clôturer la session, consulter le tableau récapitulatif |
| Étudiant | Principal | Membre d'une promotion | Marquer sa présence avec le code, déposer le lien de son exercice |
| Relecteur | Principal (rôle) | Étudiant **présent** à la session à qui le système a attribué l'exercice d'un pair | Noter l'exercice (entier de 0 à 20) et le commenter, corriger sa note avant la clôture |
| Système | Secondaire | Horloge du serveur et moteur d'attribution | Faire expirer le code (RG1), tirer au hasard les relecteurs (RG8) |

Le Relecteur est une spécialisation de l'Étudiant : un même étudiant peut déposer un exercice et relire celui d'un pair.

## 3. Périmètre

### 3.1 Inclus (version 1)

- Ouverture d'une session de cours et génération d'un code de présence.
- Marquage de la présence par code, avec gestion des erreurs (code inconnu, déjà présent, code expiré).
- Dépôt d'un lien d'exercice par session.
- Attribution aléatoire des relectures entre pairs, sans auto-relecture.
- Saisie et modification (avant clôture) d'une note entière de 0 à 20 accompagnée d'un commentaire.
- Clôture d'une session par le formateur (voir H3).
- Tableau récapitulatif par promotion.
- Interface web React pour le formateur, l'étudiant et le relecteur.

### 3.2 Exclus (version 1)

- Authentification et gestion des comptes : l'étudiant est identifié par l'`etudiantId` transmis dans les requêtes (H4).
- Administration des promotions et des étudiants : ces données de référence sont pré-chargées en base. Les formateurs ne sont pas enregistrés en version 1 (H4).
- Notifications (e-mail, push), application mobile native, exports (PDF, CSV).
- Relectures multiples d'un même exercice, contestation de note, réattribution manuelle.

## 4. Exigences fonctionnelles

Chaque exigence est vérifiée par des critères d'acceptation « Quand… Alors… ». Toute erreur est renvoyée au format `{ "code": "...", "message": "..." }` (RG12).

### EF1 — Ouvrir une session de cours

- **Acteur :** Formateur — **Route :** `POST /api/sessions` — **Règles :** RG1, RG10
- **Critères d'acceptation :**
  - **Quand** le formateur ouvre une session pour une promotion existante avec un titre, **Alors** le système crée une session au statut `OUVERTE`, génère un code unique, fixe `expirationAt = ouvertureAt + 15 min` et répond `201` avec `{id, code, ouvertureAt, expirationAt}`.
  - **Quand** `titre` ou `promotionId` est absent ou invalide (titre vide, promotion inconnue), **Alors** le système répond `400` (`REQUETE_INVALIDE`) et aucune session n'est créée.

### EF2 — Marquer sa présence

- **Acteur :** Étudiant — **Route :** `POST /api/presences` — **Règles :** RG1, RG3
- **Critères d'acceptation :**
  - **Quand** l'étudiant saisit un code valide, non expiré, et n'est pas encore présent, **Alors** la présence est enregistrée et le système répond `201` avec `{id, sessionId, etudiantId, source}` (`source = "CODE"`).
  - **Quand** le code ne correspond à aucune session, **Alors** le système répond `400 CODE_INCONNU`.
  - **Quand** l'étudiant est inconnu ou n'appartient pas à la promotion de la session, **Alors** le système répond `400 REQUETE_INVALIDE` et aucune présence n'est créée (H2, H9).
  - **Quand** l'étudiant est déjà présent à cette session (y compris en cas de double envoi simultané), **Alors** le système répond `409 DEJA_PRESENT` et aucune seconde présence n'est créée.
  - **Quand** le code est saisi après `expirationAt` ou après la clôture de la session, **Alors** le système répond `410 CODE_EXPIRE`.

### EF3 — Déposer le lien de son exercice

- **Acteur :** Étudiant — **Route :** `POST /api/exercices` — **Règles :** RG4, RG5
- **Critères d'acceptation :**
  - **Quand** l'étudiant dépose pour la première fois un lien `http(s)` valide pour une session, **Alors** l'exercice est enregistré et le système répond `201` avec `{id, statut}` : `EN_RELECTURE` si un relecteur a pu être attribué immédiatement (EF4), sinon `DEPOSE`.
  - **Quand** le lien n'est pas une URL absolue `http` ou `https`, **Alors** le système répond `400 LIEN_INVALIDE`.
  - **Quand** l'étudiant a déjà déposé un exercice pour cette session, **Alors** le système répond `409 EXERCICE_DEJA_DEPOSE` et le premier dépôt reste inchangé.

### EF4 — Attribuer les relectures au hasard

- **Acteur :** Système — **Route :** aucune (traitement interne déclenché par EF2 et EF3) — **Règles :** RG2, RG7, RG8
- **Critères d'acceptation :**
  - **Quand** un exercice est déposé et qu'il existe au moins un étudiant présent à la session autre que l'auteur, **Alors** le système crée une relecture `EN_ATTENTE` dont le relecteur est tiré au hasard parmi les présents éligibles ayant le moins de relectures dans la session, et l'exercice passe à `EN_RELECTURE`.
  - **Quand** aucun relecteur n'est éligible au moment du dépôt, **Alors** l'exercice reste `DEPOSE` et il est attribué automatiquement dès qu'une nouvelle présence éligible est enregistrée à cette session.
  - **Quand** l'auteur est le seul étudiant présent, **Alors** il ne se voit jamais attribuer son propre exercice (RG2).

### EF5 — Rendre une relecture

- **Acteur :** Relecteur — **Route :** `POST /api/relectures/{id}` — **Règles :** RG2, RG6, RG9
- **Critères d'acceptation :**
  - **Quand** une note entière comprise entre 0 et 20 et un commentaire non vide sont envoyés pour une relecture attribuée, **Alors** le système répond `200`, la relecture passe à `RENDUE` et l'exercice passe à `RELU` (session ouverte) ou `DEFINITIF` (session clôturée, H8).
  - **Quand** la note est absente, non entière ou hors de l'intervalle [0 ; 20], **Alors** le système répond `400 NOTE_INVALIDE` et rien n'est enregistré.
  - **Quand** le relecteur attribué à cette relecture est l'auteur de l'exercice (contrôle de sûreté, RG2), **Alors** le système répond `403 AUTO_RELECTURE` et rien n'est enregistré.

### EF6 — Modifier sa note avant la clôture (décision Q10)

- **Acteur :** Relecteur — **Route :** `POST /api/relectures/{id}` — **Règles :** RG9
- **Critères d'acceptation :**
  - **Quand** la relecture est déjà rendue et que la session est `OUVERTE`, **Alors** le système répond `200`, la note et le commentaire sont remplacés et la date de modification est mise à jour.
  - **Quand** la relecture est déjà rendue et que la session est `CLOTUREE`, **Alors** le système répond `409 RELECTURE_DEJA_RENDUE` et la note reste inchangée.

### EF7 — Consulter le tableau récapitulatif

- **Acteur :** Formateur — **Route :** `GET /api/tableau?promotionId=` — **Règles :** RG11
- **Critères d'acceptation :**
  - **Quand** le formateur demande le tableau d'une promotion existante, **Alors** le système répond `200` avec une ligne par étudiant de la promotion, y compris ceux sans activité : `{etudiantId, nom, presences, exercicesDeposes, moyenne, relecturesEnAttente}`.
  - **Quand** la promotion n'existe pas, **Alors** le système répond `404 PROMOTION_INCONNUE`.
  - **Quand** `promotionId` est absent ou n'est pas un entier, **Alors** le système répond `400 REQUETE_INVALIDE` (H2).

### EF8 — Clôturer une session

- **Acteur :** Formateur — **Route :** `POST /api/sessions/{id}/cloture` (extension H3, ticket #12) — **Règles :** RG1, RG9
- **Critères d'acceptation :**
  - **Quand** le formateur clôture une session `OUVERTE`, **Alors** la session passe à `CLOTUREE`, les notes rendues deviennent définitives (exercices `DEFINITIF`) et les exercices sans relecteur passent à `SANS_RELECTURE`. Le système répond `200` avec le bilan de la clôture.
  - **Quand** un étudiant saisit ensuite le code de cette session, **Alors** le système répond `410 CODE_EXPIRE`.
  - **Quand** la session est déjà clôturée, **Alors** le système répond `409 SESSION_DEJA_CLOTUREE`. La clôture est irréversible.
  - **Quand** la session n'existe pas, **Alors** le système répond `400 REQUETE_INVALIDE` (H2).

## 5. Exigences non fonctionnelles

| ID | Catégorie | Exigence | Vérification |
|---|---|---|---|
| ENF1 | Conformité | Les 5 routes respectent strictement `api/contrat.yaml` (chemins, statuts HTTP, schémas). | Tests d'intégration de chaque route |
| ENF2 | Robustesse | Toute erreur, y compris inattendue (500), est renvoyée au format `{code, message}` sans trace technique. | Tests des cas d'erreur |
| ENF3 | Intégrité | Les règles d'unicité (RG3, RG4, RG8) sont garanties par des contraintes en base, même en cas de requêtes simultanées. Chaque écriture est transactionnelle. | Test de double envoi concurrent |
| ENF4 | Performance | Temps de réponse inférieur à 500 ms (95e percentile) en local, pour une promotion de 100 étudiants et 50 sessions. | Mesure sur jeu de données de test |
| ENF5 | Sécurité | Validation côté serveur de toutes les entrées, requêtes paramétrées, CORS limité à l'origine du frontend, liens d'exercice ouverts dans un nouvel onglet isolé. | Revue de code, tests |
| ENF6 | Temps | Horloge du serveur faisant foi pour l'expiration. Dates en UTC au format ISO-8601, stockées en `TIMESTAMPTZ`. | Tests unitaires avec horloge simulée |
| ENF7 | Utilisabilité | Interface React en français, utilisable sur téléphone (saisie du code), messages d'erreur compréhensibles issus du champ `message`. | Recette manuelle |
| ENF8 | Maintenabilité | Architecture en couches (contrôleur, service, dépôt). Chaque règle de gestion est couverte par au moins un test automatisé. | Rapport de tests |

## 6. Règles de gestion

| ID | Intitulé | Description | Erreur associée | EF |
|---|---|---|---|---|
| RG1 | Validité du code | Le code de présence est valable 15 minutes à compter de l'ouverture : `expirationAt = ouvertureAt + 15 min`. Au-delà, ou après la clôture, il est refusé. | `410 CODE_EXPIRE` | EF1, EF2, EF8 |
| RG2 | Pas d'auto-relecture | Un étudiant ne peut jamais relire son propre exercice : il est exclu du tirage au sort, et la soumission vérifie à nouveau que le relecteur attribué n'est pas l'auteur. | `403 AUTO_RELECTURE` | EF4, EF5 |
| RG3 | Présence unique | Une seule présence par couple (session, étudiant). | `409 DEJA_PRESENT` | EF2 |
| RG4 | Dépôt unique | Un seul exercice par couple (session, étudiant). Le lien déposé n'est pas modifiable. | `409 EXERCICE_DEJA_DEPOSE` | EF3 |
| RG5 | Format du lien | Le lien est une URL absolue `http` ou `https` de 2048 caractères au plus. | `400 LIEN_INVALIDE` | EF3 |
| RG6 | Format de la note | La note est un entier compris entre 0 et 20 inclus. Le commentaire est obligatoire (1 000 caractères au plus). | `400 NOTE_INVALIDE` | EF5 |
| RG7 | Éligibilité du relecteur | Seul un étudiant présent à la session peut être relecteur d'un exercice de cette session. | — | EF4 |
| RG8 | Attribution aléatoire équilibrée | Chaque exercice reçoit exactement une relecture. Le relecteur est tiré au hasard parmi les présents autres que l'auteur ayant le moins de relectures attribuées dans la session (voir 7.2). | — | EF4 |
| RG9 | Modification de la note | Une note rendue est modifiable tant que la session est ouverte. Après la clôture, elle est définitive (décision Q10, voir 7.1). | `409 RELECTURE_DEJA_RENDUE` | EF5, EF6, EF8 |
| RG10 | Code unique | Le code de présence comporte 6 caractères parmi `A-Z` et `0-9` et il est unique. La saisie est insensible à la casse. | — | EF1 |
| RG11 | Calculs du tableau | `presences` : nombre de sessions de la promotion où l'étudiant est présent. `exercicesDeposes` : nombre d'exercices déposés. `moyenne` : moyenne des notes reçues, arrondie à 2 décimales, `null` si aucune. `relecturesEnAttente` : nombre de relectures attribuées à l'étudiant et non rendues. | — | EF7 |
| RG12 | Format d'erreur unique | Toute erreur est renvoyée sous la forme `{ "code": "...", "message": "..." }`. | — | Toutes |

## 7. Contradictions, trous du sujet et hypothèses

### 7.1 Contradiction Q10 / Q15 : modification de la note

| Réf. | Énoncé du sujet |
|---|---|
| Q10 | La note peut être modifiée tant que la session n'a pas été clôturée par le formateur. |
| Q15 | La note est définitive une fois envoyée. |

Les deux réponses sont incompatibles : selon Q15, un second envoi est toujours refusé ; selon Q10, il est accepté jusqu'à la clôture.

**Décision : Q10 est retenue.**

**Justification :**

1. Elle permet de corriger les erreurs de saisie réelles (faute de frappe sur la note, commentaire incomplet) sans intervention du formateur.
2. La modification reste bornée : après la clôture de la session par le formateur, plus aucune modification n'est possible. L'intention de Q15 (une note stable et opposable) est donc préservée à partir de la clôture.
3. Elle est compatible avec le contrat imposé : l'erreur `409 RELECTURE_DEJA_RENDUE` est renvoyée lorsqu'une relecture déjà rendue est soumise à nouveau **après la clôture**.

**Conséquences :** RG9, EF6, EF8, transition `RELU → RELU` du diagramme D4, et date de modification (`modifiee_at`) conservée dans le modèle D2.

### 7.2 Trou du sujet : attribution des relectures (nombre impair de présents, étudiant sans exercice)

**Constat :** le sujet ne précise pas comment attribuer les relectures lorsque le nombre d'étudiants présents est impair, ni lorsqu'un étudiant présent n'a pas déposé d'exercice.

**Décision (RG8) :** l'attribution se fait **par exercice** et non par paires d'étudiants. Chaque exercice déposé reçoit exactement un relecteur, tiré au hasard parmi les étudiants présents autres que l'auteur. Le tirage se limite aux candidats ayant le moins de relectures attribuées dans la session, afin d'équilibrer la charge.

| Situation | Traitement retenu |
|---|---|
| Nombre impair de présents | Sans incidence : aucun appariement par paires n'est nécessaire. Selon l'ordre des dépôts, un étudiant peut relire un exercice de plus que les autres. |
| Étudiant présent sans exercice déposé | Il reste relecteur éligible et renforce le vivier. Il ne reçoit aucune note : `exercicesDeposes = 0`, `moyenne = null`. |
| Étudiant ayant déposé sans être présent | Son exercice est relu par un étudiant présent. Lui-même ne peut pas être relecteur (RG7). |
| Auteur seul présent, ou aucun présent | L'exercice reste `DEPOSE` et il est attribué dès qu'une présence éligible arrive. S'il n'a toujours pas de relecteur à la clôture, il passe à `SANS_RELECTURE` et n'entre pas dans la moyenne. |
| Relecteur qui ne rend pas sa relecture | La relecture reste comptée dans `relecturesEnAttente`. Aucune réattribution automatique en version 1. |

### 7.3 Hypothèses complémentaires

| ID | Sujet | Hypothèse retenue |
|---|---|---|
| H1 | Champ `source` d'une présence | Le sujet ne le définit pas. Il indique l'origine de la présence. Seule valeur en version 1 : `CODE` (présence saisie par l'étudiant avec le code). |
| H2 | Erreurs de validation génériques | Le contrat ne fixe pas de code d'erreur pour les champs manquants, les formats incorrects ou les identifiants inconnus. Ces cas renvoient `400` avec le code `REQUETE_INVALIDE`. Le seul `404` utilisé est `PROMOTION_INCONNUE`. |
| H3 | Routes absentes du contrat imposé | Deux besoins n'ont pas de route : la clôture d'une session (EF8, nécessaire à la décision Q10) et la consultation par un relecteur des relectures qui lui sont attribuées (il lui faut l'`id` de la relecture et le lien de l'exercice). Pour respecter le contrat imposé, ces routes n'y figuraient pas au départ. **Décision (ticket #10) :** la consultation `GET /api/relectures?relecteurId=` est ajoutée à `api/contrat.yaml`, marquée comme extension. Sans elle, l'écran Relecteur est inutilisable. **Décision (ticket #12) :** la clôture `POST /api/sessions/{id}/cloture` est ajoutée de la même façon, car la décision Q10 n'a d'effet que si le formateur peut clôturer. |
| H4 | Identification des utilisateurs | Le contrat ne prévoit pas d'authentification. L'étudiant est identifié par l'`etudiantId` transmis dans le corps de `POST /api/presences` et `POST /api/exercices`. `POST /api/sessions` ne reçoit que `titre` et `promotionId` : le formateur n'est pas enregistré. `POST /api/relectures/{id}` ne reçoit que `note` et `commentaire` : l'émetteur n'est pas transmis. Le `403 AUTO_RELECTURE` vérifie donc que le relecteur attribué n'est pas l'auteur de l'exercice. Conséquence acceptée en version 1 : toute personne qui connaît l'`id` d'une relecture peut la soumettre. Identifier réellement l'émetteur demandera une authentification. Promotions et étudiants sont pré-chargés en base. |
| H5 | Dépôt et présence | Le contrat ne prévoit aucune erreur liée à la présence sur `POST /api/exercices`. Un étudiant de la promotion peut donc déposer un exercice même s'il n'est pas présent. |
| H6 | Ordre des contrôles de présence | Les contrôles s'enchaînent ainsi : `CODE_INCONNU`, puis le contrôle de l'étudiant (`REQUETE_INVALIDE`, H9), puis `DEJA_PRESENT`, puis `CODE_EXPIRE`. Un étudiant déjà présent qui ressaisit un code expiré est donc informé qu'il est déjà présent. |
| H7 | Calcul de la moyenne | La moyenne inclut les notes rendues encore modifiables (session ouverte). Le tableau reflète donc l'état courant. |
| H8 | Relecture rendue après la clôture | Une relecture attribuée mais non rendue au moment de la clôture peut encore être rendue **une seule fois**. Elle est immédiatement définitive : il s'agit d'une première saisie, pas d'une modification. |
| H9 | Promotion de l'étudiant qui émarge | Le sujet ne dit pas si un étudiant peut émarger à la session d'une autre promotion. Décision : non. L'étudiant doit appartenir à la promotion de la session, sinon `POST /api/presences` renvoie `400 REQUETE_INVALIDE`. Sinon, sa présence n'apparaîtrait dans le tableau récapitulatif d'aucune promotion (RG11), et il deviendrait relecteur éligible (RG7) pour les exercices d'une promotion qui n'est pas la sienne. |

## 8. Contraintes techniques

| Domaine | Choix imposé ou retenu |
|---|---|
| Backend | Java 17 ou supérieur, Spring Boot 3.x, Maven (Spring Web, Spring Data JPA, Bean Validation) |
| Base de données | PostgreSQL 15 ou supérieur, schéma conforme au diagramme D2, migrations versionnées |
| Frontend | **React**, application monopage consommant l'API REST en JSON |
| Contrat d'API | OpenAPI 3.0 : `api/contrat.yaml` fait foi (approche « contrat d'abord ») |
| Versionnement | Git, dépôt public GitHub `kfokam48-epreuve-265`, branche `main` |
| Arborescence | `/docs` (et `/docs/diagrammes`), `/api`, `/backend`, `/frontend` |
| Conventions | Commits de jalon préfixés `[JALON]`. Aucun code applicatif avant l'étape d'implémentation. |
| Tests | JUnit 5, Mockito et MockMvc côté backend |

## 9. Livrables

| Livrable | Emplacement | Étape |
|---|---|---|
| Cahier des charges | `docs/CAHIER_DES_CHARGES.md` | Étape 1 — `[JALON] analyse` |
| D1 — Cas d'utilisation | `docs/diagrammes/D1_use_cases.md` | Étape 1 |
| D2 — Modèle de données | `docs/diagrammes/D2_datamodel.md` | Étape 1 |
| D3 — Séquence « Marquer sa présence » | `docs/diagrammes/D3_sequence.md` | Étape 1 |
| D4 — États d'un exercice | `docs/diagrammes/D4_etats.md` | Étape 1 |
| Contrat d'API OpenAPI 3.0 | `api/contrat.yaml` | Étape 1 |
| Backlog priorisé | Issues GitHub du dépôt | Étape 1 |
| Journal de bord | `docs/JOURNAL.md` | Toutes les étapes |
| API Spring Boot et tests | `/backend` | Étapes d'implémentation |
| Application React | `/frontend` | Étapes d'implémentation |

## 10. Démarche

1. **Étape 0 — Initialisation :** dépôt Git, `.gitignore`, arborescence imposée, commit `Initial commit`.
2. **Étape 1 — Analyse (ce document) :** exigences, règles de gestion, arbitrage des contradictions, diagrammes D1 à D4, contrat OpenAPI, backlog. Jalon `[JALON] analyse`.
3. **Implémentation du backend, guidée par le contrat :** modèle de données, puis routes dans l'ordre du parcours (sessions, présences, exercices, relectures, tableau). Chaque règle de gestion est d'abord couverte par un test.
4. **Implémentation du frontend React :** écrans Formateur, Étudiant et Relecteur, branchés sur l'API.
5. **Recette :** vérification de chaque critère « Quand… Alors… » de la section 4.

**Organisation :**

- Le backlog est géré en issues GitHub priorisées selon la méthode MoSCoW (Must / Should / Could). Chaque issue référence les EF et RG qu'elle couvre.
- Chaque commit référence son issue (`#n`).
- Une issue est terminée lorsque le code est écrit, les tests passent, la conformité au contrat est vérifiée et le journal est mis à jour.

### Backlog initial

| # | Issue | Priorité | EF | RG / H |
|---|---|---|---|---|
| 1 | `POST /api/sessions` : ouvrir une session et générer le code | Must | EF1 | RG1, RG10 |
| 2 | `POST /api/presences` : marquer sa présence | Must | EF2 | RG1, RG3, H6 |
| 3 | `POST /api/exercices` : déposer un exercice | Must | EF3 | RG4, RG5, H5 |
| 4 | Attribution aléatoire équilibrée des relectures | Must | EF4 | RG2, RG7, RG8 |
| 5 | `POST /api/relectures/{id}` : rendre ou modifier une relecture | Must | EF5, EF6 | RG2, RG6, RG9, H8 |
| 6 | `GET /api/tableau` : tableau récapitulatif | Must | EF7 | RG11, H7 |
| 7 | Écran Formateur : ouvrir une session et afficher le code | Must | EF1 | RG1 |
| 8 | Écran Étudiant : saisir le code de présence | Must | EF2 | RG1, RG3 |
| 9 | Écran Étudiant : déposer le lien de l'exercice | Must | EF3 | RG4, RG5 |
| 10 | Écran Relecteur : noter et commenter un exercice | Must | EF5, EF6 | RG6, RG9 |
| 11 | Écran Formateur : tableau récapitulatif | Must | EF7 | RG11 |
| 12 | Clôture d'une session (route complémentaire à valider) | Should | EF8 | RG9, H3 |
| 13 | Liste des relectures attribuées à un relecteur (route complémentaire à valider) | Should | EF5 | H3 |
| 14 | Tests d'intégration des 5 routes au regard du contrat | Should | Toutes | ENF1, ENF2 |
| 15 | Compte à rebours de validité du code à l'écran | Could | EF1, EF2 | RG1 |
| 16 | Documentation interactive de l'API servie par le backend | Could | — | ENF1 |
