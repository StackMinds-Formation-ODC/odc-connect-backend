# CLAUDE.md — `config/` (câblage Spring)

> Instruction de projet : **lecture seule pour les agents IA**. Complète `docs/instruction/architecture-couches-spring.md`.

## Rôle

Déclarer les beans d'infrastructure et lier la configuration externe. **Aucune logique métier.** Existant : `SecurityConfig`
(chaîne de filtres : CSRF off, stateless, `/actuator/health/**` et `/actuator/info` publics, le reste authentifié, resource server JWT).

## Ce qu'on crée ici

| Fichier | Contenu |
|---|---|
| `SecurityConfig.java` | `SecurityFilterChain`, `@EnableMethodSecurity`, CORS, branchement du `JwtAuthConverter` |
| `CorsProperties.java` | `record` `@ConfigurationProperties("odc.cors")` |
| `<Sujet>Properties.java` | une classe de propriétés par sujet (`odc.mail`, `odc.storage`…) |
| `<Sujet>Config.java` | beans techniques (`Clock`, client HTTP, mail, OpenAPI…) |

## Gabarits

```java
@Configuration
@EnableMethodSecurity                                   // active @PreAuthorize (sinon il est ignoré)
@EnableConfigurationProperties(CorsProperties.class)
@RequiredArgsConstructor
public class SecurityConfig {

	private final JwtAuthConverter jwtAuthConverter;

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
			.csrf(AbstractHttpConfigurer::disable)
			.cors(Customizer.withDefaults())                   // utilise le bean CorsConfigurationSource
			.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(auth -> auth
				.requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
				.anyRequest().authenticated())
			.oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(jwtAuthConverter)));
		return http.build();
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource(CorsProperties props) {
		CorsConfiguration cors = new CorsConfiguration();
		cors.setAllowedOrigins(props.allowedOrigins());       // liste explicite, jamais "*" avec des identifiants
		cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		cors.setAllowedHeaders(List.of("Authorization", "Content-Type"));
		cors.setMaxAge(Duration.ofHours(1));
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/api/**", cors);
		return source;
	}
}

@ConfigurationProperties(prefix = "odc.cors")
public record CorsProperties(List<String> allowedOrigins) {}
```

```yaml
# application-dev.yaml                     # application-prod.yaml
odc:                                       odc:
  cors:                                      cors:
    allowed-origins: http://localhost:3000     allowed-origins: ${ODC_CORS_ALLOWED_ORIGINS}
```

## Règles

- **Une classe = un sujet** (sécurité, CORS, mail…). `SecurityConfig` ≤ 100 lignes : au-delà, extraire.
- **Configuration externe** via `@ConfigurationProperties` (record, préfixe `odc.`), validée si besoin (`@Validated` + contraintes),
  plutôt que des `@Value` dispersés. Une propriété sensible n'a **pas de valeur par défaut** dans `application.yaml` ; en `prod`
  elle vient d'une variable d'environnement (échec au démarrage si absente — c'est voulu).
- **Secrets** : jamais dans le code ni dans un `application*.yaml` versionné ; ajouter la variable à `.env.example` (valeur vide ou factice).
- **Profils** : `application.yaml` = commun ; `application-dev.yaml` / `application-prod.yaml` = surcharges. Un `@Profile` sur un bean
  seulement si le comportement diffère vraiment (ex. serveur de mail de dev).
- **Sécurité** : routes protégées par défaut (`anyRequest().authenticated()`) ; ouvrir explicitement et au plus juste. Chaque
  `permitAll()` ajouté est commenté (pourquoi) et justifié dans `docs/memoire/`. Ordre des `requestMatchers` : du plus spécifique au plus général.
- **Mail** : `spring-boot-starter-mail` ne crée `JavaMailSender` que si `spring.mail.host` est défini. Définir `spring.mail.*` par profil
  (SMTP de dev type Mailpit) avant toute injection de `JavaMailSender`.
- **Horloge** : exposer un bean `Clock` ici si des services dépendent de l'heure (testables avec `Clock.fixed`).
- **Beans** : méthodes `@Bean` package-private, noms explicites, `proxyBeanMethods = false` si la classe n'appelle pas ses propres `@Bean`.
- Flyway et JPA sont configurés par **propriétés** (`spring.flyway.*`, `spring.jpa.*`), pas par du code ici, sauf besoin avéré.

## Interdits

- Logique métier, accès aux repositories, mapping de DTO.
- `@CrossOrigin` sur un contrôleur (tout passe par ce dossier).
- Désactiver la protection de `anyRequest()` ; `csrf` activé sans raison (API stateless + JWT) ou, à l'inverse, `cors` ouvert à `*`.
- `ddl-auto` autre que `validate`.

## Tests

`@WebMvcTest` d'un contrôleur avec `@Import({SecurityConfig.class, JwtAuthConverter.class})` et `@MockitoBean JwtDecoder` : vérifie
401 sans jeton, 403 mauvais rôle, et l'en-tête CORS pour une origine autorisée/refusée. Détail : `src/test/.../CLAUDE.md`.

## Checklist « nouvelle config »

1. Propriétés typées + clé dans `application*.yaml` (dev ET prod). 2. Variable dans `.env.example` si secret/URL.
3. Bean dans une classe dédiée. 4. Test si le comportement est observable (sécurité, CORS). 5. `docs/memoire/ETAT.md` à jour.
