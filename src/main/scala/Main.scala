import org.apache.spark.sql.SparkSession
import config.Configuration
import data.{DataLoader, DataPreprocessor}
import features.FeatureBuilder
import model.StockSVMModel
import org.apache.spark.sql.expressions.Window
import org.apache.spark.sql.functions.{col, lag, when}
import sun.java2d.marlin.MarlinUtils.logInfo

object Main {
  def main(args: Array[String]): Unit = {
    // Initialize Spark Session
    val spark = SparkSession.builder()
      .appName("Stock Market Prediction with SVM")
      .master("local[*]")
      .config("spark.dynamicAllocation.enabled", "true")
      .config("spark.shuffle.service.enabled", "true")
      .getOrCreate()

    // Enable log level setting from configuration
    spark.sparkContext.setLogLevel("OFF")

    println("Starting the stock market prediction application...")

    // Load and preprocess data
    println(s"Loading data from directory: ${Configuration.stockDataDirectory}")
    var stockData = DataLoader.loadData(spark, Configuration.stockDataDirectory)

    stockData = DataPreprocessor.cleanData(stockData)

    stockData = stockData.withColumn("previousClose", lag(col("Close"), 1).over(Window.partitionBy("stockName").orderBy("Date")))
      .withColumn("label", when(col("Close") > col("previousClose"), 1).otherwise(0))
      .drop("previousClose")

    // Generate features
    println("Generating features...")
    stockData = FeatureBuilder.addAllFeatures(stockData)

    stockData = FeatureBuilder.addROC(stockData, 10)
    stockData = FeatureBuilder.addATR(stockData, 14)
    stockData = FeatureBuilder.addStochasticOscillator(stockData, 14)

    stockData.show(20,truncate = 0)

    val (trainingData, testData) = StockSVMModel.splitData(stockData, 0.7)

    // Train the SVM model
    println("Training the SVM model...")
    val model = StockSVMModel.trainModel(trainingData)

    // Evaluate the model
    println("Evaluating model performance...")
    val accuracy = StockSVMModel.evaluateModel(model, testData)
    println(s"Model accuracy: $accuracy")

    // Save the model if needed
    println("Saving the model...")
    model.save(Configuration.outputDirectory + "/svmModel")

    // Stop the Spark session
    spark.stop()
    println("Stock market prediction application completed.")
  }
}
