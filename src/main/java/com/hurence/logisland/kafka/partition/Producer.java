/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hurence.logisland.kafka.partition;

import org.apache.kafka.clients.producer.*;

import java.util.Properties;
import java.util.Scanner;

/**
 * Created by sunilpatil on 1/2/16.
 */
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
        configProperties.put("partitions.0","USA");
        configProperties.put("partitions.1","India");

        org.apache.kafka.clients.producer.Producer producer = new KafkaProducer(configProperties);
        String line = in.nextLine();
        while(!line.equals("exit")) {
            ProducerRecord<String, String> rec = new ProducerRecord<String, String>(topicName, line);

            producer.send(rec, new Callback() {
                public void onCompletion(RecordMetadata metadata, Exception exception) {
                    System.out.println("Message sent to topic ->" + metadata.topic()+ " ,parition->" + metadata.partition() +" stored at offset->" + metadata.offset());
                }
            });
            line = in.nextLine();
        }
        in.close();
        producer.close();
    }
}