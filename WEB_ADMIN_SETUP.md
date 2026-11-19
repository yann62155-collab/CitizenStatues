# CitizenStatues — panneau web

## Installation

1. Installer le JAR CitizenStatues sur le serveur Minecraft (NeoForge 1.21.1 / Java 21), puis redémarrer.
2. Dans BoxToPlay : **Plugins & Mods → Gestion des ports → Réserver un nouveau port**.
3. Le port Minecraft actuel est `30033`. Le panneau web doit utiliser **un autre port réservé**.
4. Au premier démarrage, le mod crée `config/citizenstatues-web.properties`.
5. Remplacer la valeur `port=30034` par le port supplémentaire réservé dans BoxToPlay, enregistrer, puis redémarrer.
6. Ouvrir `http://mc312.boxtoplay.com:PORT_RÉSERVÉ`.
7. Lire la valeur `accessKey` dans `config/citizenstatues-web.properties` et la saisir sur l’écran de connexion. Ne jamais partager cette clé.

Exemple de configuration :

```properties
enabled=true
bind=0.0.0.0
port=30034
accessKey=clé générée automatiquement
```

## Fonctionnalités

- Créer une statue à l’apparition principale du monde.
- Renommer, activer, désactiver, déplacer et supprimer une statue.
- Créer et renommer des dossiers.
- Placer chaque statue dans un seul dossier ou la laisser indépendante.
- Les statues d’un même dossier partagent leurs destinations. Lorsqu’une destination est ajoutée, modifiée ou retirée sur une statue du groupe, la modification est propagée au groupe.
- Les groupes et leurs membres sont sauvegardés dans `data/citizen_statues.json`.

## Sécurité réseau

Cette version écoute en **HTTP simple**. La clé d’accès protège les opérations, mais HTTP ne chiffre pas le trafic. Pour une exposition sur Internet, placer le panneau derrière un proxy HTTPS (TLS) avant d’y saisir la clé. Ne pas réutiliser cette clé sur d’autres services.

Le panneau s’exécute uniquement sur un serveur dédié. Il ne démarre pas en partie solo.
