package com.hurence.logisland.jobs;

import com.hurence.botsearch.analytics.NetworkTrace;
import com.hurence.logisland.botsearch.HttpFlow;
import com.hurence.logisland.botsearch.Trace;
import com.hurence.logisland.processor.Processor;
import com.hurence.logisland.processor.SplitText;
import com.hurence.logisland.processor.StandardProcessContext;
import com.hurence.logisland.processor.UpdateBiNetflowDate;
import com.hurence.logisland.record.Record;
import com.hurence.logisland.record.RecordUtils;
import org.apache.commons.cli.*;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.api.java.function.Function;
import org.apache.spark.api.java.function.PairFunction;
import org.apache.spark.mllib.clustering.KMeans;
import org.apache.spark.mllib.clustering.KMeansModel;
import org.apache.spark.mllib.feature.StandardScaler;
import org.apache.spark.mllib.feature.StandardScalerModel;
import org.apache.spark.mllib.linalg.Vector;
import org.apache.spark.mllib.linalg.Vectors;
import org.apache.spark.rdd.RDD;
import scala.Tuple2;

import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;






public class KMeansClustering {

    public static class ClusteredTrace implements Serializable {
        private String src_ip;
        private String dest_ip;

        private double avg_uploaded_bytes;
        private double avg_downloaded_bytes;
        private double avg_time_between_two_fLows;
        private double most_significant_frequency;
        private long flows_count;


        public String getSrc_ip() {
            return src_ip;
        }

        public void setSrc_ip(String src_ip) {
            this.src_ip = src_ip;
        }

        public String getDest_ip() {
            return dest_ip;
        }

        public void setDest_ip(String dest_ip) {
            this.dest_ip = dest_ip;
        }

        public double getAvg_uploaded_bytes() {
            return avg_uploaded_bytes;
        }

        public void setAvg_uploaded_bytes(double avg_uploaded_bytes) {
            this.avg_uploaded_bytes = avg_uploaded_bytes;
        }

        public double getAvg_downloaded_bytes() {
            return avg_downloaded_bytes;
        }

        public void setAvg_downloaded_bytes(double avg_downloaded_bytes) {
            this.avg_downloaded_bytes = avg_downloaded_bytes;
        }

        public double getAvg_time_between_two_fLows() {
            return avg_time_between_two_fLows;
        }

        public void setAvg_time_between_two_fLows(double avg_time_between_two_fLows) {
            this.avg_time_between_two_fLows = avg_time_between_two_fLows;
        }

        public double getMost_significant_frequency() {
            return most_significant_frequency;
        }

        public void setMost_significant_frequency(double most_significant_frequency) {
            this.most_significant_frequency = most_significant_frequency;
        }

        public long getFlows_count() {
            return flows_count;
        }

        public void setFlows_count(long flows_count) {
            this.flows_count = flows_count;
        }
    }



