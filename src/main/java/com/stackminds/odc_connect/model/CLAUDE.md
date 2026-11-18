# CLAUDE.md — `model/` (entités JPA)

> Instruction de projet : **lecture seule pour les agents IA**. Si elle semble fausse, le dire à l'utilisateur.
> Complète `docs/instruction/architecture-couches-spring.md` et `nomenclature-java.md` ; en cas de conflit, ces deux fichiers gagnent.

## Rôle

Représenter les données persistées. **Rien d'autre** : pas de HTTP, pas de DTO, pas de service, pas d'appel au repository.
Dépend seulement de Jakarta Persistence, de Hibernate (annotations) et de Lombok.

## Ce qu'on crée ici

| Fichier | Contenu |
|---|---|
| `<Entite>.java` | une `@Entity` (nom au singulier, **sans** suffixe `Entity`) |
| `enums/<Nom>.java` | enum métier persisté (`MemberStatus`) |
| `<Valeur>.java` | `@Embeddable` pour un groupe de colonnes réutilisé (ex. `Address`) |

Un fichier = un type. Pas de classe utilitaire ni de constantes diverses ici.

## Gabarit d'entité

```java
@Entity
@Table(name = "members")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // exigé par JPA, interdit au code applicatif
public class Member {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;                                   // interne : jamais exposé

	@Column(name = "public_id", nullable = false, updatable = false, unique = true)
	private UUID publicId;                             // identifiant externe, fixé à la construction

	@Column(nullable = false, length = 254)
	private String email;

	@Column(name = "display_name", nullable = false, length = 120)
	private String displayName;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private MemberStatus status;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	public Member(String email, String displayName) {
		this.publicId = UUID.randomUUID();
		this.email = email;
		this.displayName = displayName;
		this.status = MemberStatus.PENDING;
	}

	// Comportements nommés plutôt que des setters : l'entité garde ses invariants.
	public void rename(String displayName) {
		this.displayName = displayName;
	}

	public void changeStatus(MemberStatus status) {
		this.status = status;
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof Member other && publicId.equals(other.getPublicId());
	}

	@Override
	public int hashCode() {
		return publicId.hashCode();
	}
}
```

## Règles

**Identité**
- `id` : `Long`, `GenerationType.IDENTITY`, colonne `BIGINT GENERATED ALWAYS AS IDENTITY`. Jamais dans un DTO.
- `publicId` : `UUID`, généré **dans le constructeur** (donc stable avant l'insertion), `unique`, `updatable = false`.
- `equals`/`hashCode` sur `publicId` uniquement, via les getters (compatible avec les proxys Hibernate). Jamais sur `id`
  (nul avant `persist`) ni sur tous les champs.

**Lombok**
- Autorisé : `@Getter`, `@NoArgsConstructor(access = PROTECTED)`.
- **Interdit** : `@Data`, `@Setter` de classe, `@EqualsAndHashCode`, `@ToString` (relations lazy → requêtes parasites / boucles),
  `@Builder` sur une entité à champs générés.

**Mutation**
- Pas de setters publics. Modifier l'état par des méthodes qui portent le sens métier (`rename`, `changeStatus`, `assignTo`).
- Une entité vérifie ses invariants **locaux** (champ non nul, valeur cohérente). Les règles qui font intervenir d'autres entités
  ou la base (unicité, transitions autorisées) vivent dans `service/` et `service/policy/`.

**Colonnes et types**
- `@Table(name = "...")` et `@Column(name = "...")` **explicites** pour tout nom composé (`snake_case`). Le schéma appartient à
  Flyway ; `ddl-auto: validate` compare longueurs, nullabilité et types : ils doivent coïncider avec la migration.
- Types : `String`→`VARCHAR(n)` (toujours `length`), `UUID`→`UUID`, `Instant`→`TIMESTAMPTZ`, `LocalDate`→`DATE`,
  `BigDecimal`→`NUMERIC(p,s)` (jamais `double` pour de l'argent), `boolean`→`BOOLEAN`.
- Dates techniques en `Instant` (UTC) avec `@CreationTimestamp` / `@UpdateTimestamp` (Hibernate) : pas de configuration d'audit.
- Enum : toujours `@Enumerated(EnumType.STRING)` (jamais `ORDINAL`). Ajouter une `CHECK` dans la migration si l'ensemble est fermé.

**Relations**
- `@ManyToOne(fetch = FetchType.LAZY)` partout (le défaut `EAGER` de `@ManyToOne` est interdit). `@OneToMany` seulement si le
  côté « un » a vraiment besoin de naviguer : préférer une requête du repository.
- Une seule direction par défaut. Bidirectionnel : `mappedBy` côté « un », méthode `add…/remove…` qui synchronise les deux côtés.
- `cascade = ALL` / `orphanRemoval = true` uniquement pour un **composant** (cycle de vie dépendant du parent, ex. lignes d'une
  commande). Jamais `CascadeType.REMOVE` entre entités indépendantes.
- Collections initialisées (`new ArrayList<>()`), jamais exposées modifiables (`Collections.unmodifiableList` dans le getter).
- Clé étrangère : colonne `<entité>_id` (valeur de l'`id` interne), contrainte nommée dans la migration.

**Concurrence** : ajouter `@Version private long version;` (colonne `version BIGINT NOT NULL DEFAULT 0`) sur toute entité modifiable
par plusieurs utilisateurs ; une `OptimisticLockingFailureException` est traduite en 409 par `GlobalExceptionHandler`.

**Enums (`enums/`)** : valeurs en `UPPER_SNAKE_CASE`, jamais renommées ni réordonnées sans migration de données (les valeurs
sont écrites en base). Pas de logique qui dépend de HTTP ; une méthode d'aide pure est tolérée.

## Interdits

- `@Data`, `@ToString`, `EAGER`, `EnumType.ORDINAL`, `double`/`float` pour de l'argent.
- Import de `dto`, `service`, `controller`, `mapper`, `repository`, ou d'un type Spring Web.
- Setter public sur `publicId`, `id`, `createdAt`.
- Entité renvoyée par un contrôleur ou stockée dans un DTO.
- Modifier une entité **sans** la migration correspondante dans le même commit (le démarrage échoue).

## Checklist « nouvelle entité »

1. Migration `V<n>__create_<table>.sql` (voir `src/main/resources/db/CLAUDE.md`) — d'abord.
2. Entité + enums, noms de colonnes alignés sur la migration.
3. Repository (`repository/CLAUDE.md`), puis DTO, mapper, service, contrôleur.
4. Test d'intégration repository qui prouve que le schéma Flyway et l'entité concordent.
