# Big data messaging with Kafka


Build a continuous big data messaging system with Kafka
               
**credits** : Sunil Patil, Software Engineer, JavaWorld | APR 25, 2016 


## An introduction

When the big data movement started it was mostly focused on batch processing. Distributed data storage and querying tools like MapReduce, Hive, and Pig were all designed to process data in batches rather than continuously. Businesses would run multiple jobs every night to extract data from a database, then analyze, transform, and eventually store the data. More recently enterprises have discovered the power of analyzing and processing data and events as they happen, not just once every few hours. Most traditional messaging systems don't scale up to handle big data in realtime, however. So engineers at LinkedIn built and open-sourced Kafka: a distributed messaging framework that meets the demands of big data by scaling on commodity hardware.

Over the past few years, Kafka has emerged to solve a variety of use cases. In the simplest case, it could be a simple buffer for storing application logs. Combined with a technology like Spark Streaming, it can be used to track data changes and take action on that data before saving it to a final destination. Kafka's predictive mode makes it a powerful tool for detecting fraud, such as checking the validity of a credit card transaction when it happens, and not waiting for batch processing hours later.

This two-part tutorial introduces Kafka, starting with how to install and run it in your development environment. You'll get an overview of Kafka's architecture, followed by an introduction to developing an out-of-the-box Kafka messaging system. Finally, you'll build a custom producer/consumer application that sends and consumes messages via a Kafka server. In the second half of the tutorial you'll learn how to partition and group messages, and how to control which messages a Kafka consumer will consume.

### What is Kafka?

Apache Kafka is messaging system built to scale for big data. Similar to Apache ActiveMQ or RabbitMq, Kafka enables applications built on different platforms to communicate via asynchronous message passing. But Kafka differs from these more traditional messaging systems in key ways:

- It's designed to scale horizontally, by adding more commodity servers.
- It provides much higher throughput for both producer and consumer processes.
- It can be used to support both batch and real-time use cases.
- It doesn't support JMS, Java's message-oriented middleware API.

### Kafka's architecture

Before we explore Kafka's architecture, you should know its basic terminology:

- A producer is process that can publish a message to a topic.
- a consumer is a process that can subscribe to one or more topics and consume messages published to topics.
- A topic category is the name of the feed to which messages are published.
- A broker is a process running on single machine.
- A cluster is a group of brokers working together.

Figure 1: Kafka's architecture Figure 1. Architecture of a Kafka message system


Kafka's architecture is very simple, which can result in better performance and throughput in some systems. Every topic in Kafka is like a simple log file. When a producer publishes a message, the Kafka server appends it to the end of the log file for its given topic. The server also assigns an offset, which is a number used to permanently identify each message. As the number of messages grows, the value of each offset increases; for example if the producer publishes three messages the first one might get an offset of 1, the second an offset of 2, and the third an offset of 3.

When the Kafka consumer first starts, it will send a pull request to the server, asking to retrieve any messages for a particular topic with an offset value higher than 0. The server will check the log file for that topic and return the three new messages. The consumer will process the messages, then send a request for messages with an offset higher than 3, and so on.

In Kafka, the client is responsible for remembering the offset count and retrieving messages.The Kafka server doesn't track or manage message consumption. By default, a Kafka server will keep a message for seven days. A background thread in the server checks and deletes messages that are seven days or older. A consumer can access messages as long as they are on the server. It can read a message multiple times, and even read messages in reverse order of receipt. But if the consumer fails to retrieve the message before the seven days are up, it will miss that message.

### Kafka benchmarks
Production use by LinkedIn and other enterprises has shown that with proper configuration Kafka is capable of processing hundreds of gigabytes of data daily. In 2011, three LinkedIn engineers used benchmark testing to demonstrate that Kafka could achieve much higher throughput than ActiveMQ and RabbitMQ.

