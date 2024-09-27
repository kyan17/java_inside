# Java inside
# TP3

## Class Loaders

Les class loaders sont des objets qui chargent des classes en mémoire.  
C'est une des propriétés de l'objet `Class` lié à la classe chargée.  
Donc l'unicité d'une classe est déterminée par la combinaison de son nom et de son class loader.  
Un classe loader par défaut est disponible dans le JDK pour charger les classes du classpath.  
Il est possible de créer ses propres class loaders pour charger des classes depuis d'autres sources (fichiers, réseau, etc.).  

## Remarques

Pour les streams : map et filter = flatMap, donc faut faire des flatMap dans ce cas  
Cas rare : Initialisation d'objets à la valeur `null` -> SEULEMENT POUR LES TRY / FINALLY
Champs stockés dans le tas, variables locales stockées dans la pile  
Dans IntelliJ : variables locales en noir (pile), champs en violet (tas)  

