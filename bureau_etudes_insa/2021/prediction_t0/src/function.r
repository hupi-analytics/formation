pretreat = function(articles, commandes, collection){
  
  # ------------------------------------ Calcul de la quantité commandée pour chaque article
  commandes <- commandes %>% filter(Type == "R") %>% mutate(Code.Article = paste(Collection, Code.Article, sep = "-"))
  qte <- aggregate(Quantité~Code.Article, data = commandes, sum)
  
  # ------------------------------------ Pre-traitement de la table articles
  df = articles
  code_to_color <- read.csv2(code_to_color_url, stringsAsFactors=FALSE, encoding="UTF-8")
  fam_to_code_fam = read.csv2(fam_to_code_fam_url, stringsAsFactors=FALSE, encoding="UTF-8")
    
  # Création du code de l'article
  rownames(df) = paste(df$Collection, df$Code.Article, sep = '-')
  df$Code = rownames(df)

  # Gestion de la Collection
  #df$Collection <- df$Collection %>% as.factor
  
  # Gestion de la Catégorie d'age 
  df$Attribut.2 <- as.character(df$Attribut.2)
  df$Attribut.2[df$Attribut.2 %in% c("Baby (M)", "Juvenile Boys", "Toddlers (M)")] = "Kids"
  df$Attribut.2 <- as.factor(df$Attribut.2)
  df <- df %>% rename(Catégorie.Age = Attribut.2)

  # Gestion de la Gamme
  df <- df %>% rename(Gamme = Attribut.1)
  df[is.na(df$Gamme),"Gamme"] <- "other"
  df$Gamme <- df$Gamme %>% as.factor

  # Gestion de la Famille
  row.names(fam_to_code_fam) = fam_to_code_fam$Code.Famille
  df$Code.Famille <- fam_to_code_fam[df$Code.Famille, "Famille"]
  df$Code.Famille[is.na(df$Code.Famille)] <- "other"
  df$Code.Famille = df$Code.Famille %>% as.factor
  df <- df %>% rename(Famille = Code.Famille)

  # Gestion de la Couleur
  rownames(code_to_color) = code_to_color$code
  df$col = lapply(strsplit(as.character(df$Code.Article),"-"), function(x) x[[2]]) %>% unlist %>% substring(., 1, 1)
  df$Couleur <- code_to_color[df$col, "color"] 
  df[is.na(df$Couleur),"Couleur"] <- "other"
  df$Couleur <- df$Couleur %>% as.factor

  # Gestion de Bin Vente Tarif
  df$Bin.vente.tarif <- cut(df$Prix.vente.tarif, 
                            breaks = c(head(quantile(df$Prix.vente.tarif), -1), Inf),
                            include.lowest = T)
  
  
  # Gestion de Trimestre.Article
  df$Date.Début.expédition <- as.Date(df$Date.Début.expédition, "%d/%m/%Y")
  df$Trimestre.création <- quarter(df$Date.Début.expédition, with_year = F) %>% as.factor

  # Sélection des colonnes
  df <- df %>% select(Libellé, Collection, 
                      Couleur, Catégorie.Age, Gamme, Famille, 
                      Bin.vente.tarif, Trimestre.création, Code)
  
  df$Code.Article = lapply(strsplit(rownames(df), '-'), function(k) k[2]) %>% unlist

  df <- na.omit(df)

  # ------------------------------------ Separation learn et pred
  learn = filter(df, Collection %in% collection$learn)
  pred = filter(df, Collection %in% collection$pred)
  
  rownames(learn) = learn$Code
  rownames(pred) = pred$Code

  # ------------------------------------ Ajout des quantités pour learn
  # Ajout des Quantités
  learn$Quantité = 0
  learn[qte$Code.Article, "Quantité"] = qte$Quantité
  
  # Ajout des articles de référence
  # ref 1 : articles avec le même code
  ref1 = function(art){
    res = filter(learn, Code.Article == art$Code.Article & Collection < art$Collection)
    if (nrow(res) == 0){
      return(0)
    }else{
      return(mean(res$Quantité))
    }
  }
  # ref 2 : articles avec les mêmes caractéristiques
  ref2 = function(art){
    res = filter(learn, Couleur == art$Couleur & Famille == art$Famille & Catégorie.Age == art$Catégorie.Age & Collection < art$Collection)
    if (nrow(res) == 0){
      return(0)
    }else{
      return(mean(res$Quantité))
    }
  }
  
  learn$Quant.Ref.1 = sapply(1:nrow(learn), function(k) ref1(learn[k,]))
  learn$Quant.Ref.2 = sapply(1:nrow(learn), function(k) ref2(learn[k,]))
  
  pred$Quant.Ref.1 = sapply(1:nrow(pred), function(k) ref1(learn[k,]))
  pred$Quant.Ref.2 = sapply(1:nrow(pred), function(k) ref2(learn[k,]))
  
  #Ajout des Bin Quantité
  learn$Bin.Quantité = ifelse(learn$Quantité == 0, "[0]", "(0, Inf[") %>% as.factor
  
  learn <- learn %>% select(-Code.Article, -Code)
  pred <- pred %>% select(-Code.Article, -Code)
  
  return(list(learn = learn,
              pred = pred
    )
  )
}