[http://bravenewgeek.com/benchmarking-message-queue-latency/](http://bravenewgeek.com/benchmarking-message-queue-latency/)


## Quick setup and demo

We'll build a custom application in this tutorial, but let's start by installing and testing a Kafka instance with an out-of-the-box producer and consumer.

1. Visit the Kafka download page to install the most recent version. Extract the binaries into a software/kafka folder. Change your current directory to point to the new folder.

        wget http://mirrors.ircam.fr/pub/apache/kafka/0.10.2.0/kafka_2.11-0.10.2.0.tgz
        tar -xzf kafka_2.11-0.10.2.0.tgz
        cd kafka_2.11-0.10.2.0

2. start a ZooKeeper server if you don't already have one.

	    bin/zookeeper-server-start.sh config/zookeeper.properties

3. Now start the Kafka server:

	    bin/kafka-server-start.sh config/server.properties
	
4. Create a test topic that you can use for testing: 

        bin/kafka-topics.sh --create --zookeeper localhost:2181 \
            --replication-factor 1 --partitions 1 --topic javaworld.
    
5. Start a simple console consumer that can consume messages published to a given topic, such as javaworld: 

        bin/kafka-console-consumer.sh --zookeeper localhost:2181 \
            --topic javaworld --from-beginning.
    
6. Start up a simple producer console that can publish messages to the test topic: 

        bin/kafka-console-producer.sh --broker-list localhost:9092 \
            --topic javaworld.
        
7. Try typing one or two messages into the producer console. Your messages should show in the consumer console.


## A simple producer/consumer application

You've seen how Kafka works out of the box. Next, let's develop a custom producer/consumer application. The producer will retrieve user input from the console and send each new line as a message to a Kafka server. The consumer will retrieve messages for a given topic and print them to the console. The producer and consumer components in this case are your own implementations of kafka-console-producer.sh and kafka-console-consumer.sh.

Let's start by creating a Producer.java class. This client class contains logic to read user input from the console and send that input as a message to the Kafka server.

We configure the producer by creating an object from the java.util.Properties class and setting its properties. The ProducerConfig class defines all the different properties available, but Kafka's default values are sufficient for most uses. For the default config we only need to set three mandatory properties:

- BOOTSTRAP_SERVERS_CONFIG
- KEY_SERIALIZER_CLASS_CONFIG
- VALUE_SERIALIZER_CLASS_CONFIG

BOOTSTRAP_SERVERS_CONFIG (bootstrap.servers) sets a list of host:port pairs used for establishing the initial connections to the Kakfa cluster in the host1:port1,host2:port2,... format. Even if we have more than one broker in our Kafka cluster, we only need to specify the value of the first broker's host:port. The Kafka client will use this value to make a discover call on the broker, which will return a list of all the brokers in the cluster. It's a good idea to specify more than one broker in the BOOTSTRAP_SERVERS_CONFIG, so that if that first broker is down the client will be able to try other brokers.

The Kafka server expects messages in byte[] key, byte[] value format. Rather than converting every key and value, Kafka's client-side library permits us to use friendlier types like String and int for sending messages. The library will convert these to the appropriate type. For example, the sample app doesn't have a message-specific key, so we'll use null for the key. For the value we'll use a String, which is the data entered by the user on the console.

To configure the message key, we set a value of KEY_SERIALIZER_CLASS_CONFIG on the org.apache.kafka.common.serialization.ByteArraySerializer. This works because null doesn't need to be converted into byte[]. For the message value, we set VALUE_SERIALIZER_CLASS_CONFIG on the org.apache.kafka.common.serialization.StringSerializer, because that class knows how to convert a String into a byte[].

Custom key/value objects
Similar to StringSerializer, Kafka provides serializers for other primitives such as int and long. In order to use a custom object for our key or value, we would need to create a class implementing org.apache.kafka.common.serialization.Serializer. We could then add logic to serialize the class into byte[]. We would also have to use a corresponding deserializer in our consumer code.


### The Kafka producer

After filling the Properties class with the necessary configuration properties, we can use it to create an object of KafkaProducer. Whenever we want to send a message to the Kafka server after that, we'll create an object of ProducerRecord and call the KafkaProducer's send() method with that record to send the message. The ProducerRecord takes two parameters: the name of the topic to which message should be published, and the actual message. Don't forget to call the Producer.close() method when you're done using the producer:

Listing 1. KafkaProducer


        public class Producer {
          private static Scanner in;
          public static void main(String[] argv)throws Exception {
              if (argv.length != 1) {
                  System.err.println("Please specify 1 parameters ");
                  System.exit(-1);
              }
              String topicName = argv[0];
              in = new Scanner(System.in);
              System.out.println("Enter message(type exit to quit)");

              //Configure the Producer
              Properties configProperties = new Properties();
              configProperties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,"localhost:9092");
              configProperties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,"org.apache.kafka.common.serialization.ByteArraySerializer");
              configProperties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,"org.apache.kafka.common.serialization.StringSerializer");

              org.apache.kafka.clients.producer.Producer producer = new KafkaProducer<String, String>(configProperties);
              String line = in.nextLine();
              while(!line.equals("exit")) {
                  ProducerRecord<String, String> rec = new ProducerRecord<String, String>(topicName, line);
                  producer.send(rec);
                  line = in.nextLine();
              }
              in.close();
              producer.close();
          }
        }
      
    
