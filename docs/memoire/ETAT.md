# État du dépôt `odc-connect-backend`

Mis à jour le 2026-11-17 (analyse initiale du code reçu).

## Résumé

Le dépôt est un **squelette Spring Boot** généré (Initializr) avec l'infrastructure en place (Docker, profils, sécurité JWT,
Flyway, Testcontainers) mais **aucun code métier** : pas d'entité, de contrôleur, de service ni de migration.
L'architecture cible (couches `config / controller / dto / exception / mapper / model / repository / security / service`)
est documentée dans `docs/instruction/` mais **les dossiers n'existent pas encore** dans `src/main/java`.

## Deux états du dépôt rencontrés (2026-11-17)

- **Clone brut** (`ODC_connect.zip`, 2026-11-13) : package `com.stackMinds.odc_connect`, `pom.xml` minimal (`spring-boot-starter` +
  `starter-test`), `application.properties`, aucun Docker/Flyway/sécurité, pas de `.gitignore`, historique git `main`/`master` ("init").
- **État complété** (`odc-connect-backend.zip` + patch d'infrastructure) : package `com.stackminds.odc_connect`, `pom.xml` complet,
  Docker, profils, `SecurityConfig`. **C'est cet état que décrivent les sections ci-dessous.**

## Stack (vérifiée dans `pom.xml` de l'état complété)

- Java 21 · Spring Boot **4.1.2** (parent) · Maven, groupe `com.stackminds`, artefact `odc-connect`, version `0.0.1-SNAPSHOT`.
- Starters : `webmvc`, `data-jpa`, `flyway` (+ `flyway-database-postgresql`), `security`, `security-oauth2-resource-server`,
  `validation`, `actuator`, `mail` ; `devtools` (runtime, optionnel), `postgresql` (runtime), `lombok` (optionnel).
- Tests : starters `*-test` correspondants, `spring-boot-testcontainers`, `testcontainers-junit-jupiter`,
  `testcontainers-postgresql`.
- Lombok déclaré comme processeur d'annotations **explicitement** dans `maven-compiler-plugin` (compile et testCompile).

## Code existant

| Fichier | Rôle |
|---|---|
| `OdcConnectApplication` | `@SpringBootApplication`, point d'entrée |
| `config/SecurityConfig` | Chaîne de filtres : CSRF désactivé, sessions `STATELESS`, `/actuator/health/**` et `/actuator/info` publics, `anyRequest().authenticated()`, `oauth2ResourceServer().jwt()` avec réglages par défaut |
| `test/.../TestcontainersConfiguration` | Conteneur `postgres:17` en `@ServiceConnection` |
| `test/.../TestOdcConnectApplication` | Lancement de l'app avec le conteneur de test |
| `test/.../OdcConnectApplicationTests` | `contextLoads` (`@SpringBootTest` + Testcontainers) — **seul test**, exige Docker, pas encore taggé `integration` |

## Configuration

- `application.yaml` : nom `odc-connect`, `open-in-view: false`, `ddl-auto: validate` (schéma = Flyway), `jwk-set-uri` =
  `${JWT_JWK_SET_URI:http://localhost:8081/realms/odc/protocol/openid-connect/certs}`, arrêt `graceful`, Actuator
  `health,info` avec probes.
- `application-dev.yaml` : datasource `localhost:5432/odc`, user `odc`, mot de passe `${DB_PASSWORD:change-me}`, `show-sql`,
  health détaillé, logs `com.stackminds` en DEBUG.
- `application-prod.yaml` : tout par variables d'environnement, `JWT_JWK_SET_URI` **sans défaut** (échec volontaire si absent),
  `show-sql: false`, health sans détails, logs INFO.
- `.env.example` : `DB_NAME`, `DB_USER`, `DB_PASSWORD`, `JWT_JWK_SET_URI`.

## Docker

- `docker-compose.yml` : service `db` (postgres:17, port lié à `127.0.0.1:5432`, volume `pgdata`, healthcheck) pour les
  profils `dev` et `prod` ; `app` (profil `dev`, `Dockerfile.dev`, `mvn spring-boot:run`, `./src` monté en volume, cache
  `m2`) ; `app-prod` (profil `prod`, `Dockerfile`, exige `JWT_JWK_SET_URI`). Port `8080`.
- `Dockerfile` : build Maven 3.9.9 / Temurin 21, image d'exécution `eclipse-temurin:21-jre`, utilisateur non-root `app`
  (uid 1001), `HEALTHCHECK` sur `/actuator/health`, `-XX:MaxRAMPercentage=75`.

## Absent (à créer au fil des fonctionnalités)

- Dossiers `controller/ dto/ exception/ mapper/ model/ repository/ security/ service/` ; `GlobalExceptionHandler` / `ErrorCode`.
- `src/main/resources/db/migration/` (donc aucun schéma) ; `db/seed/dev/`.
- CORS ; `@EnableMethodSecurity` ; conversion des rôles du JWT ; configuration `spring.mail.*` ; documentation OpenAPI.
- Outils de qualité (Spotless/Checkstyle, Sonar, ArchUnit), CI (`.github/` absent).
- `.mvn/wrapper/` absent de l'archive reçue → `./mvnw` ne démarre pas tel quel (voir `PIEGES.md`).

## Docs

`docs/instruction/` (5 fichiers), `CLAUDE.md` racine, et **11 `CLAUDE.md` de couche** (model, repository, dto, mapper, service,
controller, exception, security, config, tests, `db/`) adaptés depuis `app-service` (NestJS, architecture hexagonale) vers
Spring Boot en couches. Décisions 0001 à 0004 et journal du 2026-11-17 : voir `decisions/` et `journal/`.

## Prochaines étapes suggérées

1. Restaurer `.mvn/wrapper/` et vérifier `./mvnw -v`.
2. Créer les dossiers de couches et `exception/` (`BusinessException`, `ErrorCode`, `GlobalExceptionHandler`).
3. Ajouter `security/JwtAuthConverter` (rôles de l'IdP) et décider du format des rôles (voir `PIEGES.md`).
4. Première fonctionnalité de bout en bout (migration → model → … → tests) pour valider la checklist.
5. Tagger `OdcConnectApplicationTests` `@Tag("integration")`.
