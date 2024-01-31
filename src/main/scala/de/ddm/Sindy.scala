package de.ddm

import org.apache.spark.sql.{Dataset, Row, SparkSession,Encoder,Encoders}

import scala.collection.mutable


object Sindy {
  implicit val tupleEncoder: Encoder[(String, Set[String])] = Encoders.tuple(Encoders.STRING, Encoders.kryo[Set[String]])

  private def readData(input: String, spark: SparkSession): Dataset[Row] = {
    spark
      .read
      .option("inferSchema", "false")
      .option("header", "true")
      .option("quote", "\"")
      .option("delimiter", ";")
      .csv(input)
  }
//+---------------+      +---------------------+       +---------------------+       +-------------------+      +---------------------+
//| Input Data    | ---> | Read and Transform  | --->  | Combine DataFrames  | --->  | Group By Key      | ---> | Generate Dependencies| --->  ---> | Output Result     |
//| (TPCH)     |      +---------------------+       +---------------------+       +-------------------+      +---------------------+
//+---------------+                                                                                                                            |
//      |                                                                                                                                     |
//      v                                                                                                                                     |
//+-----------------------------------------------+                                                                                         |
//| Extract Data from Rows (flatMap Operation)     |                                                                                         |
//| (extractDataFromRows function)                 |                                                                                         |
//| - For each row, extract values and form tuples |                                                                                         |
//|   (identifier, Set(identifier))                |                                                                                         |
//+-----------------------------------------------+                                                                                         |
//      |                                                                                                                                     |
//      v                                                                                                                                     |
//+-----------------------------------------------+                                                                                         |
//| Generate Dependency Rows (flatMap Operation)  |                                                                                         |
//| (generateDependencyRows function)              |                                                                                         |
//| - For each identifier, generate tuples with    |                                                                                         |
//|   dependencies (identifier, Set(dependencies)) |                                                                                         |
//+-----------------------------------------------+                                                                                         |
//      |                                                                                                                                     |
//      v                                                                                                                                     |
//+-----------------------------------------------+                                                                                         |
//| Reduce Operation (groupByKey and reduce)       |                                                                                         |
//| - Group tuples by identifier (groupByKey)     |                                                                                         |
//| - For each group, intersect sets (reduce)      |                                                                                         |
//+-----------------------------------------------+                                                                                         |
//      |                                                                                                                                     |
//      v                                                                                                                                     |
//      |                                                                                                                                     |
//      v                                                                                                                                     |
//+-----------------------------------------------+                                                                                         |
//| Collect and Sort Results                       |                                                                                         |
//| - Collect final results as an array            |                                                                                         |
//| - Sort results alphabetically by key           |                                                                                         |
//+-----------------------------------------------+                                                                                         |
//
  def discoverINDs(inputs: List[String], spark: SparkSession): Unit = {
        import spark.implicits._
        def extractDataFromRows(df: Dataset[Row]) = {
          val columns = df.columns
      df.flatMap(row => columns.indices.flatMap(i => Seq((row.get(i).toString, Set(columns(i))))))
    }
    def generateDependencyRows(set: (String, Set[String])) = set._2.map(identifier => (identifier, set._2 - identifier))
    val finalINDs = inputs
      .map(input => readData(input, spark))
      .map(extractDataFromRows)
      .reduce(_ union _)
      .groupByKey(_._1)
      .mapGroups((key, value) => (key, value.flatMap(elem => elem._2).toSet))
      .flatMap(generateDependencyRows)
      .groupByKey(_._1)
      .mapGroups((value, set) => (value, set.map(x => x._2).reduce((ref, dep) => ref.intersect(dep))))
      .filter(elem => elem._2.nonEmpty)
      .collect()
      .map(row => (row._1, row._2.toList.sorted))
      .sortBy(x => x._1)
    finalINDs.foreach { case (value, dependencies) => println(s"$value < ${dependencies.mkString(", ")}")

    }

  }}





