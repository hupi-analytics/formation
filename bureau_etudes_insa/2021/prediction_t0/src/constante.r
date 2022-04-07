
## Constantes Mongo : 
mongo_host = Sys.getenv("MONGO_HOST")
customer = Sys.getenv("MONGO_USER")
password = Sys.getenv("MONGO_PASSWORD")
collection_name = "collection_sortie.csv"

## Constantes HDFS : 
hdfs_url = Sys.getenv("HDFS_URL")
hdfs_port = Sys.getenv("HDFS_PORT")
hdfs_username = Sys.getenv("HDFS_USERNAME")
input_directory = Sys.getenv("INPUT_DIRECTORY")
output_directory = Sys.getenv("OUTPUT_DIRECTORY") #si on veut ecrire sur HDFS

url <- paste0("http://", hdfs_url, ":", hdfs_port, "/webhdfs/v1/user/", hdfs_username, "/", input_directory)
# input
article_url = paste0(url, "data/Articles.txt", "?op=OPEN")
commande_url = paste0(url, "data/Commandes.txt", "?op=OPEN")
collection_url = paste0(url, "data/Collections.txt", "?op=OPEN")
# output
output_directory = paste0(url, collection_name, "?op=OPEN")
# import
code_to_color_url = paste0(url, "import/code_to_color.csv", "?op=OPEN")
fam_to_code_fam_url = paste0(url, "import/fam_to_code_fam.csv", "?op=OPEN")
