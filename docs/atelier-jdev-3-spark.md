# Introduction to spark



## Interactive data exploration with SPARK
In this sequence, we will use the Spark's interactive shell to explore the [Wikipedia web site traffic](http://dumps.wikimedia.org/other/pagecounts-raw/). 

You will then discover how easy  an exploratory data analysis with Spark (and Scala) can be, in an R (or Python) fashion. You will also see how an intuitive/high-level framework like Spark can drastically improve data mining in a big data context (by distributing all the processing behind the scene and let user manipulates data structures without worrying about their sizes) where relational SQL or even Map/Reduce jobs would definitely suffers from very high latencies. At last I hope you will be impressed by the seamlessly inter-operation between Spark & the Hadoop file system.

### Data scheme
Each request of a page, whether for editing or reading, whether a "special page" such as a log of actions generated on the fly, or an article from Wikipedia or one of the other projects, reaches one of our squid caching hosts and the request is sent via UDP to a filter which tosses requests from our internal hosts, as well as requests for wikis that aren't among our general projects. This filter writes out the project name, the size of the page requested, and the title of the page requested.

The 1st column is the timestamp (hour granularity), the 2nd is the language, the 3rd column is the title of the page retrieved, the 4th column is the number of requests, and the 5th column is the size of the content returned.

Here are a few sample lines from one file:

	20140101-060000 fr Special:Recherche/Acteurs_et_actrices_N 1 739
	20140101-120000 fr Special:Recherche/Agrippa_d/%27Aubign%C3%A9 1 743
	20140101-110000 fr Special:Recherche/All_Mixed_Up 1 730
	20140101-120000 fr Special:Recherche/Andr%C3%A9_Gazut.html 1 737

There is one data files :

    cd /tmp
    wget https://github.com/Hurence/logisland-flow-analytics-ml-jobs/releases/download/v0.1/pagecount_sm.dat.tgz
    tar xzf /tmp/pagecount_sm.dat.tgz

We will launch our *driver* program through the Spark shell. To connect to the Spark cluster through the shell, simply type: 

    /usr/local/spark/bin/spark-shell 

Warm up by creating an RDD (Resilient Distributed Dataset) named pagecounts from the HDFS input files. In the Spark shell, the SparkContext is already created for you as variable sc.

	sc
	val pagecounts = sc.textFile("/tmp/pagecount_sm.dat" )
	
Now have a look to the first line of this file

	pagecounts.first

Let’s take a peek at the data. You can use the take operation of an RDD to get the first K records. Here, K = 10. 

	pagecounts.take(10)

Unfortunately this is not very readable because take() returns an array and Scala simply prints the array with each element separated by a comma. We can make it prettier by traversing the array to print each record on its own line.

	pagecounts.take(10).foreach(println)

Recall from above when we described the format of the data set, that the second field is the "project code" and contains information about the language of the pages. For example, the project code "fr" indicates a French page. Let’s derive an RDD containing only French pages from pagecounts. This can be done by applying a filter function to pagecounts. For each record, we can split it by the field delimiter (i.e. a space) and get the second field-– and then compare it with the string "fr".

To avoid reading from disks each time we perform any operations on the RDD, we also cache the RDD into memory. This is where Spark really starts to to shine.

Now count the number of lines.

	val enPages = pagecounts.filter(_.split(" ")(1) == "en").cache
	enPages.count

The first time this command is run it will take 2 - 3 minutes while Spark scans through the entire data set on disk. But since frPages was marked as "cached” in the previous step, if you run count on the same RDD again, it should return an order of magnitude faster.

If you examine the console log closely, you will see lines like this, indicating some data was added to the cache:

	enPages.count
	
Let’s try something fancier. Generate a histogram of total page views on Wikipedia French pages for the date range represented in our dataset. The high level idea of what we’ll be doing is as follows. First, we generate a key value pair for each line; the key is the date (the first eleven characters of the first field), and the value is the number of pageviews for that date (the fourth field).

	val enTuples = enPages.map(line => line.split(" "))
	val enKeyValuePairs = enTuples.map(line => (line(0).substring(0, 11), line(3).toInt))

