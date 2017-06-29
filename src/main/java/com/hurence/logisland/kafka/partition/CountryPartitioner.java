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

import org.apache.kafka.clients.producer.Partitioner;
import org.apache.kafka.common.Cluster;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CountryPartitioner implements Partitioner {
    private static Map<String,Integer> countryToPartitionMap;

    // This method will gets called at the start, you should use it to do one time startup activity
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

    //This method will get called once for each message
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

    // This method will get called at the end and gives your partitioner class chance to cleanup
    public void close() {}
}