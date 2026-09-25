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

**Fait :** (en cours : tickets #1 à #4 écrits) environnement mis en place : PostgreSQL 17 sous Docker (port 5435), backend Spring Boot 3.5.16 / Java 21 avec Flyway (V1 schéma conforme à D2, V2 promotions, V3 étudiants fictifs), squelette React/Vite. Ticket #1 `POST /api/sessions` : code unique (RG10), expiration à 15 min (RG1), erreurs au format `{code, message}`, interface de création de session ; 14 tests. Ticket #2 `POST /api/presences` : contrôles dans l'ordre de H6 (400 `CODE_INCONNU`, 400 `REQUETE_INVALIDE`, 409 `DEJA_PRESENT`, 410 `CODE_EXPIRE`), doublon simultané refusé par la contrainte UNIQUE (ENF3), formulaire d'émargement ; 22 tests dont 5 d'intégration sur PostgreSQL (Testcontainers). Nouvelle hypothèse H9 (émargement refusé pour un étudiant d'une autre promotion), reportée dans le cahier des charges, D3 et le contrat. Configuration CORS globale limitée à l'origine du frontend (ENF5) et proxy Vite dirigé vers `127.0.0.1:8080`, aussi pour `npm run preview` ; 2 tests CORS. Ticket #3 `POST /api/exercices` : dépôt du lien par un étudiant de la promotion (H5), 400 `LIEN_INVALIDE` pour une adresse non http(s) (RG5), 400 `REQUETE_INVALIDE`, 409 `EXERCICE_DEJA_DEPOSE` (RG4, y compris envois simultanés) ; 22 tests dont 4 d'intégration, avec un conteneur PostgreSQL de test partagé avec le ticket #2. Interface organisée en onglets (Espace Formateur par défaut, Espace Étudiant, Dépôt d'exercice pré-rempli après l'émargement), utilisables au clavier, sans perte de saisie d'un onglet à l'autre. Tickets #1 à #3 et CORS : les 60 tests passent, dont 9 d'intégration, et le build du frontend réussit. Ticket #4 : attribution automatique des relectures (EF4) au dépôt et à chaque nouvelle présence ; relecteur tiré au hasard parmi les présents les moins chargés, jamais l'auteur (RG2, RG7, RG8) ; exercices en attente verrouillés contre les émargements simultanés ; sans nouvelle route ni changement du frontend, conformément au contrat ; 8 tests écrits dont 3 d'intégration. Reste à faire : exécuter les tests du ticket #4, faire le test de bout en bout.

**Bloqué :** [durée à compléter] sans promotion en base, `POST /api/sessions` ne pouvait renvoyer que 400 : migration V2 ajoutée pour les tests manuels. [durée à compléter] la refonte de l'interface incluait l'émargement du ticket #2 : retiré ensuite pour garder le ticket #1 isolé. [durée à compléter] ma consigne du ticket #2 demandait `{codeAcces, matricule}` alors que le contrat imposé attend `{code, etudiantId}` avec un 410 `CODE_EXPIRE` : contrat conservé. [durée à compléter] le frontend recevait une erreur CORS ou réseau lors des appels à l'API : cause non confirmée, puisque le proxy Vite évite tout contrôle CORS au navigateur ; configuration CORS ajoutée et proxy dirigé vers `127.0.0.1`, résultat à confirmer. [durée à compléter] ma consigne du ticket #3 décrivait la publication d'un exercice par le formateur (titre, consignes, 404) alors que le contrat et EF3 prévoient le dépôt d'un lien par l'étudiant : contrat conservé. [durée à compléter] ma consigne du ticket #4 demandait un écran pour lancer ou consulter la répartition des relectures, alors que le contrat n'a aucune route pour l'attribution : contrat strict conservé, attribution interne sans écran dédié.

**IA :** lui ai demandé l'initialisation (Docker, Spring Boot, Flyway, React), les tickets #1 à #4 (backend, tests, interface) et les migrations V2 et V3. Vérifié : les 60 tests des tickets #1 à #3 et de la configuration CORS, exécutés avec `mvn test`, sont verts (dont 9 d'intégration sur PostgreSQL, aucun ignoré) et le build du frontend réussit ; requêtes et réponses relues face au contrat. L'IA a signalé avant de coder que mes consignes des tickets #2, #3 et #4 contredisaient le contrat, et j'ai choisi le contrat les trois fois. Après l'ajout de H9, contrat et diagrammes revalidés (Redocly, script de conformité, parseur Mermaid). Face à l'erreur CORS, l'IA a d'abord vérifié que le frontend passait bien par le proxy Vite et n'appelait pas le port 8080 directement, avant d'ajouter la configuration demandée. L'IA a contrôlé les versions des dépendances sur Maven Central et npm avant de les écrire. Les tests du ticket #4 ne sont pas encore exécutés.

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
