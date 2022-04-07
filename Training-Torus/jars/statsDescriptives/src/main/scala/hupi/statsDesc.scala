import org.apache.spark.{SparkConf, SparkContext}

import geotrellis.raster._
import geotrellis.raster.vectorize._
import geotrellis.raster.vectorize
import geotrellis.raster.io.geotiff._
import geotrellis.raster.render._
import geotrellis.raster.io.geotiff.GeoTiff

import geotrellis.spark._
import geotrellis.spark.io.RasterReader
import geotrellis.spark.io.hadoop._
import geotrellis.spark.io.hadoop
import geotrellis.spark.tiling.FloatingLayoutScheme

import geotrellis.vector._

import org.apache.spark.rdd._
import org.apache.spark.SparkContext
import org.apache.spark.mllib.linalg.Vectors

import java.net.URI 
import scala.math.BigDecimal.RoundingMode

import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.Path

import org.apache.hadoop.io.compress.CompressionCodecFactory
import java.io.DataOutputStream
import org.apache.hadoop.conf.Configuration
import com.typesafe.config._

import com.mongodb.spark._
import com.mongodb.spark.config._
import org.bson.Document

import org.apache.spark.sql.SparkSession

object descStatistics {

  def main(args: Array[String]) {

    val nb_args = 3
  
    if (args.length != nb_args) {
      Console.err.println("Main need " + nb_args + " arguments.")
      System.exit(1)
    }

    // Parameters
    val landsatName = args(0).toString // "LC08_L1TP_125052_20171012_20171024_01_T1" 
    val redBand = args(1).toString // "B4"
    val nirBand = args(2).toString // "B5"

    // From resources/application.conf
    val config = ConfigFactory.load()
    val HdfsUrl = config.getString("HdfsUrl")
    val dataRepo = config.getString("dataRepo")
    val MongoUrl = config.getString("MongoUrl")

    val sparkSession = SparkSession.builder()
           //.config("es.read.field.exclude", "netflow.tcp_flags_label")   
          //.master("local")
          .appName("descStatistics")
          .getOrCreate()

    import sparkSession.implicits._

    val sc = sparkSession.sparkContext

    // implicit variable (important variables to run the functions in the geotrellis library)
    implicit val sparkContext = sc

    val rr = implicitly[RasterReader[HadoopGeoTiffRDD.Options, (ProjectedExtent, Tile)]]

    // To make the notebook run more efficiently 
    val options =
    HadoopGeoTiffRDD.Options(
      numPartitions = Some(100)
    )

    val saveName = HdfsUrl + dataRepo + landsatName + "_withNDVIMean"

    // Delete outputs if it's existed in HDFS 

    val conf = sc.hadoopConfiguration  
    val fs = org.apache.hadoop.fs.FileSystem.get(new java.net.URI(HdfsUrl), conf) 
    fs.delete(new Path(saveName + ".png"),true)
    
    // Load GeoTiff that we need from HDFS 
    val ndvi = HadoopGeoTiffRDD[ProjectedExtent, Tile](
      new Path(HdfsUrl + dataRepo + landsatName + ".tif"), 
      options).map(l => (l._1, l._2.convert(DoubleConstantNoDataCellType)))        

    // Convert Tile to ArrayDouble
    val arrayNDVI = ndvi.map(l => (l._1.extent, l._2.toArrayDouble))

    // We replace NaN by NODATA = -2147483648 and compute descriptive stats by tile
    val summaryStatsByTile = arrayNDVI.map {
      case (extent, arrayDouble) => {
        // Length of array of each Tile
        val lengthArray = arrayDouble.length
        // If array contains NaN => we transform whole array to array of NODATA
        if (arrayDouble.filter(l => l.isNaN).length > 1) {
          val newArray = Array.fill(lengthArray)(NODATA.toDouble)
          val mean = newArray.sum / lengthArray
          // we return new RDD of 10 columns : xmin, ymin, xmax, ymax, ...and summary stats of NDVI per Tile
          (landsatName, extent.xmin, extent.ymin, extent.xmax, extent.ymax, extent.width, extent.height,
           newArray.min, newArray.max,
           newArray.sum / lengthArray, 
           Math.sqrt((newArray.map( _ - mean).map(t => t*t).sum)/lengthArray))
        } else {
          val mean = arrayDouble.sum / lengthArray
          (landsatName, extent.xmin, extent.ymin, extent.xmax, extent.ymax, extent.width, extent.height,
           arrayDouble.min, arrayDouble.max,
           mean, 
           Math.sqrt((arrayDouble.map( _ - mean).map(t => t*t).sum)/lengthArray))
        }
      }
    }

    // Convert RDD to Dataframe
    val df = summaryStatsByTile.toDF("landsatName", "xmin", "ymin", "xmax", "ymax", "width", "height", "minNDVI", "maxNDVI", "meanNDVI", "stdDevNDVI")

    // Prepare output to save in MongoDB
    // Convert RDD to Dataframe
    df.write.format("com.mongodb.spark.sql").option("uri", s"mongodb://10.100.2.7:27017/hupi.descriptive_stats_NDVI")
      .mode("overwrite").save()

    // Visualization Results

    // For each Tile in ndvi, we compute meanNDVI and replace all NDVI in this Tile by meanNDVI
    val ndviFinal = ndvi.map {
      case (pe, tile) => {
        val arrayTile = tile.toArrayDouble
        val lengthArrayTile = arrayTile.length
        val meanNDVI = arrayTile.sum / lengthArrayTile
        (pe, DoubleArrayTile(Array.fill(lengthArrayTile)(meanNDVI), tile.cols, tile.rows).tile)
      }
    }

    // We create color map
    val ndviColormap = "0:ffffe5ff;0.1:f7fcb9ff;0.2:d9f0a3ff;0.3:addd8eff;0.4:78c679ff;0.5:41ab5dff;0.6:238443ff;0.7:006837ff;1:004529ff"

    // Get color map from the application.conf settings file.
    val colorMap = ColorMap.fromStringDouble(ndviColormap).get

    // Get metadata and tiles to convert to raster
    val (_, metadata) = ndviFinal.collectMetadata[SpatialKey](FloatingLayoutScheme())
    val tiles = ndviFinal.tileToLayout[SpatialKey](metadata)

    // We create Raster by stitching all tiles 
    val raster_ndvi = ContextRDD(tiles, metadata).stitch

    // Render to PNG
    val ndvi_png = raster_ndvi.tile.renderPng(colorMap)

    // Save to HDFS 
    ndvi_png.write(new Path(saveName + ".png")) 

  }

}
