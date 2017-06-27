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
package com.hurence.logisland.processor;

import com.hurence.logisland.annotation.documentation.CapabilityDescription;
import com.hurence.logisland.annotation.documentation.Tags;
import com.hurence.logisland.component.PropertyDescriptor;
import com.hurence.logisland.logging.ComponentLog;
import com.hurence.logisland.logging.StandardComponentLogger;
import com.hurence.logisland.record.FieldDictionary;
import com.hurence.logisland.record.FieldType;
import com.hurence.logisland.record.Record;
import com.hurence.logisland.util.time.DateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.*;


@Tags({"record", "fields", "post-process", "binetflow", "date"})
@CapabilityDescription("Post processing step to update a dte field in a custom way")
public class UpdateBiNetflowDate extends AbstractProcessor {

    private final ComponentLog logger = new StandardComponentLogger("UpdateBiNetflowDate", this);


    @Override
    public Collection<Record> process(ProcessContext context, Collection<Record> records) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.S");
        sdf.setTimeZone(TimeZone.getTimeZone("GMT+1"));
        for (Record outputRecord : records) {


            try {
                String eventTimeString = outputRecord.getField("timestamp").asString();
                Date eventDate = sdf.parse(eventTimeString.substring(0, eventTimeString.length() -3));

                if (eventDate != null) {
                    outputRecord.setField(FieldDictionary.RECORD_TIME, FieldType.LONG, eventDate.getTime() - 60*60*1000);
                }
            } catch (Exception e) {
                outputRecord.addError("unable to parse date", logger, e.toString());
            }

        }
        return records;
    }

    @Override
    public List<PropertyDescriptor> getSupportedPropertyDescriptors() {
        return Collections.emptyList();
    }
}