Next, we shuffle the data and group all values of the same key together. Finally we sum up the values for each key. There is a convenient method called `reduceByKey` in Spark for exactly this pattern. Note that the second argument to `reduceByKey` determines the number of reducers to use. By default, Spark assumes that the reduce function is commutative and associative and applies combiners on the mapper side. Since we know there is a very limited number of keys in this case (because there are only 24 unique dates in our data set), let’s use only 5 reducer.

	enKeyValuePairs.reduceByKey(_+_ , 5).collect

The output should look like this (one line for each hour):

	(20140101-02,546310)
	(20140101-16,1106656)
	(20140101-05,326838)
	(20140101-11,747836)
	(20140101-00,628478)
	(20140101-09,450499)
	(20140101-17,1145666)
	...
	(20140101-06,292195)

### More shuffling
Suppose we want to find pages that were viewed more than 50,000 times during the days covered by our dataset. Conceptually, this task is similar to the previous query. But, given the large number of pages (23 million distinct page names), the new task is very expensive. We are doing an expensive group-by with a lot of network shuffling of data.

To recap, first we split each line of data into its respective fields. Next, we extract the fields for page name and number of page views. We reduce by key again, this time with 40 reducers. Then we filter out pages with less than 50,000 total views over our time window represented by our dataset.

	enPages.map(l => l.split(" "))
		   .map(l => (l(2), l(3).toInt))
		   .reduceByKey(_+_, 40)
		   .filter(x => x._2 > 50000)
		   .map(x => (x._2, x._1))
		   .collect
		   .foreach(println)

The output should look like this:

	(607209,Photosynthèse)
	(1441138,Spécial:Page_au_hasard)
	(615936,ALBA)
	(615658,Capacité)
	(615757,Générateur)
	(213476,Stromae)
	(222574,YouTube)
	(616600,Domestique)
	(671299,Terre)
	(239148,Portail:Accueil)
	...

> There is no hard and fast way to calculate the optimal number of reducers for a given problem; you will build up intuition over time by experimenting with different values.


### Sampling
Sometimes (when doing exploratory data analysis over huge datasets), it's really useful to sample your dataset to extract a representative portion. This is how you would proceed to take only 1% of the big dataset with 12345 as a random seed.

	val pagecounts = sc.textFile("/data/pagecount_sm.dat" )
	val enPages = pagecounts.filter(_.split(" ")(1) == "en").sample(false, 0.01, 12345)
	enPages.count	

## Interactive dataframe analysis with spark shell

get the data if not yet done

    cd /tmp
    wget https://raw.githubusercontent.com/roberthryniewicz/datasets/master/svepisodes.json -O /tmp/svepisodes.json
    
launch a shell

    /usr/local/spark/bin/spark-shell

Load data into a Spark DataFrame

    val path = "/tmp/svepisodes.json"
    val svEpisodes = spark.read.json(path)         // Create a DataFrame from JSON data (automatically infer schema and data types)

Datasets and DataFrames are distributed collections of data created from a variety of sources: JSON and XML files, tables in Hive, external databases and more. Conceptually, they are equivalent to a table in a relational database or a DataFrame in R or Python. Key difference between the Dataset and the DataFrame is that Datasets are strongly typed.

There are complex manipulations possible on Datasets and DataFrames, however they are beyond this quick guide.

To learn more about Datasets and DataFrames checkout this link.

Print DataFrame Schema

    svEpisodes.printSchema()
    
    
### Data Description

Column Name	Description
1	Airdate	 Date when an episode was aired
2	Airstamp	Timestamp when an episode was aired
3	Airtime	 Length of an actual episode airtime (no commercials)
4	Id	     Unique show id
5	Name	 Name of an episode
6	Number	 Episode number
7	Runtime	 Total length of an episode (including commercials)
8	Season	 Show season
9	Summary	 Brief summary of an episode
10	Url	Url where more information is available online about an episode


show Datafram content


    svEpisodes.show()
    
create a temporary SQL view
    
    // Creates a temporary view
    svEpisodes.createOrReplaceTempView("svepisodes")

