# 0002 — Authentification : API « resource server » JWT (constat)

Date : 2026-11-17 · Statut : **constat du code existant** (`SecurityConfig`, `application*.yaml`)

## Constat

- L'API **ne délivre pas** de jetons et ne stocke pas de mots de passe : elle valide des JWT émis par un fournisseur OAuth2/OIDC
  externe (l'exemple du dépôt est Keycloak, realm `odc`) via `spring.security.oauth2.resourceserver.jwt.jwk-set-uri`.
- API stateless : CSRF désactivé, `SessionCreationPolicy.STATELESS`.
- Routes publiques : `/actuator/health/**` et `/actuator/info` (sondes Docker/orchestrateur). Tout le reste : authentifié.
- Dev : URI par défaut `http://localhost:8081/realms/odc/protocol/openid-connect/certs`. Prod : `JWT_JWK_SET_URI` obligatoire,
  sans défaut.

## Différence avec `app-service`

`app-service` portait son propre module `auth` (comptes, mots de passe bcrypt, jetons opaques). Ce n'est **pas** le modèle
d'`odc-connect-backend` : ne pas y recréer de tables d'identifiants ni de endpoints de login.

## À décider (voir `PIEGES.md`)

- Fournisseur d'identité retenu et ajout d'un service dans le compose de dev.
- Où vivent les rôles (claim `realm_access.roles` ? `scope` ?) → écrire `security/JwtAuthConverter`.
- Validation de l'émetteur (`issuer-uri`) et de l'audience.
- Table de profils locale référençant le `sub` du JWT (colonne `external_subject`) si des données utilisateur propres à
  ODC Connect sont nécessaires.