    public static void main(String[] args) {

        // Command line management :

        Parser parser = new GnuParser();
        Options options = new Options();

        String helpMsg = "Print this message.";
        Option help = new Option("help", helpMsg);
        options.addOption(help);

        String nbOfClustersMsg = "Number of clusters";
        OptionBuilder.withArgName("nbClusters");
        OptionBuilder.hasArg();
        OptionBuilder.withDescription(nbOfClustersMsg);
        OptionBuilder.isRequired(true);
        Option nbOfClusters = OptionBuilder.create("nbClusters");
        options.addOption(nbOfClusters);

        String nbOfIterationsMsg = "Number of iterations";
        OptionBuilder.withArgName("nbIterations");
        OptionBuilder.hasArg();
        OptionBuilder.withDescription(nbOfIterationsMsg);
        OptionBuilder.isRequired(true);
        Option nbOfIterations = OptionBuilder.create("nbIterations");
        options.addOption(nbOfIterations);

        String inputPathMsg = "Training Dataset File Path";
        OptionBuilder.withArgName("inputPath");
        OptionBuilder.hasArg();
        OptionBuilder.withDescription(inputPathMsg);
        OptionBuilder.isRequired(true);
        Option inputPath = OptionBuilder.create("inputPath");
        options.addOption(inputPath);
        // Exemple : --inputPath "hdfs://sandbox.hortonworks.com:8020/user/hurence/flows.txt"
        // Example : --inputPath "file:///D:\\perso\\Developpement\\logisland-flow-analytics-ml-jobs\\resources\\light_capture_100000.txt"

        String outputPathMsg = "Saved Model File Path";
        OptionBuilder.withArgName("outputPath");
        OptionBuilder.hasArg();
        OptionBuilder.withDescription(outputPathMsg);
        OptionBuilder.isRequired(true);
        Option outputPath = OptionBuilder.create("outputPath");
        options.addOption(outputPath);
        // Example : --outputPath "file:///D:\\perso\\Developpement\\logisland-flow-analytics-ml-jobs\\target\\savedModels"

        int nbClusters = 8;
        int nbIterations = 20;
        String inputPathFile = "";
        String outputPathFile = "";

        try {
            // parse the command line arguments
            CommandLine line = parser.parse(options, args);

            if (!line.getOptionValue("nbClusters").isEmpty()) {
                nbClusters = Integer.parseInt(line.getOptionValue("nbClusters"));
            }
            if (!line.getOptionValue("nbIterations").isEmpty()) {
                nbIterations = Integer.parseInt(line.getOptionValue("nbIterations"));
            }
            if (!line.getOptionValue("inputPath").isEmpty()) {
                inputPathFile = line.getOptionValue("inputPath");
            }
            if (!line.getOptionValue("outputPath").isEmpty()) {
                outputPathFile = line.getOptionValue("outputPath");
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
            System.exit(1);
        }

        long timeInMillis = System.currentTimeMillis();
        outputPathFile += "_" + timeInMillis;

        System.out.println("Nb of clusters = " + nbClusters);
        System.out.println("Nb of iterations = " + nbIterations);
        System.out.println("Training Dataset File Path = " + inputPathFile);
        System.out.println("Output Model File Path = " + outputPathFile);


        // Initialize Spark configuration & context
        String appName = "KMeansClustering";
        SparkConf sparkConf = new SparkConf().setAppName(appName).setMaster("local[1]").set("spark.executor.memory", "512m");
        JavaSparkContext sc = new JavaSparkContext(sparkConf);

        // Read data file from file system and return it as RDD of strings:
        JavaRDD<String> linesRDD = sc.textFile(inputPathFile);

        // Split Text Processor :
        Processor splitTextProcessor = new SplitText();
        StandardProcessContext splitTextContext = new StandardProcessContext(splitTextProcessor, "splitTextProcessor");
        splitTextContext.setProperty("value.fields", "timestamp,duration,protocol,src_ip,src_port,direction,dest_ip,dest_port,state,src_tos,dest_tos,packets_out,bytes_out,bytes_in,label");
        splitTextContext.setProperty("value.regex", "(\\d{4}\\/\\d{2}\\/\\d{2}\\s\\d{1,2}:\\d{1,2}:\\d{1,2}\\.\\d{0,6}),([^,]+)?,([^,]+)?,([^,]+)?,([^,]+)?,([^,]+)?,([^,]+)?,([^,]+)?,([^,]+)?,([^,]+)?,([^,]+)?,([^,]+)?,([^,]+)?,([^,]+)?,flow=([^,]+)");

        // BiNetFlow Processor :
        Processor updateBiNetflowDate = new UpdateBiNetflowDate();
        StandardProcessContext updateBiNetflowDateContext = new StandardProcessContext(updateBiNetflowDate, "updateBiNetflowDate");

        PairFunction<String, String, Record> mapFunction = new PairFunction<String, String, Record>() {
            public Tuple2<String, Record> call(String line) {

                Record r = RecordUtils.getKeyValueRecord("", line);
                List<Record> list = new ArrayList<>();
                list.add(r);
                Collection<Record> tempRecords = splitTextProcessor.process(splitTextContext, list);
                Collection<Record> records = updateBiNetflowDate.process(updateBiNetflowDateContext, tempRecords);

                try {
                    Record record = records.iterator().next();
                    String ipSource = record.getField("src_ip").asString();
                    String ipTarget = record.getField("dest_ip").asString();

                    return new Tuple2<>(ipSource + "_" + ipTarget, record);
                }catch (Exception ex){
                    return new Tuple2<>("unknown", null);
                }
            }
        };

        JavaPairRDD<String, Record> flowsRDD = linesRDD.mapToPair(mapFunction);

        ///////////////////////////////
        // Compute traces from flows //
        ///////////////////////////////

        JavaRDD<Tuple2<String, NetworkTrace>> traces = flowsRDD.
                groupByKey()
                .map(t -> {
                    Trace trace = new Trace();
                    try {
                        Iterable<Record> flowRecords = t._2;
                        String[] tokens = t._1.split("_");

                        trace.setIpSource(tokens[0]);
                        trace.setIpTarget(tokens[1]);

                        // set up the flows buffer
                        ArrayList<HttpFlow> flows = new ArrayList<>();
                        flowRecords.forEach(flowRecord -> {
                            HttpFlow flow = new HttpFlow();
                            flow.setDate(new java.util.Date(flowRecord.getField("record_time").asLong()));
                            flow.setipSource(flowRecord.getField("src_ip").asString());
                            flow.setIpTarget(flowRecord.getField("dest_ip").asString());
                            flow.setRequestSize(flowRecord.getField("bytes_in").asLong());
                            flow.setResponseSize(flowRecord.getField("bytes_out").asLong());
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

                            flows.forEach(trace::add);

                            // compute trace frequencies and stats
                            trace.compute();
                        }
                    } catch (Exception ignored) {

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
        // Cluster the data into two classes using KMeans k:$nbClusters, nbIterations:$nbIterations
        KMeansModel clusters = KMeans.train(scaledTraces.map(x -> x._2).rdd(), nbClusters, nbIterations);

        // Display cluster centers :
        displayClustersCenters(clusters);

      //  clusters.save(sc.sc(), outputPathFile);

        try {
            FileOutputStream out = new FileOutputStream(outputPathFile);
            ObjectOutputStream oos = new ObjectOutputStream(out);
            oos.writeObject(clusters);
            oos.flush();
            oos.close();
        } catch (Exception e) {
            System.out.println("Problem serializing: " + e);
        }


        // Evaluate clustering by computing Within Set Sum of Squared Errors
        double WSSSE = clusters.computeCost(scaledTraces.map(x -> x._2).rdd());

        // Assign traces to clusters
        JavaRDD<Tuple2<String, Integer>> centroids = scaledTraces.map(t -> new Tuple2<>(t._1, clusters.predict(t._2)));

/*
*
*  logger.info(s"assign traces to clusters")
        val centroids = scaledTraces.map(t => (t._1, clusters.predict(t._2))).toDF("id", "centroid")

        logger.info(s"save traces to parquet")
        val tmp = traces.map(r => (r._1, r._2.ipSource, r._2.ipTarget,
            r._2.avgUploadedBytes,
            r._2.avgDownloadedBytes,
            r._2.avgTimeBetweenTwoFLows,
            r._2.mostSignificantFrequency,
            r._2.flowsCount)).toDF("id",
            "ip_source",
            "ip_target",
            "avg_uploaded_bytes",
            "avg_downloaded_bytes",
            "avg_time_between_two_fLows",
            "most_significant_frequency",
            "flows_count")
            .join(centroids, "id")
            .select("ip_source",
                "ip_target",
                "avg_uploaded_bytes",
                "avg_downloaded_bytes",
                "avg_time_between_two_fLows",
                "most_significant_frequency",
                "flows_count",
                "centroid")

        tmp.printSchema()
        tmp.show()
        tmp.write.save(s"$source/traces.parquet")
* */

        // Check model persistence :
       /* KMeansModel loadedClusters = KMeansModel.load(sc.sc(), outputPathFile);
        System.out.println("Centroïds loaded from persisted model file :");*/
        displayClustersCenters(clusters);


    }

    private static void displayClustersCenters(KMeansModel clusters) {
        Vector[] clusterCenters = clusters.clusterCenters();
        for (int i = 0; i < clusterCenters.length; i++) {
            Vector clusterCenter = clusterCenters[i];
            double[] centerPoint = clusterCenter.toArray();
            System.out.println("Cluster Center " + i + ": [ " +
                    "'Average uploaded bytes': " + centerPoint[0] +
                    ", 'Average downloaded bytes': " + centerPoint[1] +
                    ", 'Average time between two flows': " + centerPoint[2] +
                    ", 'Most Significant Frequency': " + centerPoint[3] +
                    " ]");
        }
    }

}
