# Journal de bord — 265

> Une entrée **par étape**, écrite **au moment où tu la termines**, pas à la fin de la journée.
> Trois lignes suffisent. Un journal rédigé d'un bloc juste avant de soumettre se repère
> immédiatement dans l'historique Git et ne compte pas.

Chaque entrée répond aux trois mêmes questions :

- **Fait** — ce que tu viens de terminer
- **Bloqué** — ce qui t'a coûté du temps, et combien
- **IA** — ce que tu lui as demandé, et **comment tu as vérifié sa réponse**

---

## Étape 1 — Analyse et conception

**Fait :** cahier des charges (8 exigences fonctionnelles, 8 non fonctionnelles, 12 règles de gestion, 8 hypothèses), les quatre diagrammes Mermaid D1 à D4, contrat d'API OpenAPI 3.0.3 (5 routes), backlog de 16 issues priorisées en MoSCoW (section 10 du cahier des charges, pas encore créées sur GitHub), commit `[JALON] analyse` poussé. Après ce commit : contrat vérifié et réaligné sur l'énoncé, cahier des charges, D2 et D4 mis en cohérence, 4 issues techniques retirées du backlog.

**Bloqué :** [durée à compléter] sur la contradiction entre Q10 (modification de la note autorisée avant la clôture) et Q15 (note définitive une fois envoyée) de CLIENT.md. Tranchée en faveur de Q10 : elle reflète le cas d'usage du formateur, qui peut corriger une erreur de saisie tant que la session n'est pas clôturée, et la note devient définitive à la clôture, ce qui préserve l'intention de Q15. Noté en section 7.1.

**IA :** lui ai demandé le cahier des charges, les diagrammes, le contrat et le backlog. Vérifié : contrat validé avec Redocly puis contrôlé règle par règle par un script, diagrammes passés au parseur Mermaid. En relisant le contrat face à l'énoncé, j'ai trouvé deux champs en trop (formateurId, relecteurId) et des réponses d'erreur qui n'utilisaient pas le composant commun Erreur : corrigé, puis cahier des charges, D2 et D4 réalignés.

---

## Étape 2 — Première version

