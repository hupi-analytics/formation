import org.apache.spark.{SparkConf, SparkContext}

import geotrellis.raster._
import geotrellis.raster.vectorize._
import geotrellis.raster.vectorize
import geotrellis.raster.io.geotiff.GeoTiff

import geotrellis.spark._
import geotrellis.spark.io.hadoop
import geotrellis.spark.io.hadoop._
import geotrellis.spark.io.RasterReader
import geotrellis.spark.tiling.FloatingLayoutScheme

import geotrellis.vector._

import org.apache.hadoop.fs.Path

import org.apache.spark.SparkContext
import org.apache.spark.rdd._
import org.apache.spark.mllib.linalg.{Vector, Vectors}

import java.net.URI
import scala.math.BigDecimal.RoundingMode

import org.apache.hadoop.io.compress.CompressionCodecFactory
import java.io.DataOutputStream
import org.apache.hadoop.conf.Configuration
import com.typesafe.config._

import org.apache.spark.sql.SparkSession

object MultiBand {

  def main(args: Array[String]) {

    val nb_args = 3
  
    if (args.length != nb_args) {
      Console.err.println("Main need " + nb_args + " arguments.")
      System.exit(1)
    }

    // Parameters
    val landsatName = args(0).toString // "LC08_L1TP_125052_20171012_20171024_01_T1" 
    val listBands = args(1).toString.split(",") // "B1,B2,B3,B4,B5,B6,B7,B9,B10,B11"
    val numPartitions = args(2).toInt

    // From resources/application.conf
    val config = ConfigFactory.load()
    val HdfsUrl = config.getString("HdfsUrl")
    val dataRepo = config.getString("dataRepo")
    val saveRepo = config.getString("saveRepo")

    val sparkSession = SparkSession.builder()
           //.config("es.read.field.exclude", "netflow.tcp_flags_label")   
          //.master("local")
          .appName("MultiBand")
          .getOrCreate()

    val sc = sparkSession.sparkContext

    // implicit variable (important variables to run the functions in the geotrellis library)
    implicit val sparkContext = sc

    val rr = implicitly[RasterReader[HadoopGeoTiffRDD.Options, (ProjectedExtent, Tile)]]

    // To make the notebook run more efficiently 
    val options =
    HadoopGeoTiffRDD.Options(
      numPartitions = Some(100)
    )

    type LandsatKey = (ProjectedExtent, URI, Int)
    // For each RDD, we're going to include more information in the key, including:
    // - the ProjectedExtent
    // - the URI
    // - the future band value
    def uriToKey(bandIndex: Int): (URI, ProjectedExtent) => LandsatKey =
      { (uri, pe) =>
        (pe, uri, bandIndex)
    }

    val savePath = new Path(HdfsUrl + saveRepo + landsatName + ".tif")
    val savePath_reduced = new Path(HdfsUrl + saveRepo + landsatName + "_reduced.tif")

    // Delete outputs if it's existed in HDFS 

    val conf = sc.hadoopConfiguration 

    // delete the images if it existed already
    val fs = org.apache.hadoop.fs.FileSystem.get(new java.net.URI(HdfsUrl), conf)
    fs.delete(savePath,true)
    fs.delete(savePath_reduced,true)

    // Function to get GeoTiff for each bands
    def bandPath(b: String) = s"${HdfsUrl}${dataRepo}${landsatName}/${landsatName}_${b}.TIF"
        
    // We initialize variable multiBands
    var multibands = HadoopGeoTiffRDD[ProjectedExtent, LandsatKey, Tile](
          new Path(bandPath("B1")), 
          uriToKey(0),
          options)

    // We create a loop that do the union of all bands 
    for (i <- 1 to listBands.length - 1) {
      val band = listBands(i)
      val bandTile = HadoopGeoTiffRDD[ProjectedExtent, LandsatKey, Tile](
              new Path(bandPath(band)), 
              uriToKey(i),
              options)
      multibands = sc.union(multibands, bandTile) 
    }

    // Combine all singleBands into one MultiBand 
    // Union these together, rearrange the elements so that we'll be able to group by key,
    // group them by key, and the rearrange again to produce multiband tiles.
    val sourceTiles: RDD[(ProjectedExtent, MultibandTile)] = {
     multibands.repartition(numPartitions)
        .map { case ((pe, uri, bandIndex), tile) =>
          // Get the center of the tile, which we will join on
          val (x, y) = (pe.extent.center.x, pe.extent.center.y)

          // Round the center coordinates in case there's any floating point errors
          val center =
            (
              BigDecimal(x).setScale(5, RoundingMode.HALF_UP).doubleValue(),
              BigDecimal(y).setScale(5, RoundingMode.HALF_UP).doubleValue()
            )

          // Get the scene ID from the path
          val sceneId = uri.getPath.split('/').reverse.drop(1).head

          val newKey = (sceneId, center)
          val newValue = (pe, bandIndex, tile)
          (newKey, newValue)
        }
        .map(l => (l._1, List(l._2))).reduceByKey(_ ++ _)
        //.groupByKey()
        .map { case (oldKey, groupedValues) =>
          val projectedExtent = groupedValues.head._1
          val bands = Array.ofDim[Tile](groupedValues.size)
          for((_, bandIndex, tile) <- groupedValues) {
            bands(bandIndex) = tile
          }
          (projectedExtent, MultibandTile(bands))
        }
    }

    // Convert to a Raster
    val (_, metadata) = sourceTiles.collectMetadata[SpatialKey](FloatingLayoutScheme())
    val tiles = sourceTiles.tileToLayout[SpatialKey](metadata)

    // Raster original without compression
    val raster = ContextRDD(tiles, metadata).stitch

    // Raster with resolution reduced
    val raster_reduced =
      ContextRDD(tiles, metadata)
        .withContext { rdd =>
          rdd.mapValues { tile =>
            // Magic numbers! These were created by fiddling around with
            // numbers until some example landsat images looked good enough
            // to put on a map for some other project.
            val (min, max) = (4000, 15176)
            def clamp(z: Int) = {
              if(isData(z)) { if(z > max) { max } else if(z < min) { min } else { z } }
              else { z }
            }
            // we normalize the value of these rasters from min to 0, from max to 255 to reduce the resolution
            val blue = tile.band(0).map(clamp _).delayedConversion(ByteCellType).normalize(min, max, 0, 255)
            val green = tile.band(1).map(clamp _).delayedConversion(ByteCellType).normalize(min, max, 0, 255)
            val red = tile.band(2).map(clamp _).delayedConversion(ByteCellType).normalize(min, max, 0, 255)
            MultibandTile(blue, green, red)
          }
        }
        .stitch

    // Save to HDFS 
    GeoTiff(raster, metadata.crs).write(savePath)
    GeoTiff(raster_reduced, metadata.crs).write(savePath_reduced)

  }

}