### Configuring the message consumer

Next we'll create a simple consumer that subscribes to a topic. Whenever a new message is published to the topic, it will read that message and print it to the console. The consumer code is quite similar to the producer code. We start by creating an object of java.util.Properties, setting its consumer-specific properties, and then using it to create a new object of KafkaConsumer. The ConsumerConfig class defines all the properties that we can set. There are just four mandatory properties:

- BOOTSTRAP_SERVERS_CONFIG (bootstrap.servers)
- KEY_DESERIALIZER_CLASS_CONFIG (key.deserializer)
- VALUE_DESERIALIZER_CLASS_CONFIG (value.deserializer)
- GROUP_ID_CONFIG (bootstrap.servers)

Just as we did for the producer class, we'll use BOOTSTRAP_SERVERS_CONFIG to configure the host/port pairs for the consumer class. This config lets us establish the initial connections to the Kakfa cluster in the host1:port1,host2:port2,... format.

As I previously noted, the Kafka server expects messages in byte[] key and byte[] value formats, and has its own implementation for serializing different types into byte[]. Just as we did with the producer, on the consumer side we'll have to use a custom deserializer to convert byte[] back into the appropriate type.

In the case of the example application, we know the producer is using ByteArraySerializer for the key and StringSerializer for the value. On the client side we therefore need to use org.apache.kafka.common.serialization.ByteArrayDeserializer for the key and org.apache.kafka.common.serialization.StringDeserializer for the value. Setting those classes as values for KEY_DESERIALIZER_CLASS_CONFIG and VALUE_DESERIALIZER_CLASS_CONFIG will enable the consumer to deserialize byte[] encoded types sent by the producer.

Finally, we need to set the value of the GROUP_ID_CONFIG. This should be a group name in string format. I'll explain more about this config in a minute. For now, just look at the Kafka consumer with the four mandatory properties set:



Listing 2. KafkaConsumer


    public class Consumer {
      private static Scanner in;
      private static boolean stop = false;
    
      public static void main(String[] argv)throws Exception{
          if (argv.length != 2) {
              System.err.printf("Usage: %s <topicName> <groupId>\n",
                      Consumer.class.getSimpleName());
              System.exit(-1);
          }
          in = new Scanner(System.in);
          String topicName = argv[0];
          String groupId = argv[1];
    
          ConsumerThread consumerRunnable = new ConsumerThread(topicName,groupId);
          consumerRunnable.start();
          String line = "";
          while (!line.equals("exit")) {
              line = in.next();
          }
          consumerRunnable.getKafkaConsumer().wakeup();
          System.out.println("Stopping consumer .....");
          consumerRunnable.join();
      }
    
      private static class ConsumerThread extends Thread{
          private String topicName;
          private String groupId;
          private KafkaConsumer<String,String> kafkaConsumer;
    
          public ConsumerThread(String topicName, String groupId){
              this.topicName = topicName;
              this.groupId = groupId;
          }
          public void run() {
              Properties configProperties = new Properties();
              configProperties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
              configProperties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
              configProperties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
              configProperties.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
              configProperties.put(ConsumerConfig.CLIENT_ID_CONFIG, "simple");
    
              //Figure out where to start processing messages from
              kafkaConsumer = new KafkaConsumer<String, String>(configProperties);
              kafkaConsumer.subscribe(Arrays.asList(topicName));
              //Start processing messages
              try {
                  while (true) {
                      ConsumerRecords<String, String> records = kafkaConsumer.poll(100);
                      for (ConsumerRecord<String, String> record : records)
                          System.out.println(record.value());
                  }
              }catch(WakeupException ex){
                  System.out.println("Exception caught " + ex.getMessage());
              }finally{
                  kafkaConsumer.close();
                  System.out.println("After closing KafkaConsumer");
              }
          }
          public KafkaConsumer<String,String> getKafkaConsumer(){
             return this.kafkaConsumer;
          }
      }
    }
  
