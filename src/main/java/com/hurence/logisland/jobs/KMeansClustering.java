package com.hurence.logisland.jobs;


import com.hurence.botsearch.analytics.NetworkTrace;
import com.hurence.logisland.botsearch.HttpFlow;
import com.hurence.logisland.botsearch.Trace;
import com.hurence.logisland.processor.Processor;
import com.hurence.logisland.processor.SplitText;
import com.hurence.logisland.processor.StandardProcessContext;
import com.hurence.logisland.record.Record;
import com.hurence.logisland.record.RecordUtils;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.api.java.function.PairFunction;
import org.apache.spark.mllib.clustering.KMeans;
import org.apache.spark.mllib.clustering.KMeansModel;
import org.apache.spark.mllib.feature.StandardScaler;
import org.apache.spark.mllib.feature.StandardScalerModel;
import org.apache.spark.mllib.linalg.Vector;
import org.apache.spark.mllib.linalg.Vectors;
import org.apache.spark.rdd.RDD;
import scala.Tuple2;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;


public class KMeansClustering {

    public static void main(String[] args) {

        String appName = "KMeansClustering";

        // Initialize Spark configuration & context
        SparkConf sparkConf = new SparkConf().setAppName(appName).setMaster("local[1]").set("spark.executor.memory", "512m");

        JavaSparkContext sc = new JavaSparkContext(sparkConf);

        // Read data file from Hadoop file system.
        String path = "hdfs://sandbox.hortonworks.com:8020/user/hurence/flows.txt";

        // Read the data file and return it as RDD of strings
        JavaRDD<String> linesRDD = sc.textFile(path);

        Processor splitTextProcessor = new SplitText();
        StandardProcessContext context = new StandardProcessContext(splitTextProcessor, "dummy");

        PairFunction<String, String, Record> mapFunction = new PairFunction<String, String, Record>() {
            public Tuple2<String, Record> call(String line) {

                Record r = RecordUtils.getKeyValueRecord("", line);
                List list = new ArrayList<Record>();
                list.add(r);
                Collection<Record> records = splitTextProcessor.process(context, list);
                Record record = records.iterator().next();
                String ipSource = record.getField("ip_source").asString();
                String ipTarget = record.getField("ip_target").asString();

                return new Tuple2<>(ipSource + "_" + ipTarget, record);
            }
        };

        JavaPairRDD<String, Record> flowsRDD = linesRDD.mapToPair(mapFunction);
        //JavaRDD<Tuple2<String,Record>> flowsRDD = linesRDD.map(mapFunction);


        ////////////////////////////////////////
        // Compute traces from flows
        JavaRDD<Tuple2<String, NetworkTrace>> traces = flowsRDD.
                groupByKey()
                .map(t -> {
                    Iterable<Record> flowRecords = t._2;
                    String[] tokens = t._1.split("_");
                    Trace trace = new Trace();
                    try {
                        trace.setIpSource(tokens[0]);
                        trace.setIpTarget(tokens[1]);

                        // set up the flows buffer
                        ArrayList<HttpFlow> flows = new ArrayList<HttpFlow>();
                        flowRecords.forEach(f -> {
                            HttpFlow flow = new HttpFlow();
                            flow.setDate(new java.util.Date(f.getField("timestamp").asLong()));
                            flow.setRequestSize(f.getField("requestSize").asLong());
                            flow.setResponseSize(f.getField("responseSize").asLong());
                            flows.add(flow);
                        });

                        // we need at least 5 flows to compute one trace
                        if (flows.size() > 5) {
                            // flows need to be sorted on timestamp
                            flows.sort(new Comparator<HttpFlow>() {
                                @Override
                                public int compare(HttpFlow flow2, HttpFlow flow1) {
                                    return flow1.getDate().compareTo(flow2.getDate());
                                }
                            });

                            flows.forEach(f -> trace.add(f));

                            // compute trace frequencies and stats
                            trace.compute();
                        }
                    } catch (Exception e) {

                    }

                    return trace;
                })
                .map(trace -> new Tuple2<String, NetworkTrace>(trace.getIpSource() + "_" + trace.getIpTarget()
                        , new NetworkTrace(
                        trace.getIpSource(),
                        trace.getIpTarget(),
                        (float) trace.getAvgUploadedBytes(),
                        (float) trace.getAvgDownloadedBytes(),
                        (float) trace.getAvgTimeBetweenTwoFLows(),
                        (float) trace.getMostSignificantFrequency(),
                        trace.getFlows().size(),
                        "",
                        0)));

        // Save flows to parquet
        //flowsRDD. toDF().write.save(s"$source/flows.parquet")

        // Convert traces into a Dense vector
        JavaRDD<Tuple2<String, Vector>> tracesTuple = traces.map(t -> {
            double[] values = new double[4];
            values[0] = t._2.avgUploadedBytes();
            values[1] = t._2.avgDownloadedBytes();
            values[2] = t._2.avgTimeBetweenTwoFLows();
            values[3] = t._2.mostSignificantFrequency();
            return new Tuple2<>(t._1, Vectors.dense(values));
        }).cache();


        // Scale the trace to get mean = 0 and std = 1
        StandardScaler scaler = new StandardScaler(true, true);

        RDD<Vector> tracesVector = tracesTuple.map(tv -> tv._2).rdd();

        StandardScalerModel scalerModel = scaler.fit(tracesVector);

        JavaRDD<Tuple2<String, Vector>> scaledTraces = tracesTuple.map(x -> new Tuple2<>(x._1, scalerModel.transform(x._2)));


        // TODO add an automated job which compute best parameters
        // Cluster the data into two classes using KMeans
        int numClusters = 8;
        int numIterations = 20;
        // Cluster the data into two classes using KMeans k:$numClusters, numIterations:$numIterations
        KMeansModel clusters = KMeans.train(scaledTraces.map(x -> x._2).rdd(), numClusters, numIterations);

        // Evaluate clustering by computing Within Set Sum of Squared Errors
        double WSSSE = clusters.computeCost(scaledTraces.map(x -> x._2).rdd());

        // Assign traces to clusters
        JavaRDD<Tuple2<String, Integer>> centroids = scaledTraces.map(t -> new Tuple2<>(t._1, clusters.predict(t._2)));

        // TODO : transform into dataframe : .toDF("id", "centroid")
        // TODO : save into file and / or display in console
    }
}