run basic SQL queries
    
    spark.sql("SELECT * FROM svepisodes ORDER BY season, number")
    
    +----------+--------------------+-------+------+--------------------+------+-------+------+--------------------+--------------------+
    |   airdate|            airstamp|airtime|    id|                name|number|runtime|season|             summary|                 url|
    +----------+--------------------+-------+------+--------------------+------+-------+------+--------------------+--------------------+
    |2014-04-06|2014-04-06T22:00:...|  22:00| 10897|Minimum Viable Pr...|     1|     30|     1|Attending an elab...|http://www.tvmaze...|
    |2014-04-13|2014-04-13T22:00:...|  22:00| 10898|       The Cap Table|     2|     30|     1|After a celebrato...|http://www.tvmaze...|
    |2014-04-20|2014-04-20T22:00:...|  22:00| 10899|Articles of Incor...|     3|     30|     1|While Gavin Belso...|http://www.tvmaze...|
    |2014-04-27|2014-04-27T22:00:...|  22:00| 10900|    Fiduciary Duties|     4|     30|     1|At Peter's toga p...|http://www.tvmaze...|
    |2014-05-04|2014-05-04T22:00:...|  22:00| 10901|      Signaling Risk|     5|     30|     1|Erlich  convinces...|http://www.tvmaze...|
    |2014-05-11|2014-05-11T22:00:...|  22:00| 10902|Third Party Insou...|     6|     30|     1|Richard feels thr...|http://www.tvmaze...|
    |2014-05-18|2014-05-18T22:00:...|  22:00| 10903|    Proof of Concept|     7|     30|     1|At TechCrunch Dis...|http://www.tvmaze...|
    |2014-06-01|2014-06-01T22:00:...|  22:00| 10904|Optimal Tip-to-Ti...|     8|     30|     1|Poised to compete...|http://www.tvmaze...|
    |2015-04-12|2015-04-12T22:00:...|  22:00|117409|   Sand Hill Shuffle|     1|     30|     2|Season 2 begins w...|http://www.tvmaze...|
    |2015-04-19|2015-04-19T22:00:...|  22:00|142992| Runaway Devaluation|     2|     30|     2|Pied Piper could ...|http://www.tvmaze...|
    |2015-04-26|2015-04-26T22:00:...|  22:00|142993|           Bad Money|     3|     30|     2|Richard mulls a p...|http://www.tvmaze...|
    |2015-05-03|2015-05-03T22:00:...|  22:00|142994|            The Lady|     4|     30|     2|Richard butts hea...|http://www.tvmaze...|
    |2015-05-10|2015-05-10T22:00:...|  22:00|153965|        Server Space|     5|     30|     2|Gavin creates int...|http://www.tvmaze...|
    |2015-05-17|2015-05-17T22:00:...|  22:00|154580|            Homicide|     6|     30|     2|Erlich runs into ...|http://www.tvmaze...|
    |2015-05-24|2015-05-24T22:00:...|  22:00|155129|       Adult Content|     7|     30|     2|The team fields j...|http://www.tvmaze...|
    |2015-05-31|2015-05-31T22:00:...|  22:00|155130| White Hat/Black Hat|     8|     30|     2|Richard gets para...|http://www.tvmaze...|
    |2015-06-07|2015-06-07T22:00:...|  22:00|155199| Binding Arbitration|     9|     30|     2|Erlich wants to t...|http://www.tvmaze...|
    |2015-06-14|2015-06-14T22:00:...|  22:00|155200|Two Days of The C...|    10|     30|     2|As the guys await...|http://www.tvmaze...|
    |2016-04-24|2016-04-24T22:00:...|  22:00|560883|    Founder Friendly|     1|     30|     3|After being uncer...|http://www.tvmaze...|
    |2016-05-01|2016-05-01T22:00:...|  22:00|668661|      Two in the Box|     2|     30|     3|The new and impro...|http://www.tvmaze...|
    +----------+--------------------+-------+------+--------------------+------+-------+------+--------------------+--------------------+
    
