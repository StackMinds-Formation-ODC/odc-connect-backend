# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository

`odc-connect-backend` : API REST d'**ODC Connect** (équipe `stackminds`). Dépôt git **unique** (un seul module Maven),
pas un workspace multi-dépôts.

| Élément | Valeur |
|---|---|
| Stack | Java 21 · Spring Boot 4.1.2 · Maven (wrapper `mvnw`) |
| Persistance | Spring Data JPA (Hibernate) · PostgreSQL 17 · migrations Flyway |
| Sécurité | Spring Security, API stateless, **OAuth2 Resource Server (JWT)** : les jetons sont émis par un fournisseur externe (ex. Keycloak), cette API ne fait que les valider |
| Autres starters | Validation, Actuator, Mail, Lombok, DevTools |
| Tests | JUnit 5, Spring Boot test starters, spring-security-test, Testcontainers (PostgreSQL) |
| Package racine | `com.stackminds.odc_connect` — minuscules (convention Java) ; le `_` est voulu (`odc-connect` n'est pas un nom de package valide). Si le dépôt a encore `com.stackMinds`, voir `docs/memoire/PIEGES.md` |

> État réel du code : voir `docs/memoire/ETAT.md`. Au moment de la rédaction de ce fichier, seuls `OdcConnectApplication`
> et `config/SecurityConfig` existent ; le reste de l'architecture ci-dessous est la **cible** à respecter.

## Dossier `docs/` (règles pour tous les agents IA)

```
docs/
  instruction/        # instructions du projet — LECTURE SEULE pour les agents IA
  memoire/            # mémoire de travail des agents — lecture/écriture
    decisions/        # décisions d'architecture / techniques (NNNN-sujet.md)
    journal/          # journal chronologique (AAAA-MM-JJ-sujet.md)
    ETAT.md           # état courant du dépôt
    PIEGES.md         # pièges connus
```

- **Lecture obligatoire en début de session** : avant la première modification, lire `docs/memoire/ETAT.md`,
  `docs/memoire/PIEGES.md` et ce qui est pertinent dans `docs/instruction/`. Ce n'est pas optionnel.
- **`docs/instruction/` est en lecture seule** : ne jamais y créer, modifier, renommer ou supprimer quoi que ce soit.
  Si une instruction semble fausse ou périmée, le dire à l'utilisateur au lieu de la changer.
- **`docs/memoire/` est en lecture/écriture** : y consigner une décision, mettre à jour `ETAT.md`, ajouter un piège à
  `PIEGES.md`, ajouter une entrée de journal, quand c'est utile ou quand l'utilisateur le demande.

Instructions disponibles (`docs/instruction/`) :

| Fichier | Sujet |
|---|---|
| `architecture-couches-spring.md` | Couches, règle de dépendance, rôle de chaque dossier, checklist nouvelle fonctionnalité |
| `nomenclature-java.md` | Noms de packages/classes/méthodes, routes, tables, migrations Flyway, tokens d'erreur |
| `qualite-de-code.md` | Limites de taille, SOLID, règles Java/Spring, erreurs, sécurité |
| `strategie-tests.md` | Tests autorisés / interdits, emplacement, exécution |
| `seeder-gestion.md` | Données de départ (référence vs démo) via Flyway, séparation des environnements |

## Architecture

Architecture **en couches** (Spring Boot classique), sous `src/main/java/com/stackminds/odc_connect/` :

```
config/        # @Configuration : SecurityConfig (existe), CORS, OpenAPI, propriétés @ConfigurationProperties…
controller/    # @RestController minces : valider l'entrée -> service -> réponse
dto/           # records Request/Response + Bean Validation (jamais d'entité)
exception/     # BusinessException, ErrorCode, GlobalExceptionHandler (@RestControllerAdvice)
mapper/        # entité <-> DTO (classes @Component écrites à la main)
model/         # entités JPA (+ model/enums)
repository/    # interfaces Spring Data JPA (persistance seule)
security/      # JwtAuthConverter (rôles du JWT), CurrentUser, constantes de rôles
service/       # logique métier, @Transactional, règles inter-entités (service/policy)
OdcConnectApplication.java
```

Ressources : `src/main/resources/{application*.yaml, db/migration/ (schéma), db/seed/dev/ (démo, dev/test seulement)}`.
Tests : `src/test/java/com/stackminds/odc_connect/` (même découpage par couche — voir `strategie-tests.md`).

Règle de dépendance : `controller -> service -> repository -> model`. `dto`, `mapper`, `exception`, `security` et `config`
sont transverses mais **ne remontent jamais** : `repository`/`model` ne connaissent ni `service`, ni `controller`, ni `dto` ;
`service` ne connaît aucun type HTTP ; `controller` n'appelle jamais un `repository` ni n'expose une entité.
Le mapping entité -> DTO se fait **dans la méthode `@Transactional` du service** (`spring.jpa.open-in-view=false`).

Conventions clés (détail dans `docs/instruction/nomenclature-java.md`) :
- Identifiants Java/SQL/JSON en **anglais** ; commentaires, Javadoc, messages d'erreur, docs et commits en **français**.
- Classes : `PascalCase` + suffixe de rôle (`MemberService`, `MemberController`, `CreateMemberRequest`, `MemberResponse`).
  Tables/colonnes : `snake_case`. Routes : `/api/<ressources-pluriel-kebab>`.
- `id` (clé interne) ne sort **jamais** de l'API ; la ressource est désignée par `publicId` (UUID, colonne `public_id`).
- Le domaine parle en **codes d'erreur** (`ErrorCode`, ex. `MEMBER_NOT_FOUND`) ; seul `GlobalExceptionHandler` les traduit en
  statut HTTP (corps `ProblemDetail` RFC 9457 + propriété `code`).
- Injection **par constructeur** (`@RequiredArgsConstructor`), jamais `@Autowired` sur un champ.

## `CLAUDE.md` par couche (instructions détaillées)

Chaque couche a son propre `CLAUDE.md`, **chargé automatiquement dès qu'on lit ou modifie un fichier de ce dossier**. Il décrit
les fichiers à créer à ce niveau : gabarit de code, règles, interdits, tests, checklist. **Les lire avant de créer un fichier
dans la couche.** En cas de conflit : `docs/instruction/` > `CLAUDE.md` de couche > ce fichier. Ils sont en **lecture seule** pour
les agents (comme `docs/instruction/`) : signaler à l'utilisateur ce qui semble faux.

| Dossier | Fichier |
|---|---|
| `model/` | `src/main/java/<pkg>/model/CLAUDE.md` — entités JPA, enums |
| `repository/` | `…/repository/CLAUDE.md` — interfaces Spring Data, requêtes |
| `dto/` | `…/dto/CLAUDE.md` — records Request/Response, validation |
| `mapper/` | `…/mapper/CLAUDE.md` — entité ↔ DTO |
| `service/` | `…/service/CLAUDE.md` — métier, transactions, policies |
| `controller/` | `…/controller/CLAUDE.md` — routes REST |
| `exception/` | `…/exception/CLAUDE.md` — `ErrorCode`, `BusinessException`, handler |
| `security/` | `…/security/CLAUDE.md` — conversion des rôles JWT, utilisateur courant |
| `config/` | `…/config/CLAUDE.md` — `SecurityConfig`, CORS, propriétés |
| tests | `src/test/java/<pkg>/CLAUDE.md` — un gabarit de test par couche |
| SQL | `src/main/resources/db/CLAUDE.md` — migrations Flyway, seeds |

## Commandes

Toujours exécuter depuis la racine du dépôt.

```bash
./mvnw -v                                   # vérifie le wrapper (voir note ci-dessous)
./mvnw clean package -DskipTests            # build du jar -> target/*.jar
./mvnw test                                 # TOUS les tests (les tests d'intégration exigent Docker)
./mvnw test -DexcludedGroups=integration    # tests unitaires seulement (sans Docker)
./mvnw test -Dtest=MemberServiceTest        # une classe ; -Dtest=MemberServiceTest#shouldRejectDuplicateEmail pour une méthode
```

> **Note wrapper** : `mvnw` lit `.mvn/wrapper/maven-wrapper.properties`. Ce dossier était absent de l'archive analysée ; s'il
> manque dans ton clone, `./mvnw` échoue (« cannot read distributionUrl »). Restaure-le (`mvn wrapper:wrapper`) ou utilise `mvn`.

### Lancer l'application

```bash
cp .env.example .env                        # renseigner DB_PASSWORD (jamais versionné)

# Tout en Docker (db + app, profil Spring `dev`) :
docker compose --profile dev up --build

# Ou : base en Docker + app sur la machine
docker compose --profile dev up -d db
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev   # sans profil, aucune datasource -> échec au démarrage

# Production (jar dans l'image, profil `prod`) :
docker compose --profile prod up --build -d             # exige DB_PASSWORD et JWT_JWK_SET_URI
```

- Port `8080` ; sondes : `GET /actuator/health` (liveness/readiness activés) et `/actuator/info`, **seules routes publiques**.
  Tout le reste exige un JWT valide (`SecurityConfig`).
- Variables d'environnement : `DB_NAME`, `DB_USER` (défaut `odc`), `DB_PASSWORD` (obligatoire), `JWT_JWK_SET_URI`
  (obligatoire en prod ; en dev, défaut `http://localhost:8081/realms/odc/protocol/openid-connect/certs`). En prod tout passe
  par `SPRING_DATASOURCE_URL|USERNAME|PASSWORD` + `JWT_JWK_SET_URI` ; un démarrage sans elles échoue volontairement.
- Aucun secret dans le code ni dans `application*.yaml` (le mot de passe `change-me` n'existe que comme défaut de dev).

### Base de données

Le schéma appartient à **Flyway** (`src/main/resources/db/migration/V<n>__<description>.sql`) ; Hibernate est en
`ddl-auto: validate`. Toute entité ou colonne ajoutée **sans migration** fait échouer le démarrage. Ne jamais modifier une
migration déjà appliquée : en ajouter une nouvelle.