### Consumer and ConsumerThread

Writing the consumer code in Listing 2 in two parts ensures that we close the Consumer object before exiting. I'll describe each class in turn. First, ConsumerThread is an inner class that takes a topic name and group name as its arguments. In the run() method it creates a KafkaConsumer object, with appropriate properties. It subscribes to the topic that was passed as an argument in the constructor, by calling the kafkaConsumer.subscribe() method, then polls the Kafka server every 100 milliseconds to check if there are any new messages in the topic. It will iterate through the list of any new messages and print them to the console.

In the Consumer class we create a new object of ConsumerThread and start it in a different thread. The ConsumerThead starts an infinite loop and keeps polling the topic for new messages. Meanwhile in the Consumer class, the main thread waits for a user to enter exit on the console. Once a user enters exit, it calls the KafkaConsumer.wakeup() method, causing the KafkaConsumer to stop polling for new messages and throw a WakeupException. We can then close the KafkaConsumer gracefully, by calling kafkaConsumer's close() method.


### Run the application

To test this application you can run the code in Listings 1 and 2 from your IDE, or you can follow these steps:

1. Download the sample code, by executing the command: 

        git clone https://github.com/Hurence/logisland-flow-analytics-ml-jobs.git

2. Compile the code and create a fat JAR with the command: 

        mvn clean compile assembly:single
    
3. Start the consumer: 

        java -cp target/logisland-flow-analytics-ml-jobs-0.10.1-jar-with-dependencies.jar \
            com.hurence.logisland.kafka.simple.Consumer plik group1

4. Start the producer: 

        java -cp target/logisland-flow-analytics-ml-jobs-0.10.1-jar-with-dependencies.jar \
            com.hurence.logisland.kafka.simple.Producer plik

5. Enter a message in the producer console and check to see whether that message appears in the consumer. Try a few messages.
Type exit in the consumer and producer consoles to close them.


### Conclusion

In the first half of this tutorial you've learned the basics of big data messaging with Kafka, including a conceptual overview of Kafka, setup instructions, and how to configure a producer/consumer messaging system with Kafka.

As you've seen, Kafka's architecture is both simple and efficient, designed for performance and throughput. 



## Use Kafka's partitions, message offsets, and consumer groups to handle up to millions of messages per day
                  
you'll learn how to use partitions to distribute load and scale your application horizontally, handling up to millions of messages per day. You'll also learn how Kafka uses message offsets to track and manage complex message processing, and how to protect your Kafka messaging system against failure should a consumer go down. We'll develop the example application from Part 1 for both publish-subscribe and point-to-point use cases.
   
### Partitions in Kafka
   
Topics in Kafka can be subdivided into partitions. For example, while creating a topic named Demo, you might configure it to have three partitions. The server would create three log files, one for each of the demo partitions. When a producer published a message to the topic, it would assign a partition ID for that message. The server would then append the message to the log file for that partition only.

If you then started two consumers, the server might assign partitions 1 and 2 to the first consumer, and partition 3 to the second consumer. Each consumer would read only from its assigned partitions. You can see the Demo topic configured for three partitions in Figure 1.

A partitioned topic in Apache Kafka
Figure 1. A partitioned topic in Apache Kafka
To expand the scenario, imagine a Kafka cluster with two brokers, housed in two machines. When you partitioned the demo topic, you would configure it to have two partitions and two replicas. For this type of configuration, the Kafka server would assign the two partitions to the two brokers in your cluster. Each broker would be the leader for one of the partitions.

