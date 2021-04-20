
# Founction qui retourne le nombre d'elements different d'un vecteur
count = function(vecteur){
    return( length( unique(vecteur) ) )
}

# Fonction qui remplace les NA des colonnes numerique par des 0. Faire attention avec le sens des colonnes (si des dates sont au format numerique, ne pas executer cette fonction)
Na_replace = function(df){
    a = ncol(df)
    for ( i in 1 : a){
        if ( is.numeric(df[,i]) ){
            b = sum(is.na(df[,i]) )
            if( b != 0){
                c = which( is.na(df[,i]) )
                df[c, i] = 0
            }
        }    
        if ( typeof(df[,i])=="character" ){
            b = sum(is.na(df[,i]) )
            if( b != 0){
                c = which( is.na(df[,i]) )
                df[c, i] = "-"
            }
        }
    }
    return(df)
}

## Fonction qui retourne l'age d'une personne en fonction de sa date de naissance
AGE = function( vecteur ){
    a = today() -  as.Date(vecteur) 
    return( as.numeric( trunc(a / 365) ) ) # Nombre d'annee de la personne
} 






