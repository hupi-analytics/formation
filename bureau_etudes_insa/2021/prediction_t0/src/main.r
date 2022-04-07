## ------------------- Importation des packages
cat("-----------------------------\n")
cat("Importation des packages (1/6)\n\n")
silent = function(x) {suppressWarnings(suppressMessages(x))}
silent(library(lubridate, quietly=TRUE))
silent(library(dplyr, quietly=TRUE))
silent(library(stringr, quietly=TRUE))
silent(library(randomForest, quietly=TRUE))
silent(library(mongolite))

## ------------------- Importation des fonctions/variables d'environnement
source("./src/constante.R", encoding="UTF-8")
source("./src/function.R", encoding="UTF-8")

## ------------------- Importation des tables
cat("-----------------------------\n")
cat("Importation des tables (2/6)\n\n")
collections = read.table(collection_url, header = T, sep =";", comment.char = "", quote = "", dec = ',', fileEncoding = "UTF-8", stringsAsFactors = T)
articles = read.table(article_url, header = T, sep =";", comment.char = "", quote = "", dec = ',', fileEncoding = "UTF-8", stringsAsFactors = T)
commandes = read.table(commande_url, header = T, sep =";", comment.char = "", quote = "", dec = ',', fileEncoding = "UTF-8", stringsAsFactors = T)

## ------------------- Repérage des collections à prédire
ind = which(collections[3]=="")
collection = list(learn = collections$Collection[ind],
                  pred = collections$Collection[-ind])

## ------------------- Importation des tables
cat("-----------------------------\n")
cat("Prétraitement des données (3/6)\n\n")
article = pretreat(articles, commandes, collection)

## ------------------- Premier modele : classification entre les articles achetes ou non
cat("-----------------------------\n")
cat("Entrainement du modèle de classification (4/6)\n\n")
class.mod <- randomForest(Bin.Quantité ~. , data = select(article$learn,-Quantité, -Libellé, -Collection),n_try=0.1)
# Recupération des prédictions et des scores  
article$pred$Quantité = predict(class.mod, article$pred, type = "response")
article$pred$Quantité = ifelse(article$pred$Quantité == "[0]", 0, 1)
article$pred$Score = apply(predict(class.mod, article$pred, type = "prob"), 1, max)

## ------------------- Deuxieme modele : regression sur les articles achetes
cat("-----------------------------\n")
cat("Entrainement du modèle de régression (5/6)\n\n")
learn2 = article$learn %>% filter(Quantité > 0) %>% select(-Libellé, -Collection, -Bin.Quantité) 
pred2 = article$pred %>% filter(Quantité == 1)

reg.mod = randomForest(Quantité ~ ., data = learn2)
pred2$Quantité = predict(reg.mod, pred2)
pred2$Score = pred2$Score * mean(reg.mod$rsq)

# Recupération des prédictions et des scores  
article$pred[rownames(pred2), "Quantité"] = pred2$Quantité
article$pred[rownames(pred2), "Score"] = pred2$Score

## ------------------- Ecriture des resultats dans MongoDB
cat("-----------------------------\n")
cat("Ecriture de la table (6/6)\n\n")
res <- article$pred %>% select(-Trimestre.création, -Quant.Ref.1, -Quant.Ref.2)

mongo_url = paste0("mongodb://", customer, ":", password, "@", mongo_host)
mongo <- mongo(collection = "prediction", db = customer, url = mongo_url, verbose = F)
mongo$drop()
mongo$insert(res)