When a producer published a message, it would go to the partition leader. The leader would take the message and append it to the log file on the local machine. The second broker would passively replicate that commit log to its own machine. If the partition leader went down, the second broker would become the new leader and start serving client requests. In the same way, when a consumer sent a request to a partition, that request would go first to the partition leader, which would return the requested messages.
   
### Benefits of partitioning
   
Consider the benefits of partitioning a Kafka-based messaging system:
   
- **Scalability** : In a system with just one partition, messages published to a topic are stored in a log file, which exists on a single machine. The number of messages for a topic must fit into a single commit log file, and the size of messages stored can never be more than that machine's disk space. Partitioning a topic lets you scale your system by storing messages on different machines in a cluster. If you wanted to store 30 gigabytes (GB) of messages for the Demo topic, for instance, you could build a Kafka cluster of three machines, each with 10 GB of disk space. Then you would configure the topic to have three partitions.
- **Server-load balancing** : Having multiple partitions lets you spread message requests across brokers. For example, If you had a topic that processed 1 million messages per second, you could divide it into 100 partitions and add 100 brokers to your cluster. Each broker would be the leader for single partition, responsible for responding to just 10,000 client requests per second.
- **Consumer-load balancing** : Similar to server-load balancing, hosting multiple consumers on different machine lets you spread the consumer load. Let's say you wanted to consume 1 million messages per second from a topic with 100 partitions. You could create 100 consumers and run them in parallel. The Kafka server would assign one partition to each of the consumers, and each consumer would process 10,000 messages in parallel. Since Kafka assigns each partition to only one consumer, within the partition each message would be consumed in order.


### Two ways to partition
   
The producer is responsible for deciding what partition a message will go to. The producer has two options for controlling this assignment:
   
- **Custom partitioner**: You can create a class implementing the `org.apache.kafka.clients.producer.Partitioner` interface. This custom Partitioner will implement the business logic to decide where messages are sent.
- **DefaultPartitioner**: If you don't create a custom partitioner class, then by default the `org.apache.kafka.clients.producer.internals.DefaultPartitioner` class will be used. The default partitioner is good enough for most cases, providing three options:
    - **Manual**: When you create a `ProducerRecord`, use the overloaded constructor `new ProducerRecord(topicName, partitionId,messageKey,message)` to specify a partition ID.
    - **Hashing(Locality sensitive)**: When you create a ProducerRecord, specify a messageKey, by calling new ProducerRecord(topicName,messageKey,message). `DefaultPartitioner` will use the hash of the key to ensure that all messages for the same key go to same producer. This is the easiest and most common approach.
    - **Spraying(Random Load Balancing)**: If you don't want to control which partition messages go to, simply call `new ProducerRecord(topicName, message)` to create your `ProducerRecord`. In this case the partitioner will send messages to all the partitions in round-robin fashion, ensuring a balanced server load.

### Partitioning a Kafka application
   
For the simple producer/consumer example in Part 1, we used a `DefaultPartitioner`. Now we'll try creating a custom partitioner instead. For this example, let's assume that we have a retail site that consumers can use to order products anywhere in the world. Based on usage, we know that most consumers are in either the United States or India. We want to partition our application to send orders from the US or India to their own respective consumers, while orders from anywhere else will go to a third consumer.
   
To start, we'll create a `CountryPartitioner` that implements the `org.apache.kafka.clients.producer.Partitioner` interface. We must implement the following methods:
   
Kafka will call configure() when we initialize the `Partitioner` class, with a Map of configuration properties. This method initializes functions specific to the application's business logic, such as connecting to a database. In this case we want a fairly generic partitioner that takes countryName as a property. We can then use `configProperties.put("partitions.0","USA")` to map the flow of messages to partitions. In the future we can use this format to change which countries get their own partition.
The Producer API calls `partition()` once for every message. In this case we'll use it to read the message and parse the name of the country from the message. If the name of the country is in the `countryToPartitionMap`, it will return partitionId stored in the Map. If not, it will hash the value of the country and use it to calculate which partition it should go to.
We call `close()` to shut down the partitioner. Using this method ensures that any resources acquired during initialization are cleaned up during shutdown.
Note that when Kafka calls configure(), the Kafka producer will pass all the properties that we've configured for the producer to the `Partitioner` class. It is essential that we read only those properties that start with partitions., parse them to get the partitionId, and store the ID in `countryToPartitionMap`.
   
