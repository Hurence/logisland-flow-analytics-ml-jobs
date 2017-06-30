package com.hurence.logisland.job

import org.apache.spark.SparkContext
import org.apache.spark.SparkConf

object SimpleApp {
    def main(args: Array[String]) {

        // job configuration
        val conf = new SparkConf().setAppName("Simple App")
        val sc = new SparkContext(conf)

        // job definition
        var file = "/tmp/pagecount_sm.dat"
        val pagecounts = sc.textFile( file )
        val enPages = pagecounts.filter(_.split(" ")(1) == "en")
        val enPageCount = enPages.count
        println("Page count for EN pages: %s".format(enPageCount))

    }
}