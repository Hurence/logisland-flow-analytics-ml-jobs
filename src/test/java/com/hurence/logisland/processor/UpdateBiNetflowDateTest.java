package com.hurence.logisland.processor;

import com.hurence.logisland.processor.util.BaseSyslogTest;
import com.hurence.logisland.record.FieldDictionary;
import com.hurence.logisland.record.FieldType;
import com.hurence.logisland.record.Record;
import com.hurence.logisland.record.StandardRecord;
import com.hurence.logisland.util.runner.MockRecord;
import com.hurence.logisland.util.runner.TestRunner;
import com.hurence.logisland.util.runner.TestRunners;
import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Test;

import java.util.Date;

public class UpdateBiNetflowDateTest extends BaseSyslogTest {

    private static final Logger logger = Logger.getLogger(UpdateBiNetflowDateTest.class);

    private static final Date PROCESSING_DATE = new Date();


    private Record getRecord() {
        Record record1 = new StandardRecord();
        record1.setField("string1", FieldType.STRING, "value1");
        record1.setField("string2", FieldType.STRING, "value2");
        record1.setField("long1", FieldType.LONG, 1);
        record1.setField("long2", FieldType.LONG, 2);

        record1.setField(FieldDictionary.RECORD_TIME, FieldType.LONG, PROCESSING_DATE.getTime());
        record1.setField(FieldDictionary.RECORD_TYPE, FieldType.STRING, "bi_netflow");

        record1.setField("timestamp", FieldType.STRING, "2011/08/10 12:56:20.942510");


        return record1;
    }

    @Test
    public void testDateConvertion() throws Exception {


        TestRunner testRunner = TestRunners.newTestRunner(new UpdateBiNetflowDate());
        testRunner.assertValid();
        testRunner.enqueue(getRecord());
        testRunner.run();
        testRunner.assertAllInputRecordsProcessed();
        testRunner.assertOutputRecordsCount(1);
        MockRecord record = testRunner.getOutputRecords().get(0);


        System.out.println(new Date(record.getField(FieldDictionary.RECORD_TIME).asLong()));

        record.assertFieldEquals(FieldDictionary.RECORD_TIME, 1312973780942L);


    }


}
