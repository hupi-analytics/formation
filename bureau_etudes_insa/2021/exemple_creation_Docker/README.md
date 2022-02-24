# Exemple de création Docker
## Objectif du repo 
L'objectif de ce repo est de créer un Docker à partir d'un script R qui prend les données de la base iris.txt dans HDFS, fait un modèle KMeans et écrit les résultats finaux dans MongoDB.
## Structure
```
exemple_creation_Docker
|
|___src
|    |
|    |__constante.r             # Script contenant les constantes du modèle
|    |
|    |__function.r              # Script contenant les fonctions qui sont utilisées par le script principal
|    |
|    |__main.r                  # Script principal
|
│___Dockerfile                  # Fichier permettant de construire une image Docker
|
│___env.file                    # Variables d'environnements
|
│___environment.yml             # Environnement + dépendances conda
|
│___README.md                   # Description pour l'utilisation du repo
|
│___start.sh                    # Script de lancement du programme
       
```

## Utilisation des constantes
Dans ce repo, il y a 1 constante que nous pouvons modifier (sans compter les variables d'environnement) qui correspond à la collection mongo de sortie.
``` 
    - collection_sortie = Collection où nous allons écrire la matrice de test à la fin du script.
```


## Environnement
### Environnement conda
Le docker nécessite d'être lancé dans un environnement conda activé. Cet environnement est défini par le fichier `environment.yml`.
L'environnement conda et toutes les dépendances sont automatiquement chargées au démarrage du container. 
### Variables d'environnement
Cette application nécessite plusieurs variables d'environnement définies dans le fichier `env.file` afin d'avoir accès aux bases MongoDb et chemin d'accès au input dans HDFS.
## Dockerfile
Voici le  fichier `Dockerfile`, il est générique et peut être reproduit pour d'autres projets :

```
Docker
## Generic part for all Hupi project (Python & R)
# Get conda
FROM continuumio/miniconda3
RUN apt-get update --fix-missing && \
    apt-get install -y ntp && \
    apt-get clean

# Create project conda environment
ADD environment.yml /tmp/environment.yml
RUN conda env create -f /tmp/environment.yml
# Pull the environment name out of the environment.yml
RUN echo "source activate $(head -1 /tmp/environment.yml | cut -d' ' -f2)" > ~/.bashrc
ENV PATH /opt/conda/envs/$(head -1 /tmp/environment.yml | cut -d' ' -f2)/bin:$PATH

RUN mkdir /usr/local/workdir
WORKDIR /usr/local/workdir
```

**Note** : Cette méthode fonctionne également avec des application fonctionnant avec le langage `Python`.
## Lancement de l'application
### Build de l'image
On se met à la racine du repo et on lance la commande suivante
```bash
$ docker build -t <nom_image> .
```
### Lancement du programme
Toujours à la racine du repo, on lance le programme via la commande
```bash
$ docker run --rm --name <nom_container> -v "/chemin/vers/le/repo/wik_222_modele_cluster_options_veh:/usr/local/workdir" --env-file /chemin/vers/le/repo/wik_222_modele_cluster_options_veh/env.file <nom_image> /bin/bash start.sh rscript ./src/main.r
```

### Commande Docker 
Voici la commande Docker à exécuter afin que le script se lance :
En local sur mon ordinateur :
```
 docker run --rm --name test_wiki -v "C:\Users\gauth\Desktop\Wikicampers\wik_222_modele_cluster_options_veh:/usr/local/workdir" --env-file ./env.file registry.hupi.io/hupi/wik_222_cluster_vehicules:0.8 /bin/bash start.sh Rscript ./src/main.r
```
 Sur le serveur :
```
  docker -H localhost run --rm --name test_wiki -v "/home/wikicampers2/batch/wikicampers2/wik_222_modele_cluster_options_veh:/usr/local/workdir" --env-file /home/wikicampers2/batch/wikicampers2/wik_222_modele_cluster_options_veh/env.file registry.hupi.io/hupi/wik_222_cluster_vehicules:0.8 /bin/bash start.sh Rscript ./src/main.r
```
