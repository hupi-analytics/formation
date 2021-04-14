
# Script final

# Importation des library :

library(mongolite)

# Importation des fonctions et constantes

source( file = "./src/constante.r")
source( file = "./src/function.r")

########### Chargement des donnees #################

uri <- paste0(path_hdfs, "?op=OPEN")
data = read.csv(uri)

########### Entrainement le modèle #################

irisCluster = kmeans(data[,1:4], center=3)

# current time
created_at = rep(Sys.time(), times = length(irisCluster$cluster))

# mettre tous dans meme dataframe
res_cluster = cbind(data, cluster = irisCluster$cluster, created_at)

########### ecrire résultats dans MongoDB #################

mongo_url = paste0("mongodb://", customer, ":", password, "@", mongo_host)

# On ecrit d'abord une collection de test dans MongoDB et puis update cette collection
mongo <- mongo(collection = collection_sortie, db = customer, url = mongo_url, verbose = F)

# drop la collection
mongo$drop()

# inserer dans la collection
mongo$insert(res_cluster)
