# CLAUDE.md — `repository/` (accès aux données)

> Instruction de projet : **lecture seule pour les agents IA**. Complète `docs/instruction/architecture-couches-spring.md`.

## Rôle

Persistance seule : lire et écrire des **entités** via Spring Data JPA. Aucune règle métier, aucune transaction, aucun DTO.
Dépend uniquement de `model/`.

## Ce qu'on crée ici

| Fichier | Contenu |
|---|---|
| `<Entite>Repository.java` | interface `extends JpaRepository<Entite, Long>` — une par entité racine |
| `projection/<Nom>Summary.java` | (rare) `record`/interface de lecture partielle pour une liste lourde |

Pas d'implémentation `…Impl` tant qu'une requête dynamique complexe ne l'impose pas (alors `Specification`, voir ci-dessous).

## Gabarit

```java
public interface MemberRepository extends JpaRepository<Member, Long> {

	Optional<Member> findByPublicId(UUID publicId);

	boolean existsByEmailIgnoreCase(String email);

	Page<Member> findAllByStatus(MemberStatus status, Pageable pageable);

	@Query("""
		select m from Member m
		where lower(m.displayName) like lower(concat('%', :term, '%'))
		""")
	Page<Member> search(@Param("term") String term, Pageable pageable);

	@Modifying(clearAutomatically = true)
	@Query("update Member m set m.status = :status where m.id in :ids")
	int updateStatus(@Param("ids") Collection<Long> ids, @Param("status") MemberStatus status);
}
```

## Règles

- **Clé générique** : `JpaRepository<Entité, Long>` (l'`id` interne). La recherche externe se fait par `findByPublicId`.
- **Retours** : `Optional<T>` pour 0..1, `List<T>`/`Page<T>` pour 0..n (jamais `null`). `Page` avec `Pageable` dès qu'une liste
  peut grandir ; `List` seulement pour des ensembles bornés par nature (référentiels).
- **Requêtes dérivées** (`findBy…`) : jusqu'à 2-3 critères et un nom lisible. Au-delà : `@Query` JPQL avec un nom de méthode court.
  **Paramètres nommés** (`@Param`) et text blocks ; jamais de concaténation de chaîne (injection). SQL natif seulement si JPQL
  ne suffit pas, et alors commenté.
- **Pas de `@Transactional`** ici (c'est le service qui ouvre la transaction). Exception : un `@Modifying` appelé hors service est
  interdit ; il passe toujours par un service.
- `@Modifying` : `clearAutomatically = true` pour ne pas garder des entités périmées dans le contexte de persistance.
- **N+1** : pour les relations lazy lues par le mapper, `@EntityGraph(attributePaths = {"roles"})` ou `join fetch`. Attention :
  `join fetch` d'une collection + `Pageable` charge tout en mémoire (avertissement Hibernate HHH90003004) → faire deux requêtes
  (page d'ids, puis `findAllById` avec graphe) ou une projection.
- **Tri client** : un `sort=` arbitraire permet d'ordonner sur n'importe quelle propriété. Le contrôleur/service ne laisse passer
  qu'une liste blanche (voir `controller/CLAUDE.md`).
- **Requêtes dynamiques** (filtres facultatifs) : `JpaSpecificationExecutor<T>` + classe `<Entite>Specifications` (méthodes
  statiques `Specification<T>`), dans ce dossier. Pas de `Criteria` éparpillé dans les services.
- **Projections** : pour les lectures lourdes, `record <Nom>Summary(...)` (sous `repository/projection/`) retourné par
  `select new …` ou projection d'interface. Elles ne sortent pas de l'application : le mapper les convertit en `*Response`.
- Pas de `default` method contenant de la logique métier ; pas de dépendance à un autre repository.

## Interdits

- `@Transactional`, `@Service`, import de `dto`/`service`/`controller`/`exception`.
- `findAll()` non paginé sur une table qui grossit ; `deleteAll()`/`deleteAllInBatch()` hors tests.
- Requête native avec valeurs concaténées ; `@Query` sans paramètre nommé.
- Renvoyer ou accepter un DTO.

## Tests (obligatoires pour toute requête non triviale)

`repository/<Entite>RepositoryTest` — `@DataJpaTest` + `@Import(TestcontainersConfiguration.class)` + base PostgreSQL réelle,
`@Tag("integration")`. Vérifier : la requête personnalisée, les contraintes (unicité, FK : l'exception attendue est levée),
et que le schéma Flyway concorde avec l'entité. Détail : `src/test/.../CLAUDE.md`. Jamais H2.

## Checklist « nouveau repository »

1. L'entité et sa migration existent. 2. Interface + méthodes nécessaires **maintenant** (YAGNI). 3. Test d'intégration.