OK, so now let’s run a slightly more complex SQL query on the underlying table data.


Total Number of Episodes

    spark.sql("SELECT count(1) AS TotalNumEpisodes FROM svepisodes").show()
    
    +----------------+
    |TotalNumEpisodes|
    +----------------+
    |              28|
    +----------------+
    
    
Number of Episodes per Season
    
    spark.sql("SELECT season, count(number) as episodes FROM svepisodes GROUP BY season").show()
    
    +------+--------+
    |season|episodes|
    +------+--------+
    |     1|       8|
    |     3|      10|
    |     2|      10|
    +------+--------+
    
### Word Count on Episode Summaries

Now let’s perform a basic word-count on the summary column and find out which words occur most frequently. This should give us some indication on the popularity of certain characters and other relevant keywords in the context of the Sillicon Valley show.    

raw version

    import org.apache.spark.sql.functions._                        // Import additional helper functions
    
    val svSummaries = svEpisodes.select("summary").as[String]      // Convert to String type (becomes a Dataset)
    
    // Extract individual words
    val words = svSummaries
      .flatMap(_.split("\\s+"))                             // Split on whitespace
      .filter(_ != "")                                      // Remove empty words
      .map(_.toLowerCase())                                 // Lowercase
      
    words.show()
    
    +----------+
    |     value|
    +----------+
    | attending|
    |        an|
    | elaborate|
    |    launch|
    |    party,|
    |   richard|
    |       and|
    |       his|
    |  computer|
    |programmer|
    |   friends|
    |         -|
    |       big|
    |     head,|
    |    dinesh|
    |       and|
    |  gilfoyle|
    |         -|
    |     dream|
    |        of|
    +----------+
    
    // Word count
    words.groupByKey(value => value)                        // Group by word
         .count()                                           // Count
         .orderBy($"count(1)" desc)                         // Order by most frequent
         .show()                                            // Display results
         
    +-------+--------+
    |  value|count(1)|
    +-------+--------+
    |      a|      59|
    |     to|      48|
    |    and|      40|
    |    the|      38|
    |richard|      26|
    |   pied|      24|
    |     of|      16|
    | erlich|      16|
    |    his|      16|
    | dinesh|      16|
    |  gavin|      14|
    |     at|      14|
    |    for|      12|
    |  piper|      12|
    |     in|      12|
    |     by|      12|
    |    big|      11|
    |  jared|      11|
    |  about|      10|
    |   with|       9|
    +-------+--------+
    
 As you can see there are plenty of stop words and punctuation marks that surface to the top. Let’s clean this up a bit by creating a basic stop word list and a punctuation mark list that we’ll use as basic filters before we aggregate and order the words again.
 
    val stopWords = List("a", "an", "to", "and", "the", "of", "in", "for", "by", "at")      // Basic set of stop words
    val punctuationMarks = List("-", ",", ";", ":", ".", "?", "!")                          // Basic set of punctuation marks
    
    // Filter out stop words and punctuation marks
    val wordsFiltered = words                                                               // Create a new Dataset
        .filter(!stopWords.contains(_))                                                     // Remove stop words
        .filter(!punctuationMarks.contains(_))                                              // Remove punctuation marks

    // Word count
    wordsFiltered
      .groupBy($"value" as "word")                          // Group on values (default) column name
      .agg(count("*") as "occurences")                      // Aggregate
      .orderBy($"occurences" desc)                          // Display most common words first
      .show()                                               // Display results
      
      
    +----------+----------+
    |      word|occurences|
    +----------+----------+
    |   richard|        26|
    |      pied|        24|
    |       his|        16|
    |    erlich|        16|
    |    dinesh|        16|
    |     gavin|        14|
    |     piper|        12|
    |     jared|        11|
    |       big|        11|
    |     about|        10|
    |  gilfoyle|         9|
    |      guys|         9|
    |      with|         9|
    |      when|         9|
    |        is|         9|
    |      that|         9|
    |    monica|         9|
    |       new|         9|
    |meanwhile,|         8|
    |   piper's|         8|
    +----------+----------+