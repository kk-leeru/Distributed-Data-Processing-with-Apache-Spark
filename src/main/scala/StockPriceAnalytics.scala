// object Main extends App {
//   println("Hello, World!")
// }

import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions._
import org.apache.spark.streaming._


import org.apache.hadoop.fs.{FileSystem, Path}
import org.apache.arrow.flatbuf.Bool

object StockPriceAnalytics {
  def main(args: Array[String]): Unit = {

    // Create a Spark session
    val spark = SparkSession.builder()
      .appName("Stock Price Analytics")
      .master("local[*]")  // Use all available cores
      .getOrCreate()

    if (args.length>0) {
      args(0) match {
        case "batch" => runBatchAnalytics(spark)
        case "stream" => runRealTimeAnalytics(spark)
        case _ => println("Unknown mode. Use 'batch' or 'stream'.")
      }
    } 
    else {
      println("Plese provide a mdoe: 'batch' or 'stream'.")
    }

  
   
  }

  def runBatchAnalytics (spark: SparkSession): Unit = {
    // Load stock price data from CSV (replace with your dataset path)
    val data = spark.read.option("header", "true")
      .csv("./src/spdata.csv")

    // Display the schema of the dataset
    data.printSchema()

    // Show some sample rows
    data.show()  

    // Perform data processing: Calculate average closing price

    val newValue = col("收盤價(元)").cast("double") / col("開盤價(元)").cast("double")
    val dfAPI = data.filter(col("成交量(千股)") > 5000)  
      .withColumn("Close_open_ratio", newValue)
      .groupBy("證券代碼")
      .agg(avg("開盤價(元)").alias("Avg_open"), avg("收盤價(元)").alias("Avg_close"))
      .orderBy(col("Avg_close").desc)

      // Show average closing prices
      dfAPI.show()

      //write to external storage system for spark 
      // dfAPI.write.mode("overwrite").csv("./output")

      //write as csv
      dfAPI.coalesce(1)
        .write.option("header", "true")
        .mode("overwrite")
        .csv("./output")

      //to change it instead of regular part-00000
      val fs = FileSystem.get(spark.sparkContext.hadoopConfiguration)
      val srcPath = new Path(s"./output/part-00000%")
      val destPath = new Path(s"./output/final_output.csv")
      fs.rename(srcPath, destPath)

    // Stop the Spark session
    spark.stop()
  }
  def runRealTimeAnalytics (spark: SparkSession): Unit = {
    // create StreamingContext with batch interval of 5 seconds

    val ssc= new StreamingContext(spark.sparkContext, Seconds(5))

    //connect to a socket stream for real-time data
    val lines = ssc.socketTextStream("localhost", 9999)

    //Process the streaming data
    val stockPrices = lines.map(_.split(","))
      .filter(arr => arr(1).toDouble >100) //filter 

      //print
      stockPrices.print()

      //start streaming context and await termination
      ssc.start()
      ssc.awaitTermination()
  }
}