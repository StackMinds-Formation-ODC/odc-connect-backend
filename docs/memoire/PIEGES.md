# Pièges connus

Constatés dans le code au 2026-11-17 (dépôt encore à l'état de squelette). Barrer (`~~…~~`) et dater un piège quand il est corrigé.

- **`.mvn/wrapper/` absent de l'archive.** `mvnw` (Maven Wrapper 3.3.4) lit `.mvn/wrapper/maven-wrapper.properties` et
  échoue sans lui (« cannot read distributionUrl »). Vérifier le clone ; sinon `mvn wrapper:wrapper` ou utiliser `mvn`.
  Les `Dockerfile` utilisent l'image Maven et ne sont pas concernés.
- **Flyway sans aucune migration + `ddl-auto: validate`.** `db/migration/` n'existe pas. Le démarrage passe tant qu'il n'y a
  aucune entité ; **dès qu'une `@Entity` est ajoutée sans migration, l'application refuse de démarrer** (validation du
  schéma). Toujours créer la migration avec l'entité (checklist, étape 1).
- **Ne jamais modifier une migration appliquée.** Flyway compare les checksums et échoue au démarrage ; corriger par une
  nouvelle migration `V<n+1>`.
- **JWKS de dev inaccessible depuis le conteneur.** Le défaut `http://localhost:8081/…` désigne la machine hôte hors Docker ;
  dans le conteneur `app`, `localhost` est le conteneur lui-même, et le compose ne contient **aucun** fournisseur d'identité.
  La récupération des clés est paresseuse : l'app démarre, mais toute requête authentifiée échoue (401) tant qu'un
  Keycloak (ou équivalent) n'est pas joignable. Surcharger `JWT_JWK_SET_URI` (ex. `host.docker.internal`) ou ajouter un
  service Keycloak au compose.
- **`iss` et `aud` non validés.** Seul `jwk-set-uri` est configuré : Spring ne vérifie que la signature et l'expiration.
  Ajouter `spring.security.oauth2.resourceserver.jwt.issuer-uri` (validation de l'émetteur) et, si l'IdP le permet, un
  validateur d'audience.
- **Rôles du JWT non lus.** Le convertisseur par défaut ne mappe que `scope`/`scp` en `SCOPE_*`. Les rôles Keycloak sont dans
  `realm_access.roles` (ou `resource_access.<client>.roles`) : écrire `security/JwtAuthConverter` avant d'utiliser
  `hasRole(...)`/`@PreAuthorize`. De plus `@EnableMethodSecurity` n'est pas activé : `@PreAuthorize` serait ignoré
  silencieusement tant qu'il manque.
- **Pas de CORS.** Un frontend d'une autre origine sera bloqué par le navigateur (le préflight `OPTIONS` reçoit 401 car
  `anyRequest().authenticated()`). Déclarer un `CorsConfigurationSource` dans `config/` et l'activer dans `SecurityConfig`
  (`http.cors(...)`), avec des origines explicites par profil.
- **Starter `mail` sans configuration.** Aucune propriété `spring.mail.*` : sans `spring.mail.host`, **aucun bean
  `JavaMailSender` n'est créé** ; l'injecter fait échouer le démarrage. Prévoir la configuration (par profil, secrets en
  variables d'environnement) avec la première fonctionnalité d'envoi d'e-mail. Un serveur SMTP de dev (Mailpit/MailHog) manque au compose.
- **Tout test d'intégration exige Docker.** `OdcConnectApplicationTests` utilise Testcontainers : sans démon Docker,
  `./mvnw test` échoue même si le code est correct. Lancer `-DexcludedGroups=integration` après avoir taggé les tests
  (le tag n'est pas encore posé sur `contextLoads`).
- **Testcontainers 2.x.** Le test existant importe `org.testcontainers.postgresql.PostgreSQLContainer` (non générique, module
  `testcontainers-postgresql`). Ne pas réintroduire l'ancien `org.testcontainers.containers.PostgreSQLContainer`
  ni l'ancien artefact `postgresql` de Testcontainers 1.x.
- **Spring Boot 4 ≠ tutoriels Boot 3.** Starters renommés/modularisés (`spring-boot-starter-webmvc`,
  `spring-boot-starter-*-test`), annotations de test déplacées, Jackson 3 (paquet `tools.jackson.*` pour databind ; les
  annotations restent `com.fasterxml.jackson.annotation.*`). Se référer à la doc 4.1.2 (liens dans `HELP.md`) avant de
  copier un exemple.
- **`open-in-view: false` + mapping DTO hors transaction = `LazyInitializationException`.** Mapper dans la méthode
  `@Transactional` du service (règle d'architecture).
- **Lombok et processeurs d'annotations.** `maven-compiler-plugin` redéfinit `annotationProcessorPaths` (compile et
  testCompile) avec Lombok seul. **Ajouter MapStruct ou un autre processeur exige de le déclarer ici** et, pour MapStruct,
  d'ajouter `lombok-mapstruct-binding` après Lombok ; sinon les mappers générés ne voient pas les getters Lombok.
- **`pom.xml` : éléments vides.** `<name/>`, `<description/>`, `<url/>`, `<licenses><license/></licenses>`,
  `<developers><developer/></developers>` et `<scm>` vides issus d'Initializr. Sans effet sur le build mais à renseigner ou
  supprimer (`<name/>` vide donne un nom d'artefact vide dans certains outils).
- **Package `odc_connect`.** Le `_` vient de `odc-connect`, nom invalide en Java (voir `HELP.md`). Ne pas « corriger » le package.
- **Mot de passe `change-me`.** Valeur par défaut volontaire **dev seulement** (`application-dev.yaml`, `.env.example`). La prod
  n'a aucun défaut et exige `DB_PASSWORD` ; ne jamais copier cette valeur dans un fichier de prod.
- **Dossier `tests` sous `src/main` (autre projet).** Le projet de référence `TaskBoard` montrait un dossier `tests` dans
  `src/main/java/…`. Ici les tests vivent sous `src/test/java` (voir `decisions/0001`) : un test sous `src/main` serait
  empaqueté dans le jar et ne verrait pas les dépendances de test.
- **Casse du package : `stackMinds` vs `stackminds`.** Le dépôt `ODC_connect.zip` (clone du 2026-11-13) a le package
  `com.stackMinds.odc_connect` (M majuscule), `groupId com.stackMinds`, `artifactId odc_connect`. L'état « complété »
  (Dockerfile, `SecurityConfig`…) et le patch d'infrastructure utilisent `com.stackminds.odc_connect` et `odc-connect`.
  Sous Linux/macOS (Docker, CI) `stackMinds` et `stackminds` sont **deux dossiers différents** ; sous Windows le même.
  Choisir **minuscules** (convention Java) et renommer le dossier **avec `git mv`** en une fois (le `groupId` aussi).
- **`pom.xml` du clone minimal.** Le clone `ODC_connect.zip` n'a que `spring-boot-starter` (+ `starter-test`) : sans
  `webmvc`, l'application démarre puis s'arrête (pas de serveur web) ; sans `data-jpa`/`flyway`/`security`/`validation`,
  les gabarits des `CLAUDE.md` de couche ne compilent pas. Il faut le `pom.xml` complet (voir `ETAT.md`).
- **Pas de `.gitignore` dans le clone** : `target/` apparaît comme non suivi (`?? target/`). Le patch d'infrastructure en
  ajoute un ; sinon `git add .` committerait les classes compilées.
- **`token.txt` à côté du dépôt** (`ODC connect/token.txt`, 58 caractères, fourni dans l'archive). Ressemble à un jeton
  d'accès : ne jamais le copier dans le dépôt ni le commiter ; s'il a été partagé, le **révoquer** et en générer un autre.
- **Dossier `.github/modernize/java-upgrade/`** (outil de migration Java) : `.gitignore` interne `**/*` ; sans rapport avec
  le build, à ignorer.
