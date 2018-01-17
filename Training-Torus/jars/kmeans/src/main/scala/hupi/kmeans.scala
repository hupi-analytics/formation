import org.apache.spark.{SparkConf, SparkContext}

import geotrellis.raster._
import geotrellis.raster.vectorize._
import geotrellis.raster.vectorize
import geotrellis.raster.io.geotiff._
import geotrellis.raster.render._
import geotrellis.raster.io.geotiff.GeoTiff
import geotrellis.raster.resample._

import geotrellis.spark._
import geotrellis.spark.io._
import geotrellis.spark.io.RasterReader
import geotrellis.spark.io.hadoop._
import geotrellis.spark.io.hadoop
import geotrellis.spark.tiling._
import geotrellis.spark.tiling.FloatingLayoutScheme

import geotrellis.vector._

import org.apache.spark.SparkContext
import org.apache.spark.rdd._
import org.apache.spark.rdd.RDD
import org.apache.spark.HashPartitioner
import org.apache.spark.mllib.linalg.{Vector, Vectors}
import org.apache.spark.mllib.clustering.{KMeans, KMeansModel}

import java.net.URI
import scala.math.BigDecimal.RoundingMode
import org.apache.hadoop.fs.Path

import org.apache.hadoop.io.compress.CompressionCodecFactory
import java.io.DataOutputStream
import org.apache.hadoop.conf.Configuration
import com.typesafe.config._

import org.apache.spark.sql.SparkSession

object kmeans {

  def main(args: Array[String]) {

    val nb_args = 3
  
    if (args.length != nb_args) {
      Console.err.println("Main need " + nb_args + " arguments.")
      System.exit(1)
    }

    // From resources/application.conf
    val config = ConfigFactory.load()
    val HdfsUrl = config.getString("HdfsUrl")
    val dataRepo = config.getString("dataRepo")
    val saveRepo = config.getString("saveRepo")

    // Parameters
    val landsatName = args(0).toString // "LC08_L1TP_125052_20171231_20180103_01_T1" 
    val numPartitions = args(1).toInt
    val numClusters = args(2).toString.split(",") // list of k
    val n =  numClusters.length

    for (i <- 0 to (n - 1)) {
      val k = numClusters(i).toInt
      val sparkSession = SparkSession.builder()
           //.config("es.read.field.exclude", "netflow.tcp_flags_label")   
          //.master("local")
          .appName("kmeans")
          .getOrCreate()

      val sc = sparkSession.sparkContext

      // implicit variable (important variables to run the functions in the geotrellis library)
      implicit val sparkContext = sc

      val rr = implicitly[RasterReader[HadoopGeoTiffRDD.Options, (ProjectedExtent, Tile)]]

      //val saveAddress = HdfsUrl + saveRepo + landsatName + "/" + k 
      val saveAddress = HdfsUrl + saveRepo + landsatName + "/" + k 
      val savePath = new Path(saveAddress)

      // Delete outputs if it's existed in HDFS 

      val conf = sc.hadoopConfiguration  
      val fs = org.apache.hadoop.fs.FileSystem.get(new java.net.URI(HdfsUrl), conf) 
      // delete the model if it existed already
      fs.delete(savePath,true)
      
      // Load GeoTiff that we need from HDFS 
      // We get multi bands from HDFS (except band 8) 
      val sourceTiles = sc.hadoopMultibandGeoTiffRDD(HdfsUrl + dataRepo + landsatName + ".tif").repartition(numPartitions)

      // Prepare input for KMeans 
      // We convert multi tiles into one vector of features in RDD
      val rddArrayOfTiles = sourceTiles.map (l => (l._2.band(0).convert(DoubleConstantNoDataCellType).toArrayDouble(), 
                         l._2.band(1).convert(DoubleConstantNoDataCellType).toArrayDouble(), 
                         l._2.band(2).convert(DoubleConstantNoDataCellType).toArrayDouble(),
                         l._2.band(3).convert(DoubleConstantNoDataCellType).toArrayDouble(), 
                         l._2.band(4).convert(DoubleConstantNoDataCellType).toArrayDouble(),
                         l._2.band(5).convert(DoubleConstantNoDataCellType).toArrayDouble(), 
                         l._2.band(6).convert(DoubleConstantNoDataCellType).toArrayDouble(),
                         l._2.band(7).convert(DoubleConstantNoDataCellType).toArrayDouble(),
                         l._2.band(8).convert(DoubleConstantNoDataCellType).toArrayDouble(), 
                         l._2.band(9).convert(DoubleConstantNoDataCellType).toArrayDouble()))
        .map(l => l._1.zip(l._2).zip(l._3).zip(l._4).zip(l._5).zip(l._6).zip(l._7).zip(l._8).zip(l._9).zip(l._10))
        .map(l => l.map(k => Vectors.dense(k._1._1._1._1._1._1._1._1._1, k._1._1._1._1._1._1._1._1._2, 
                                           k._1._1._1._1._1._1._1._2, k._1._1._1._1._1._1._2,
                                          k._1._1._1._1._1._2, k._1._1._1._1._2, k._1._1._1._2,
                                           k._1._1._2, k._1._2, k._2)))

      val input = rddArrayOfTiles.flatMap(l => l)

      // Build a KMeans model

      // We train KMeans model
      val clusters = KMeans.train(input, k, 20)

      // save model to HDFS 
      clusters.save(sc, saveAddress)

      // stop SparkContext 
      sc.stop()
    }
  }

}