Below is our custom implementation of the Partitioner interface.
   
Listing 1. CountryPartitioner
   
       
    public class CountryPartitioner implements Partitioner {
       private static Map<String,Integer> countryToPartitionMap;
    
       public void configure(Map<String, ?> configs) {
           System.out.println("Inside CountryPartitioner.configure " + configs);
           countryToPartitionMap = new HashMap<String, Integer>();
           for(Map.Entry<String,?> entry: configs.entrySet()){
               if(entry.getKey().startsWith("partitions.")){
                   String keyName = entry.getKey();
                   String value = (String)entry.getValue();
                   System.out.println( keyName.substring(11));
                   int paritionId = Integer.parseInt(keyName.substring(11));
                   countryToPartitionMap.put(value,paritionId);
               }
           }
       }
    
       public int partition(String topic, Object key, byte[] keyBytes, Object value, byte[] valueBytes,
                            Cluster cluster) {
           List partitions = cluster.availablePartitionsForTopic(topic);
           String valueStr = (String)value;
           String countryName = ((String) value).split(":")[0];
           if(countryToPartitionMap.containsKey(countryName)){
               //If the country is mapped to particular partition return it
               return countryToPartitionMap.get(countryName);
           }else {
               //If no country is mapped to particular partition distribute between remaining partitions
               int noOfPartitions = cluster.topics().size();
               return  value.hashCode()%noOfPartitions + countryToPartitionMap.size() ;
           }
       }
    
       public void close() {}
    }
       
The Producer class in Listing 2 (below) is very similar to our simple producer from Part 1, with two changes marked in bold:

We set a config property with a key equal to the value of `ProducerConfig.PARTITIONER_CLASS_CONFIG`, which matches the fully qualified name of our `CountryPartitioner` class. We also set countryName to partitionId, thus mapping the properties that we want to pass to `CountryPartitioner`.
We pass an instance of a class implementing the `org.apache.kafka.clients.producer.Callback` interface as a second argument to the `producer.send()` method. The Kafka client will call its onCompletion() method once a message is successfully published, attaching a `RecordMetadata` object. We'll be able to use this object to find out which partition a message was sent to, as well as the offset assigned to the published message.

Listing 2. A partitioned producer
   
   
    public class Producer {
       private static Scanner in;
       public static void main(String[] argv)throws Exception {
           if (argv.length != 1) {
               System.err.println("Please specify 1 parameters ");
               System.exit(-1);
           }
           String topicName = argv[0];
           in = new Scanner(System.in);
           System.out.println("Enter message(type exit to quit)");
    
           //Configure the Producer
           Properties configProperties = new Properties();
           configProperties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,"localhost:9092");
           configProperties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,"org.apache.kafka.common.serialization.ByteArraySerializer");
           configProperties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,"org.apache.kafka.common.serialization.StringSerializer");
    
               configProperties.put(ProducerConfig.PARTITIONER_CLASS_CONFIG,CountryPartitioner.class.getCanonicalName());
           configProperties.put("partition.1","USA");
           configProperties.put("partition.2","India");
           
           org.apache.kafka.clients.producer.Producer producer = new KafkaProducer(configProperties);
           String line = in.nextLine();
           while(!line.equals("exit")) {
               ProducerRecord<String, String> rec = new ProducerRecord<String, String>(topicName, null, line);
               producer.send(rec, new Callback() {
                   public void onCompletion(RecordMetadata metadata, Exception exception) {
                       System.out.println("Message sent to topic ->" + metadata.topic()+ " ,parition->" + metadata.partition() +" stored at offset->" + metadata.offset());
    ;
                   }
               });
               line = in.nextLine();
           }
           in.close();
           producer.close();
       }
    }
   
### Assigning partitions to consumers
   
The Kafka server guarantees that a partition is assigned to only one consumer, thereby guaranteeing the order of message consumption. You can manually assign a partition or have it assigned automatically.

If your business logic demands more control, then you'll need to manually assign partitions. In this case you would use `KafkaConsumer.assign(<listOfPartitions>)` to pass a list of partitions that each consumer was interested in to the Kafka server.

