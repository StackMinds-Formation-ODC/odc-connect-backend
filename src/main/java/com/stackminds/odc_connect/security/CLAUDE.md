# CLAUDE.md — `security/` (briques de sécurité)

> Instruction de projet : **lecture seule pour les agents IA**. Complète `docs/memoire/decisions/0002-authentification-jwt-resource-server.md`.

## Rôle

Fournir les **composants réutilisables** de sécurité : lecture des rôles du JWT, utilisateur courant, constantes de rôles.
La **chaîne de filtres** (`SecurityFilterChain`) reste dans `config/SecurityConfig`. Cette API est un *resource server* : elle valide
des JWT émis ailleurs (Keycloak ou équivalent). **Elle ne gère ni mots de passe, ni login, ni émission de jetons.**

## Ce qu'on crée ici

| Fichier | Contenu |
|---|---|
| `JwtAuthConverter.java` | `Converter<Jwt, AbstractAuthenticationToken>` : claims → `GrantedAuthority` |
| `CurrentUser.java` | accès à l'utilisateur authentifié (sujet, rôles) |
| `Roles.java` | constantes des noms de rôles (`ADMIN`, `MEMBER`, …) |

## Gabarits

```java
public final class Roles {
	public static final String ADMIN = "ADMIN";
	public static final String MEMBER = "MEMBER";
	private Roles() {}
}

@Component
public class JwtAuthConverter implements Converter<Jwt, AbstractAuthenticationToken> {

	private final JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter(); // SCOPE_xxx

	@Override
	public AbstractAuthenticationToken convert(Jwt jwt) {
		Set<GrantedAuthority> authorities = new HashSet<>(scopes.convert(jwt));
		realmRoles(jwt).forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
		return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
	}

	// Keycloak : {"realm_access": {"roles": ["ADMIN", "MEMBER"]}}
	private static List<String> realmRoles(Jwt jwt) {
		Map<String, Object> realm = jwt.getClaimAsMap("realm_access");
		if (realm == null || !(realm.get("roles") instanceof Collection<?> roles)) {
			return List.of();
		}
		return roles.stream().map(String::valueOf).toList();
	}
}

@Component
public class CurrentUser {

	public String subject() {                              // claim « sub » : identifiant chez le fournisseur
		return jwt().getSubject();
	}

	public boolean hasRole(String role) {
		return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
			.anyMatch(a -> a.getAuthority().equals("ROLE_" + role));
	}

	private static Jwt jwt() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth instanceof JwtAuthenticationToken token) {
			return token.getToken();
		}
		throw new IllegalStateException("Aucun utilisateur authentifié dans le contexte");
	}
}
```

## Règles

- **Rôles** : stockés en autorités avec le préfixe `ROLE_` (comme `hasRole('ADMIN')` l'attend). Le nom du claim dépend du
  fournisseur : Keycloak `realm_access.roles` (ou `resource_access.<client>.roles`) ; à confirmer pour l'IdP retenu **avant** d'écrire
  le convertisseur. Ne jamais « deviner » un rôle absent : pas de rôle ⇒ aucune autorité.
- **Aucune donnée d'identité en base** pour authentifier : pas de table de mots de passe, pas de hachage. Si le métier a besoin d'un
  profil local, il référence le `sub` du JWT (colonne `external_subject`) et vit dans `model/`.
- **Stateless** : aucun état de session ni cache d'utilisateur dans ces classes (beans singletons).
- Ne jamais journaliser un JWT, un en-tête `Authorization`, ni les claims personnels.
- Lire le contexte de sécurité **uniquement** via `CurrentUser` (un seul point à mocker dans les tests).
- Validation du jeton (signature, `exp`, `iss`, `aud`) : réglage de `config/` + `application*.yaml` (`issuer-uri`), pas du code ici.
- `security/` ne dépend pas de `service`/`controller`/`repository` ; `service` et `controller` peuvent en dépendre.

## Branchement (dans `config/SecurityConfig`, pas ici)

```java
.oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(jwtAuthConverter)))
```
et `@EnableMethodSecurity` sur la classe de config pour activer `@PreAuthorize` (voir `config/CLAUDE.md`).

## Interdits

- Mot de passe, hash, login, émission ou signature de jeton, `UserDetailsService` à base de table.
- `SecurityContextHolder` utilisé directement dans un service/contrôleur (passer par `CurrentUser`).
- Noms de rôles écrits en dur dans le code Java (comparaisons, `CurrentUser.hasRole("ADMIN")`) : utiliser `Roles`. Seul
  `@PreAuthorize("hasRole('ADMIN')")` prend un littéral (SpEL) — le garder identique à la constante.
- Désactiver la validation de signature « pour tester ».

## Tests

`security/JwtAuthConverterTest` (unitaire) : un `Jwt` construit avec `Jwt.withTokenValue(...)` et des claims → autorités attendues
(avec rôles, sans `realm_access`, avec liste vide). `CurrentUser` : contexte rempli à la main (`SecurityContextHolder`) puis nettoyé
(`@AfterEach`).
