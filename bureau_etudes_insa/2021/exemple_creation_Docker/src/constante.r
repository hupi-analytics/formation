
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
output_directory = Sys.getenv("OUTPUT_DIRECTORY")