Having partitions assigned automatically is the default and most common choice. In this case, the Kafka server will assign a partition to each consumer, and will reassign partitions to scale for new consumers.

Say you're creating a new topic with three partitions. When you start the first consumer for the new topic, Kafka will assign all three partitions to the same consumer. If you then start a second consumer, Kafka will reassign all the partitions, assigning one partition to the first consumer and the remaining two partitions to the second consumer. If you add a third consumer, Kafka will reassign the partitions again, so that each consumer is assigned a single partition. Finally, if you start fourth and fifth consumers, then three of the consumers will have an assigned partition, but the others won't receive any messages. If one of the initial three partitions goes down, Kafka will use the same partitioning logic to reassign that consumer's partition to one of the additional consumers.


We'll use automatic assignment for the example application. Most of our consumer code will be the same as it was for the simple consumer seen in Part 1. The only difference is that we'll pass an instance of ConsumerRebalanceListener as a second argument to our KafkaConsumer.subscribe() method. Kafka will call methods of this class every time it either assigns or revokes a partition to this consumer. We'll override ConsumerRebalanceListener's onPartitionsRevoked() and onPartitionsAssigned() methods and print the list of partitions that were assigned or revoked from this subscriber.

Listing 3. A partitioned consumer

  
    private static class ConsumerThread extends Thread {
        private String topicName;
        private String groupId;
        private KafkaConsumer<String, String> kafkaConsumer;
        
        public ConsumerThread(String topicName, String groupId) {
            this.topicName = topicName;
            this.groupId = groupId;
        }
        
        public void run() {
             Properties configProperties = new Properties();
             configProperties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
             configProperties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
             configProperties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
             configProperties.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
            
             //Figure out where to start processing messages from
             kafkaConsumer = new KafkaConsumer<String, String>(configProperties);
             kafkaConsumer.subscribe(Arrays.asList(topicName), new ConsumerRebalanceListener() {
                 public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
                     System.out.printf("%s topic-partitions are revoked from this consumer\n", Arrays.toString(partitions.toArray()));
                 }
                 public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
                     System.out.printf("%s topic-partitions are assigned to this consumer\n", Arrays.toString(partitions.toArray()));
                 }
             });
             //Start processing messages
             try {
                 while (true) {
                     ConsumerRecords<String, String> records = kafkaConsumer.poll(100);
                     for (ConsumerRecord<String, String> record : records)
                         System.out.println(record.value());
                 }
             } catch (WakeupException ex) {
                 System.out.println("Exception caught " + ex.getMessage());
             } finally {
                 kafkaConsumer.close();
                 System.out.println("After closing KafkaConsumer");
             }
            }
            
            public KafkaConsumer<String, String> getKafkaConsumer() {
             return this.kafkaConsumer;
            }
    }
   
   
### Test the application

We're ready to run and test the current iteration of our producer/consumer application. As you've done previously, you can use the code in Listings 1 through 3, or download the complete source code on GitHub.

1. Compile and create a fat JAR by invoking: 

        mvn compile assembly:single.

2. Create a topic named part-demo with three partitions and one replication factor:

        $KAFKA_HOME/bin/kafka-topics.sh --create --zookeeper localhost:2181 \ 
            --replication-factor 1 --partitions 3 --topic part-demo
  
3. Start a producer:

        java -cp target/logisland-flow-analytics-ml-jobs-0.10.1-jar-with-dependencies.jar \
            com.hurence.logisland.kafka.partition.Producer part-demo

4. Start three consumers, then watch the console to see how your partitions are assigned and revoked every time you start a new instance of the consumer:

        java -cp target/logisland-flow-analytics-ml-jobs-0.10.1-jar-with-dependencies.jar \
          com.hurence.logisland.kafka.partition.Consumer part-demo group1

5. Type some messages into your producer console and verify whether the messages are routed to the correct consumer:

        USA: First order
        India: First order
        USA: Second order
        France: First order

Being able to partition a single topic into multiple parts is one essential to Kafka's scalability. Partitioning lets you scale your messaging infrastructure horizontally while also maintaining order within each partition.