**Fait :** (en cours : tickets #1 à #10, #12 et #14 traités) environnement mis en place : PostgreSQL 17 sous Docker (port 5435), backend Spring Boot 3.5.16 / Java 21 avec Flyway (V1 schéma conforme à D2, V2 promotions, V3 étudiants fictifs), squelette React/Vite. Ticket #1 `POST /api/sessions` : code unique (RG10), expiration à 15 min (RG1), erreurs au format `{code, message}`, interface de création de session ; 14 tests. Ticket #2 `POST /api/presences` : contrôles dans l'ordre de H6 (400 `CODE_INCONNU`, 400 `REQUETE_INVALIDE`, 409 `DEJA_PRESENT`, 410 `CODE_EXPIRE`), doublon simultané refusé par la contrainte UNIQUE (ENF3), formulaire d'émargement ; 22 tests dont 5 d'intégration sur PostgreSQL (Testcontainers). Nouvelle hypothèse H9 (émargement refusé pour un étudiant d'une autre promotion), reportée dans le cahier des charges, D3 et le contrat. Configuration CORS globale limitée à l'origine du frontend (ENF5) et proxy Vite dirigé vers `127.0.0.1:8080`, aussi pour `npm run preview` ; 2 tests CORS. Ticket #3 `POST /api/exercices` : dépôt du lien par un étudiant de la promotion (H5), 400 `LIEN_INVALIDE` pour une adresse non http(s) (RG5), 400 `REQUETE_INVALIDE`, 409 `EXERCICE_DEJA_DEPOSE` (RG4, y compris envois simultanés) ; 22 tests dont 4 d'intégration, avec un conteneur PostgreSQL de test partagé avec le ticket #2. Interface organisée en onglets (Espace Formateur par défaut, Espace Étudiant, Dépôt d'exercice pré-rempli après l'émargement), utilisables au clavier, sans perte de saisie d'un onglet à l'autre. Ticket #4 : attribution automatique des relectures (EF4) au dépôt et à chaque nouvelle présence ; relecteur tiré au hasard parmi les présents les moins chargés, jamais l'auteur (RG2, RG7, RG8) ; exercices en attente verrouillés contre les émargements simultanés ; sans nouvelle route ni changement du frontend, conformément au contrat ; 8 tests dont 3 d'intégration. Ticket #5 `POST /api/relectures/{id}` : première soumission (relecture RENDUE, exercice RELU, ou DEFINITIF après la clôture selon H8), modification tant que la session est ouverte (Q10), 409 `RELECTURE_DEJA_RENDUE` après la clôture (RG9), 400 `NOTE_INVALIDE` pour une note absente, non entière ou hors de [0 ; 20] (RG6), 403 `AUTO_RELECTURE` (RG2) ; onglet « Relecture » ; 24 tests dont 4 d'intégration. Ticket #6 `GET /api/tableau` : une ligne par étudiant de la promotion, y compris sans activité (EF7), calculée en une seule requête SQL (RG11 ; moyenne arrondie à 2 décimales, notes encore modifiables comprises selon H7) ; 404 `PROMOTION_INCONNUE` ; migration V4 avec deux index par étudiant pour ce calcul (ENF4), reportés dans D2 ; onglet « Tableau » ; 8 tests dont 2 d'intégration, sur une promotion créée pour le test. Ticket #7 (écran Formateur) : déjà livré avec le ticket #1, complété par un compte à rebours de validité du code (minuteur et barre de progression, en orange dans les 2 dernières minutes, RG1), qui couvre aussi le ticket #15. Les écrans des tickets #8 et #9 ont été livrés avec les tickets #2 et #3, celui du ticket #11 avec le ticket #6. Ticket #10 (écran Relecteur, livré avec le ticket #5) : complété par la route de consultation prévue par H3 (ticket #13), `GET /api/relectures?relecteurId=`, ajoutée au contrat comme extension (6 routes) et reportée dans H3 ; l'écran liste les relectures attribuées avec le lien de l'exercice (sans l'auteur) et permet de noter, modifier ou consulter ; 7 tests dont 1 d'intégration. Ticket #12 : clôture d'une session (EF8) par `POST /api/sessions/{id}/cloture`, deuxième route ajoutée au contrat comme extension (7 routes), reportée dans EF8, H3 et D1 ; notes rendues figées (RELU vers DEFINITIF, RG9), exercices sans relecteur passés SANS_RELECTURE, relectures en attente encore rendables une fois (H8), code refusé en 410, seconde clôture refusée en 409 `SESSION_DEJA_CLOTUREE` ; carte « Clôturer une session » avec confirmation dans l'Espace Formateur ; 11 tests dont 3 d'intégration. Ticket #14 : chaque réponse des 5 routes imposées et des 2 extensions est validée par le fichier `api/contrat.yaml` lui-même (statut déclaré, champs obligatoires, types, formats, énumérations) ; 8 tests couvrant tous les statuts du contrat, plus les erreurs hors contrat (405, 404) au format unique (ENF2). Bilan vérifié : `mvn clean test` exécute les 126 tests sans échec ni test ignoré, dont 25 d'intégration sur PostgreSQL 17, avec les migrations V1 à V4 appliquées. Reste à faire : relancer le build du frontend, vérifier le compte à rebours à la main, mesurer le temps de réponse du tableau (ENF4), décider du sort d'un dépôt d'exercice après la clôture, faire le test de bout en bout.

**Bloqué :** [durée à compléter] sans promotion en base, `POST /api/sessions` ne pouvait renvoyer que 400 : migration V2 ajoutée pour les tests manuels. [durée à compléter] la refonte de l'interface incluait l'émargement du ticket #2 : retiré ensuite pour garder le ticket #1 isolé. [durée à compléter] ma consigne du ticket #2 demandait `{codeAcces, matricule}` alors que le contrat imposé attend `{code, etudiantId}` avec un 410 `CODE_EXPIRE` : contrat conservé. [durée à compléter] le frontend recevait une erreur CORS ou réseau lors des appels à l'API : cause non confirmée, puisque le proxy Vite évite tout contrôle CORS au navigateur ; configuration CORS ajoutée et proxy dirigé vers `127.0.0.1`, résultat à confirmer. [durée à compléter] ma consigne du ticket #3 décrivait la publication d'un exercice par le formateur (titre, consignes, 404) alors que le contrat et EF3 prévoient le dépôt d'un lien par l'étudiant : contrat conservé. [durée à compléter] ma consigne du ticket #4 demandait un écran pour lancer ou consulter la répartition des relectures, alors que le contrat n'a aucune route pour l'attribution : contrat strict conservé, attribution interne sans écran dédié. [durée à compléter] l'écran de relecture demande l'identifiant de la relecture, qu'aucune route du contrat ne permet de connaître, et le relecteur ne voit pas l'exercice : résolu au ticket #10 en ajoutant la route de consultation prévue par H3 au contrat, comme extension. [durée à compléter] ma consigne de complément du ticket #14 citait une route `POST /api/relectures/attribution` qui n'existe pas et des statuts 204 et 404 que le contrat ne prévoit pas pour les extensions : contrat conservé, tests alignés sur lui.

**IA :** lui ai demandé l'initialisation (Docker, Spring Boot, Flyway, React), les tickets #1 à #10, #12 et #14 (backend, tests, interface) et les migrations V2 à V4. Vérifié : `mvn clean test`, lancé par l'IA à ma demande, donne 126 tests verts (dont 25 d'intégration sur PostgreSQL, aucun ignoré) ; le build du frontend réussissait au ticket #5 ; requêtes et réponses relues face au contrat. L'IA a signalé avant de coder que mes consignes des tickets #2, #3 et #4 contredisaient le contrat, et j'ai choisi le contrat les trois fois. Après l'ajout de H9, contrat et diagrammes revalidés (Redocly, script de conformité, parseur Mermaid). Face à l'erreur CORS, l'IA a d'abord vérifié que le frontend passait bien par le proxy Vite et n'appelait pas le port 8080 directement, avant d'ajouter la configuration demandée. Pour le ticket #5, l'IA a relevé que la lecture JSON par défaut arrondirait silencieusement une note de 12.5 en 12 : la note est lue sans conversion et refusée par `NOTE_INVALIDE`, cas couvert par les tests. Pour le ticket #6, l'IA a signalé que la performance exigée par ENF4 n'était pas mesurée et a proposé les index de V4. Pour le ticket #7, l'IA a constaté que l'écran existait déjà depuis le ticket #1 au lieu de le recoder, et a proposé d'y ajouter le compte à rebours du ticket #15. Pour le ticket #10, l'IA a signalé que l'écran livré au ticket #5 ne permettait ni de connaître l'identifiant de la relecture ni de voir l'exercice ; j'ai choisi d'ajouter la route de consultation au contrat plutôt que de garder le contrat strict. Pour le ticket #12, l'IA a signalé deux cas non couverts : le dépôt d'un exercice après la clôture, que le cahier des charges ne tranche pas, et une relecture envoyée à l'instant exact de la clôture ; laissés en l'état pour l'instant. Pour le ticket #14, l'IA a écarté l'adaptateur MockMvc officiel de la bibliothèque de validation, compilé pour Spring 5, et a vérifié dans sa documentation l'API du module utilisé à la place. L'IA a contrôlé les versions des dépendances sur Maven Central et npm avant de les écrire. Le contrat à 7 routes est lu et appliqué par le test de conformité, mais le lint Redocly n'a pas été relancé ; le compte à rebours n'a pas de test automatique, le frontend n'ayant pas d'outil de test.

---

## Étape 3 — Enveloppe

**Fait :**

**Bloqué :**

**IA :**

**Ce que j'ai sorti du périmètre pour absorber le changement, et pourquoi :**

---

## Étape 4 — Version finale

**Fait :**

**Bloqué :**

**IA :**

---

## Étape 5 — Soumission

**Fait :**

**Ce que je referais autrement avec une journée de plus :**
