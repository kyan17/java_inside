# Java inside
# TP2

Mocking = Autre implémentation d'une API pour pouvoir faire les tests  
Early checking = Vérifier si la configuration est bonne avant de démarrer l'exécution (à la compilation c'est le mieux)  
Late checking = Vérifier si la configuration est bonne après avoir démarré l'exécution (utilisé par les frameworks)  
Historiquement les configs étaient dans des fichiers XML  
Cependant quand on change le nom du Service ou d'une classe importante, ça ne modifie pas le fichier XML :(  
Maintenant on utilise des outils de refactoring pour changer le nom partout en Java avec des annotations  
Le registry permet de lier les classes entre elles, on disant que cette classe implémente cette interface, etc.  
Le registry permet de faire de l'injection de dépendance, c'est-à-dire que l'on peut dire que telle classe dépend d'une autre classe  
L'annotation courante est `@Inject` et on l'utilise au dessus des constructeurs (historiquement au dessus des setters)  
Seulement un constructeur peut être annoté avec `@Inject`  

## Interface vs Classe dans les champs

On met Map<K,V> comme type statique si le champ est public  
Si le champ est privé, on met directement la classe et pas l'interface, donc HashMap<K,V>  

