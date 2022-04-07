import org.apache.spark.{SparkConf, SparkContext}

//sparksql
import geotrellis.proj4._

import geotrellis.spark._
import geotrellis.spark.io.hadoop._
import geotrellis.spark.io.hadoop.formats._
import geotrellis.spark.io.RasterReader
import geotrellis.spark.tiling.FloatingLayoutScheme

import geotrellis.vector._

import geotrellis.raster._
import geotrellis.raster.render._
import geotrellis.raster.io.geotiff._
import geotrellis.raster.io.geotiff.reader.GeoTiffReader
import geotrellis.raster.io.geotiff.tags.TiffTags
import geotrellis.raster.io.geotiff.GeoTiff

import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.Path

import org.apache.hadoop.io.compress.CompressionCodecFactory
import java.io.DataOutputStream
import org.apache.hadoop.conf.Configuration
import com.typesafe.config._

import org.apache.spark.sql.SparkSession


object NDVI {

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
    val saveRepo = config.getString("saveRepo")

    val sparkSession = SparkSession.builder()
           //.config("es.read.field.exclude", "netflow.tcp_flags_label")   
          //.master("local")
          .appName("NDVI")
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

    val saveName = HdfsUrl + saveRepo + landsatName 

    // Delete outputs if it's existed in HDFS 

    val conf = sc.hadoopConfiguration  
    val fs = org.apache.hadoop.fs.FileSystem.get(new java.net.URI(HdfsUrl), conf) 
    fs.delete(new Path(saveName + ".png"),true)
    fs.delete(new Path(saveName + ".tif"),true)
    
    // Load GeoTiff that we need from HDFS 
    val RedBand = HadoopGeoTiffRDD[ProjectedExtent, Tile](
      new Path(HdfsUrl + dataRepo + landsatName + "/" + landsatName + "_" + redBand + ".TIF"), 
      options).map(l => (l._1, l._2.convert(DoubleConstantNoDataCellType)))

    val NIRBand = HadoopGeoTiffRDD[ProjectedExtent, Tile](
      new Path(HdfsUrl + dataRepo + landsatName + "/" + landsatName + "_" + nirBand + ".TIF"), 
      options).map(l => (l._1, l._2.convert(DoubleConstantNoDataCellType)))

    val (_, metadata_red) = RedBand.collectMetadata[SpatialKey](FloatingLayoutScheme())
    val tiles_red = RedBand.tileToLayout[SpatialKey](metadata_red)

    val (_, metadata_nir) = NIRBand.collectMetadata[SpatialKey](FloatingLayoutScheme())
    val tiles_nir = NIRBand.tileToLayout[SpatialKey](metadata_nir)

    val ndvi = (tiles_nir - tiles_red) / (tiles_nir + tiles_red).cache()

    val raster_ndvi = ContextRDD(ndvi, metadata_red).stitch

    // Color map 
    val ndviColormap = "0:ffffe5ff;0.1:f7fcb9ff;0.2:d9f0a3ff;0.3:addd8eff;0.4:78c679ff;0.5:41ab5dff;0.6:238443ff;0.7:006837ff;1:004529ff"

    // Get color map from the application.conf settings file.
    val colorMap = ColorMap.fromStringDouble(ndviColormap).get

    // Save to HDFS
    // format Tif 
    GeoTiff(raster_ndvi, metadata_red.crs).write(new Path(saveName + ".tif"))

    // format PNG 
    val ndvi_png = raster_ndvi.tile.renderPng(colorMap)

    ndvi_png.write(new Path(saveName + ".png"))
    
  }

}
