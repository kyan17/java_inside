# Java inside
# TP4

## Object Relational Mapping (ORM)
Un ORM est une API qui va modéliser des objets Java comme des tables en base de données SQL.  
C'est le pont reliant les objets relationnels et les instances d'objets Java.  
ORM le plus utilisé aujourd'hui en Java -> Hibernate.  
Les ORM vont utiliser un driver JDBC pour se connecter et communiquer avec une base de données.  
Hibernate possède un dialect est une implémentation de la Java Persistence API (JPA).  
Il ne faut pas confondre l'API de persistence et celle des requêtes (query).  
Pour faire des query on passe soit par le JPA, soit par un mapping repository.  

Schéma de communication :  
BDD physique <- JDBC Driver <- Datasource <- Dialect <- JPA  

Procédure d'hibernate (et d'un ORM en général) :
1 - Transaction  
2 - Création de la table à partir de la classe Java concernée (à partir de la définition du Bean)  
3 - Insertion des données dans la BDD  
4 - Exécution de la requête  
5 - Récupération des résultats sous forme de liste d'instances de Java Bean

## Transactions
Requête de lecture ou d'écriture sur une base de données. Rien à voir avec une transaction financière.  
Lorsque 2 mêmes transactions sur une même donnée sont effectuées en même temps,
l'une réussie et l'autre échoue (rollback).  
Il y a différents types de transactions (read-only, force write, ...).  
Il n'y pas de type de transaction par défaut, chaque BDD a son default transaction.
Remarque : pour debugger plus facilement les données importantes, il faut les stocker dans des variables locales.  
En effet le debugger pourra ensuite les afficher en cas de problème, car elle seront stockées dans la pile ou le tas.  
On pourra également les logger plus facilement.

## Datasource
Rôle principal d'une base de données -> Gérer la concurrence.  
Le datasource est l'objet utilisé pour accéder à la BDD (login, password, IP address, port, ...).  
Dans les BDD, les Id primary key sont toujours des entiers longs !  
Exemple de datasource :  
```java
var dataSource = new org.h2.jdbcx.JdbcDataSource();
// Configuration d'une BDD temporaire en mémoire pour les tests
dataSource.setURL("jdbc:h2:mem:test");
ORM.transaction(dataSource, () -> {
    // Start transaction
    . . .
    // End transaction
});
```

## Dialect
Le dialecte JPA est une configuration qui spécifie le type de base de données utilisée dans une application.  
Il permet à JPA de générer le SQL approprié pour interagir avec cette base de données spécifique.  
Chaque BDD a ses propres particularités et syntaxe SQL (MySQL, Postgres, ...), et le dialecte aide JPA à s'adapter à ces différences.

### Utilité du dialecte JPA
- Génération de SQL : Le dialecte permet à JPA de générer des requêtes SQL adaptées à la base de données utilisée.
- Compatibilité : Assure que les fonctionnalités spécifiques à BDD (comme les types de données, les fonctions, etc.) sont correctement gérées.
- Optimisation : Permet d'optimiser les requêtes pour tirer parti des capacités spécifiques de la base de données.

## Rappel d'une Java Bean
Une Java Bean est une classe qui respecte les conventions suivantes :
- Un constructeur sans paramètre
- Des méthodes d'accès (getters et setters) pour chaque attribut
- Classe mutable donc pas de record
- Méthodes hashCode(), equals() et toString() redéfinies
```java
class Country {

  private Long id;
  private String name;

  public Country() {}
  public Country(String name) {
    this.name = Objects.requireNonNull(name);
  }

  @Id
  @GeneratedValue
  public Long getId() {
    return id;
  }
  public void setId(Long id) {
    this.id = Objects.requireNonNull(id);
  }

  public String getName() {
    return name;
  }
  public void setName(String name) {
    this.name = Objects.requireNonNull(name);
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof Country country
      && Objects.equals(id, country.id)
      && Objects.equals(name, country.name);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, name);
  }

  @Override
  public String toString() {
    return "Country { id=" + id + ", name='" + name + "'}";
  }

}
```

## Threads locales
Un thread local est une variable qui est propre à chaque thread.  
On va associer à un objet son propre thread local. C'est un cheatcode pour éviter les variables globales, tout en restant mutable.  
Cela permet de stocker des informations mutables sans rencontrer des problèmes de concurrence.  
Cependant les autres threads ne peuvent pas accéder à ces variables, seul le thread local (this) est concerné.  
```java
private static final ThreadLocal<Data> DATA_THREAD_LOCAL = new ThreadLocal<>();

void method() {
  Data data = new Data();
  DATA_THREAD_LOCAL.set(data);
  try {
    // Do something with data
  } finally {
    DATA_THREAD_LOCAL.remove();
  }
}
```

## Rappels sur les exceptions
Les exceptions en Java se divisent en deux catégories principales : les exceptions checkées et les exceptions non checkées.  

### Exceptions Checkées (Vérifiées)
Les exceptions checkées sont des exceptions qui doivent être déclarées dans la signature de la méthode ou capturées dans un bloc `try-catch`.  
Quand elles sont déclarées dans la signature de la méthode, elles doivent être gérées par le code appelant (elles sont propagées).  
Quand on fait du développement API, on propage les exceptions checkées dans la plupart des cas.  
C'est lorsqu'on développe un appli utilisant notre API qu'on va attraper (catch) les exceptions checkées.  
Elles sont vérifiées au moment de la compilation. Les exceptions checkées héritent de la classe `Exception`, mais pas de `RuntimeException`.
```java
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;

public class CheckedExceptionExample {
  public static void main(String[] args) {
    try {
      File file = new File("nonexistentfile.txt");
      FileReader fr = new FileReader(file);
    } catch (FileNotFoundException e) {
      System.out.println("File not found: " + e.getMessage());
    }
  }
}
```

### Exceptions Non Checkées (Non Vérifiées)
Les exceptions non checkées ne sont pas vérifiées au moment de la compilation. Elles héritent de la classe `RuntimeException`. Ces exceptions peuvent survenir à tout moment pendant l'exécution du programme et ne nécessitent pas de déclaration explicite.
```java
public class UncheckedExceptionExample {
  public static void main(String[] args) {
    try {
      int[] numbers = {1, 2, 3};
      System.out.println(numbers[5]); // This will throw ArrayIndexOutOfBoundsException
    } catch (ArrayIndexOutOfBoundsException e) {
      System.out.println("Array index out of bounds: " + e.getMessage());
    }
  }
}
```
