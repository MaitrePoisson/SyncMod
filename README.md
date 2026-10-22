<p align="center">
  <img src="src/main/resources/logo.png" alt="Logo SyncMod" width="160">
</p>

# SyncMod

Mod NeoForge 1.21.1 (Java 21) qui synchronise le dossier `mods/` d'un joueur avec celui d'un serveur dédié.

Le joueur installe le mod une seule fois. Ensuite, quand il rejoint le serveur, SyncMod détecte les mods manquants ou obsolètes, les télécharge depuis le serveur et demande un redémarrage du jeu.

## Fonctionnement

**Côté serveur**

1. Au démarrage, le mod scanne le dossier `mods/` du serveur et calcule le SHA-256 de chaque jar. Les bibliothèques de langage (KotlinForForge, par exemple) sont incluses.
2. Il répond aux requêtes HTTP directement sur le port du jeu : aucun port supplémentaire à ouvrir.
3. Il expose un manifeste (`/manifest.json`) et les jars (`/mods/<fichier>`), rien d'autre.

**Côté client**

1. Quand NeoForge refuse la connexion parce qu'il manque des mods, SyncMod récupère le manifeste du serveur en HTTP.
2. Il compare avec le dossier `mods/` local et affiche un écran de confirmation avec la liste des fichiers.
3. Après confirmation, il télécharge les fichiers dans `mods/.sync_temp/`, vérifie leur SHA-256, les installe et supprime les anciennes versions.
4. Un écran bloquant demande de redémarrer Minecraft.

Si le serveur n'a pas SyncMod, ou si la synchronisation est impossible, l'écran d'erreur affiche la raison et un bouton pour revenir aux détails NeoForge.

## Installation

1. Placez `syncmod-1.0.0.jar` dans le dossier `mods/` du **serveur** et de chaque **client**.
2. Démarrez le serveur. Son log doit contenir :
   `Synchronisation des mods: fichiers servis sur le port du jeu (<port>)`
3. Le joueur rejoint le serveur **depuis la liste Multijoueur** (ou la connexion directe). Un lancement rapide depuis un launcher ne permet pas au mod de connaître l'adresse du serveur.

Le mod doit être installé à la main une première fois chez chaque joueur : sans lui, le client ne sait pas interpréter la réponse du serveur.

## Configuration

### Serveur : `config/syncmod-common.toml`

| Clé | Défaut | Rôle |
|---|---|---|
| `enabled` | `true` | Active la synchronisation |
| `separatePort` | `0` | `0` partage le port du jeu. Une autre valeur ouvre un serveur HTTP sur ce port TCP (à ouvrir dans le pare-feu) |
| `publicHost` | `""` | Hôte à utiliser pour les téléchargements, si différent de l'adresse de connexion du joueur |
| `excludedFiles` | `[]` | Noms de jars du `mods/` serveur à ne pas envoyer aux clients (mods réservés au serveur) |

### Client : `config/syncmod-client.toml`

| Clé | Défaut | Rôle |
|---|---|---|
| `allowedDomains` | Modrinth, CurseForge, GitHub | Hôtes HTTPS autorisés pour les téléchargements hors serveur |
| `maxFileBytes` | 256 Mo | Taille maximale d'un fichier téléchargé |

## Sécurité

- Le client ne télécharge que depuis l'hôte du serveur auquel il se connecte, ou depuis un domaine HTTPS de `allowedDomains`.
- Les redirections sont suivies à la main et chaque adresse est revalidée.
- Les noms de fichiers sont restreints (`[A-Za-z0-9._+-]+.jar`) et toute écriture est limitée au dossier `mods/`.
- Chaque fichier est vérifié par SHA-256 avant installation, et sa taille est plafonnée.
- Le joueur confirme toujours avant le téléchargement.
- Le serveur ne sert que les jars du manifeste, par nom exact.

Les fichiers transitent en HTTP clair sur le port du jeu. Leur intégrité repose sur le hash du manifeste, issu du même serveur : rejoindre un serveur revient à lui faire confiance pour les mods qu'il vous demande d'installer.

## Limites

- Seuls les jars présents dans le `mods/` du serveur sont envoyés. Un mod uniquement côté client, ou une bibliothèque qu'il exige et que le serveur n'a pas, n'est pas fourni.
- Derrière un proxy ou un tunnel qui ne relaie que le protocole Minecraft (certains services anti-DDoS), le HTTP sur le port du jeu ne passe pas. Utilisez alors `separatePort`.
- Le redémarrage automatique n'est possible que si la JVM expose sa ligne de commande, ce qui est rare sous Windows. Sinon, le bouton ferme le jeu et le joueur le relance à la main.
- Sous Windows, un jar chargé ne peut pas être supprimé ou remplacé pendant que le jeu tourne. Un petit script, lancé à la fermeture du jeu, termine ces opérations.
- Le serveur ne peut pas vérifier l'état réel du client : il ne s'agit pas d'une protection contre un client modifié.

## Dépannage

- **Le log serveur dit « sur le port 25566 »** : l'ancien jar tourne encore sur le serveur.
- **Écran « Synchronisation impossible »** : il indique les adresses testées et l'erreur. Vérifiez que le serveur a bien SyncMod et que son log contient la ligne ci-dessus.
- **« Adresse du serveur inconnue »** : rejoignez le serveur depuis la liste Multijoueur.
- **Le jeu ne démarre plus après une synchronisation** : une dépendance manque côté client. Supprimez les jars du `mods/` du client sauf SyncMod, puis rejoignez le serveur pour tout resynchroniser.

## Compiler depuis les sources

Prérequis : JDK 21 et Gradle.

```
gradle build
```

Le jar est produit dans `build/libs/syncmod-1.0.0.jar`.

Pour lancer l'environnement de test :

```
gradle runServer
gradle runClient
```

`runServer` demande d'accepter l'EULA de Minecraft dans `run/server/eula.txt`.

## Structure du projet

```
com.syncmod
├── SyncMod                  point d'entrée
├── config                   configuration client et serveur
├── manifest                 manifeste et validation
├── network                  paquets et handshake (phase de configuration)
├── security                 whitelist de domaines, garde de chemins
├── server                   scan du dossier mods, partage du port, serveur HTTP
├── client                   événements d'écran, état de synchronisation
├── client.sync              plan, téléchargement, installation, redémarrage
└── client.screen            écran de synchronisation
```
