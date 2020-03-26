/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.pinot.common.data;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.concurrent.TimeUnit;
import org.apache.pinot.common.utils.SchemaUtils;
import org.apache.pinot.spi.data.DateTimeFieldSpec;
import org.apache.pinot.spi.data.DimensionFieldSpec;
import org.apache.pinot.spi.data.FieldSpec;
import org.apache.pinot.spi.data.MetricFieldSpec;
import org.apache.pinot.spi.data.Schema;
import org.apache.pinot.spi.data.TimeFieldSpec;
import org.apache.pinot.spi.data.TimeGranularitySpec;
import org.apache.pinot.spi.data.TimeGranularitySpec.TimeFormat;
import org.apache.pinot.spi.utils.BytesUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;


public class SchemaTest {
  public static final Logger LOGGER = LoggerFactory.getLogger(SchemaTest.class);

  @Test
  public void testValidation()
      throws Exception {
    Schema schemaToValidate;

    schemaToValidate = Schema.fromString(makeSchema(FieldSpec.DataType.LONG, FieldSpec.DataType.STRING, true));
    Assert.assertTrue(schemaToValidate.validate(LOGGER));

    schemaToValidate = Schema.fromString(makeSchema(FieldSpec.DataType.BOOLEAN, FieldSpec.DataType.STRING, true));
    Assert.assertFalse(schemaToValidate.validate(LOGGER));

    schemaToValidate = Schema.fromString(makeSchema(FieldSpec.DataType.STRING, FieldSpec.DataType.STRING, false));
    Assert.assertFalse(schemaToValidate.validate(LOGGER));

    schemaToValidate = Schema.fromString(makeSchema(FieldSpec.DataType.LONG, FieldSpec.DataType.BOOLEAN, false));
    Assert.assertTrue(schemaToValidate.validate(LOGGER));
  }

  private String makeSchema(FieldSpec.DataType metricType, FieldSpec.DataType dimensionType, boolean isSingleValue) {
    return String.format("{"
        + "\"schemaName\":\"SchemaTest\","
        + "  \"metricFieldSpecs\":[ {\"name\":\"m\",\"dataType\":\"%s\"}],"
        + "  \"dimensionFieldSpecs\":[ {\"name\":\"d\",\"dataType\":\"%s\",\"singleValueField\": %s }],"
        + "   \"timeFieldSpec\":{"
        + "    \"incomingGranularitySpec\":{\"dataType\":\"LONG\",\"timeType\":\"MILLISECONDS\",\"name\":\"time\"},"
        + "    \"defaultNullValue\":12345}, \"dateTimeFieldSpecs\":["
        + "    {\"name\":\"Date\", \"dataType\":\"LONG\", \"format\":\"1:MILLISECONDS:EPOCH\", \"granularity\":\"5:MINUTES\", \"dateTimeType\":\"PRIMARY\"}"
        + "  ]}", metricType, dimensionType, isSingleValue);

  }

  private String makeUpsertSchema(FieldSpec.DataType metricType, FieldSpec.DataType dimensionType, boolean isSingleValue,
    String ingestionMode, String primaryKey, String offsetKey) {
    return String.format("{"
        + "\"schemaName\":\"SchemaTest\","
        + "  \"metricFieldSpecs\":[ {\"name\":\"m\",\"dataType\":\"%s\"}],"
        + "  \"dimensionFieldSpecs\":[ {\"name\":\"primary\",\"dataType\":\"%s\",\"singleValueField\": %s },"
        + " {\"name\":\"offset\",\"dataType\":\"LONG\"}, "
        + " {\"name\":\"other\",\"dataType\":\"STRING\"}],"
        + "   \"timeFieldSpec\":{"
        + "    \"incomingGranularitySpec\":{\"dataType\":\"LONG\",\"timeType\":\"MILLISECONDS\",\"name\":\"time\"},"
        + "    \"defaultNullValue\":12345}, \"dateTimeFieldSpecs\":["
        + "    {\"name\":\"Date\", \"dataType\":\"LONG\", \"format\":\"1:MILLISECONDS:EPOCH\", \"granularity\":\"5:MINUTES\", \"dateTimeType\":\"PRIMARY\"}],"
        + "  \"ingestionModeConfig\": {\"ingestionMode\": \"%s\", \"primaryKey\": \"%s\", \"offsetKey\": \"%s\"}"
        + "}", metricType, dimensionType, isSingleValue, ingestionMode, primaryKey, offsetKey);
  }


  @Test
  public void testSchemaBuilder() {
    String defaultString = "default";
    Schema schema = new Schema.SchemaBuilder().addSingleValueDimension("svDimension", FieldSpec.DataType.INT)
        .addSingleValueDimension("svDimensionWithDefault", FieldSpec.DataType.INT, 10)
        .addMultiValueDimension("mvDimension", FieldSpec.DataType.STRING)
        .addMultiValueDimension("mvDimensionWithDefault", FieldSpec.DataType.STRING, defaultString)
        .addMetric("metric", FieldSpec.DataType.INT).addMetric("metricWithDefault", FieldSpec.DataType.INT, 5)
        .addTime("time", TimeUnit.DAYS, FieldSpec.DataType.LONG)
        .addDateTime("dateTime", FieldSpec.DataType.LONG, "1:HOURS:EPOCH", "1:HOURS").build();

    DimensionFieldSpec dimensionFieldSpec = schema.getDimensionSpec("svDimension");
    Assert.assertNotNull(dimensionFieldSpec);
    Assert.assertEquals(dimensionFieldSpec.getFieldType(), FieldSpec.FieldType.DIMENSION);
    Assert.assertEquals(dimensionFieldSpec.getName(), "svDimension");
    Assert.assertEquals(dimensionFieldSpec.getDataType(), FieldSpec.DataType.INT);
    Assert.assertEquals(dimensionFieldSpec.isSingleValueField(), true);
    Assert.assertEquals(dimensionFieldSpec.getDefaultNullValue(), Integer.MIN_VALUE);

    dimensionFieldSpec = schema.getDimensionSpec("svDimensionWithDefault");
    Assert.assertNotNull(dimensionFieldSpec);
    Assert.assertEquals(dimensionFieldSpec.getFieldType(), FieldSpec.FieldType.DIMENSION);
    Assert.assertEquals(dimensionFieldSpec.getName(), "svDimensionWithDefault");
    Assert.assertEquals(dimensionFieldSpec.getDataType(), FieldSpec.DataType.INT);
    Assert.assertEquals(dimensionFieldSpec.isSingleValueField(), true);
    Assert.assertEquals(dimensionFieldSpec.getDefaultNullValue(), 10);

    dimensionFieldSpec = schema.getDimensionSpec("mvDimension");
    Assert.assertNotNull(dimensionFieldSpec);
    Assert.assertEquals(dimensionFieldSpec.getFieldType(), FieldSpec.FieldType.DIMENSION);
    Assert.assertEquals(dimensionFieldSpec.getName(), "mvDimension");
    Assert.assertEquals(dimensionFieldSpec.getDataType(), FieldSpec.DataType.STRING);
    Assert.assertEquals(dimensionFieldSpec.isSingleValueField(), false);
    Assert.assertEquals(dimensionFieldSpec.getDefaultNullValue(), "null");

    dimensionFieldSpec = schema.getDimensionSpec("mvDimensionWithDefault");
    Assert.assertNotNull(dimensionFieldSpec);
    Assert.assertEquals(dimensionFieldSpec.getFieldType(), FieldSpec.FieldType.DIMENSION);
    Assert.assertEquals(dimensionFieldSpec.getName(), "mvDimensionWithDefault");
    Assert.assertEquals(dimensionFieldSpec.getDataType(), FieldSpec.DataType.STRING);
    Assert.assertEquals(dimensionFieldSpec.isSingleValueField(), false);
    Assert.assertEquals(dimensionFieldSpec.getDefaultNullValue(), defaultString);

    MetricFieldSpec metricFieldSpec = schema.getMetricSpec("metric");
    Assert.assertNotNull(metricFieldSpec);
    Assert.assertEquals(metricFieldSpec.getFieldType(), FieldSpec.FieldType.METRIC);
    Assert.assertEquals(metricFieldSpec.getName(), "metric");
    Assert.assertEquals(metricFieldSpec.getDataType(), FieldSpec.DataType.INT);
    Assert.assertEquals(metricFieldSpec.isSingleValueField(), true);
    Assert.assertEquals(metricFieldSpec.getDefaultNullValue(), 0);

    metricFieldSpec = schema.getMetricSpec("metricWithDefault");
    Assert.assertNotNull(metricFieldSpec);
    Assert.assertEquals(metricFieldSpec.getFieldType(), FieldSpec.FieldType.METRIC);
    Assert.assertEquals(metricFieldSpec.getName(), "metricWithDefault");
    Assert.assertEquals(metricFieldSpec.getDataType(), FieldSpec.DataType.INT);
    Assert.assertEquals(metricFieldSpec.isSingleValueField(), true);
    Assert.assertEquals(metricFieldSpec.getDefaultNullValue(), 5);

    TimeFieldSpec timeFieldSpec = schema.getTimeFieldSpec();
    Assert.assertNotNull(timeFieldSpec);
    Assert.assertEquals(timeFieldSpec.getFieldType(), FieldSpec.FieldType.TIME);
    Assert.assertEquals(timeFieldSpec.getName(), "time");
    Assert.assertEquals(timeFieldSpec.getDataType(), FieldSpec.DataType.LONG);
    Assert.assertEquals(timeFieldSpec.isSingleValueField(), true);
    Assert.assertEquals(timeFieldSpec.getDefaultNullValue(), Long.MIN_VALUE);

    DateTimeFieldSpec dateTimeFieldSpec = schema.getDateTimeSpec("dateTime");
    Assert.assertNotNull(dateTimeFieldSpec);
    Assert.assertEquals(dateTimeFieldSpec.getFieldType(), FieldSpec.FieldType.DATE_TIME);
    Assert.assertEquals(dateTimeFieldSpec.getName(), "dateTime");
    Assert.assertEquals(dateTimeFieldSpec.getDataType(), FieldSpec.DataType.LONG);
    Assert.assertEquals(dateTimeFieldSpec.isSingleValueField(), true);
    Assert.assertEquals(dateTimeFieldSpec.getDefaultNullValue(), Long.MIN_VALUE);
    Assert.assertEquals(dateTimeFieldSpec.getFormat(), "1:HOURS:EPOCH");
    Assert.assertEquals(dateTimeFieldSpec.getGranularity(), "1:HOURS");
  }

  @Test
  public void testSchemaBuilderAddTime() {
    String incomingName = "incoming";
    FieldSpec.DataType incomingDataType = FieldSpec.DataType.LONG;
    TimeUnit incomingTimeUnit = TimeUnit.HOURS;
    int incomingTimeUnitSize = 1;
    TimeGranularitySpec incomingTimeGranularitySpec =
        new TimeGranularitySpec(incomingDataType, incomingTimeUnitSize, incomingTimeUnit, incomingName);
    String outgoingName = "outgoing";
    FieldSpec.DataType outgoingDataType = FieldSpec.DataType.INT;
    TimeUnit outgoingTimeUnit = TimeUnit.DAYS;
    int outgoingTimeUnitSize = 1;
    TimeGranularitySpec outgoingTimeGranularitySpec =
        new TimeGranularitySpec(outgoingDataType, outgoingTimeUnitSize, outgoingTimeUnit, outgoingName);
    int defaultNullValue = 17050;

    Schema schema1 =
        new Schema.SchemaBuilder().setSchemaName("testSchema").addTime(incomingName, incomingTimeUnit, incomingDataType)
            .build();
    Schema schema2 = new Schema.SchemaBuilder().setSchemaName("testSchema")
        .addTime(incomingName, incomingTimeUnit, incomingDataType, defaultNullValue).build();
    Schema schema3 = new Schema.SchemaBuilder().setSchemaName("testSchema")
        .addTime(incomingName, incomingTimeUnit, incomingDataType, outgoingName, outgoingTimeUnit, outgoingDataType)
        .build();
    Schema schema4 = new Schema.SchemaBuilder().setSchemaName("testSchema")
        .addTime(incomingName, incomingTimeUnit, incomingDataType, outgoingName, outgoingTimeUnit, outgoingDataType,
            defaultNullValue).build();
    Schema schema5 = new Schema.SchemaBuilder().setSchemaName("testSchema")
        .addTime(incomingName, incomingTimeUnitSize, incomingTimeUnit, incomingDataType).build();
    Schema schema6 = new Schema.SchemaBuilder().setSchemaName("testSchema")
        .addTime(incomingName, incomingTimeUnitSize, incomingTimeUnit, incomingDataType, defaultNullValue).build();
    Schema schema7 = new Schema.SchemaBuilder().setSchemaName("testSchema")
        .addTime(incomingName, incomingTimeUnitSize, incomingTimeUnit, incomingDataType, outgoingName,
            outgoingTimeUnitSize, outgoingTimeUnit, outgoingDataType).build();
    Schema schema8 = new Schema.SchemaBuilder().setSchemaName("testSchema")
        .addTime(incomingName, incomingTimeUnitSize, incomingTimeUnit, incomingDataType, outgoingName,
            outgoingTimeUnitSize, outgoingTimeUnit, outgoingDataType, defaultNullValue).build();
    Schema schema9 =
        new Schema.SchemaBuilder().setSchemaName("testSchema").addTime(incomingTimeGranularitySpec).build();
    Schema schema10 =
        new Schema.SchemaBuilder().setSchemaName("testSchema").addTime(incomingTimeGranularitySpec, defaultNullValue)
            .build();
    Schema schema11 = new Schema.SchemaBuilder().setSchemaName("testSchema")
        .addTime(incomingTimeGranularitySpec, outgoingTimeGranularitySpec).build();
    Schema schema12 = new Schema.SchemaBuilder().setSchemaName("testSchema")
        .addTime(incomingTimeGranularitySpec, outgoingTimeGranularitySpec, defaultNullValue).build();

    Assert.assertNotNull(schema1.getTimeFieldSpec());
    Assert.assertNotNull(schema2.getTimeFieldSpec());
    Assert.assertNotNull(schema3.getTimeFieldSpec());
    Assert.assertNotNull(schema4.getTimeFieldSpec());
    Assert.assertNotNull(schema5.getTimeFieldSpec());
    Assert.assertNotNull(schema6.getTimeFieldSpec());
    Assert.assertNotNull(schema7.getTimeFieldSpec());
    Assert.assertNotNull(schema8.getTimeFieldSpec());
    Assert.assertNotNull(schema9.getTimeFieldSpec());
    Assert.assertNotNull(schema10.getTimeFieldSpec());
    Assert.assertNotNull(schema11.getTimeFieldSpec());
    Assert.assertNotNull(schema12.getTimeFieldSpec());

    Assert.assertEquals(schema1, schema5);
    Assert.assertEquals(schema1, schema9);
    Assert.assertEquals(schema2, schema6);
    Assert.assertEquals(schema2, schema10);
    Assert.assertEquals(schema3, schema7);
    Assert.assertEquals(schema3, schema11);
    Assert.assertEquals(schema4, schema8);
    Assert.assertEquals(schema4, schema12);

    // Before adding default null value.
    Assert.assertFalse(schema1.equals(schema2));
    Assert.assertFalse(schema3.equals(schema4));
    Assert.assertFalse(schema5.equals(schema6));
    Assert.assertFalse(schema7.equals(schema8));
    Assert.assertFalse(schema9.equals(schema10));
    Assert.assertFalse(schema11.equals(schema12));

    // After adding default null value.
    schema1.getTimeFieldSpec().setDefaultNullValue(defaultNullValue);
    schema3.getTimeFieldSpec().setDefaultNullValue(defaultNullValue);
    schema5.getTimeFieldSpec().setDefaultNullValue(defaultNullValue);
    schema7.getTimeFieldSpec().setDefaultNullValue(defaultNullValue);
    schema9.getTimeFieldSpec().setDefaultNullValue(defaultNullValue);
    schema11.getTimeFieldSpec().setDefaultNullValue(defaultNullValue);
    Assert.assertEquals(schema1, schema2);
    Assert.assertEquals(schema3, schema4);
    Assert.assertEquals(schema5, schema6);
    Assert.assertEquals(schema7, schema8);
    Assert.assertEquals(schema9, schema10);
    Assert.assertEquals(schema11, schema12);
  }

  @Test
  public void testSerializeDeserialize()
      throws Exception {
    URL resourceUrl = getClass().getClassLoader().getResource("schemaTest.schema");
    Assert.assertNotNull(resourceUrl);
    Schema schema = Schema.fromFile(new File(resourceUrl.getFile()));

    Schema schemaToCompare = Schema.fromString(schema.toPrettyJsonString());
    Assert.assertEquals(schemaToCompare, schema);
    Assert.assertEquals(schemaToCompare.hashCode(), schema.hashCode());

    schemaToCompare = Schema.fromString(schema.toSingleLineJsonString());
    Assert.assertEquals(schemaToCompare, schema);
    Assert.assertEquals(schemaToCompare.hashCode(), schema.hashCode());

    schemaToCompare = SchemaUtils.fromZNRecord(SchemaUtils.toZNRecord(schema));
    Assert.assertEquals(schemaToCompare, schema);
    Assert.assertEquals(schemaToCompare.hashCode(), schema.hashCode());

    // When setting new fields, schema string should be updated
    String jsonSchema = schemaToCompare.toSingleLineJsonString();
    schemaToCompare.setSchemaName("newSchema");
    String jsonSchemaToCompare = schemaToCompare.toSingleLineJsonString();
    Assert.assertNotEquals(jsonSchemaToCompare, jsonSchema);
  }

  @Test
  public void testSimpleDateFormat()
      throws Exception {
    TimeGranularitySpec incomingTimeGranularitySpec =
        new TimeGranularitySpec(FieldSpec.DataType.STRING, 1, TimeUnit.DAYS,
            TimeFormat.SIMPLE_DATE_FORMAT + ":yyyyMMdd", "Date");
    TimeGranularitySpec outgoingTimeGranularitySpec =
        new TimeGranularitySpec(FieldSpec.DataType.STRING, 1, TimeUnit.DAYS,
            TimeFormat.SIMPLE_DATE_FORMAT + ":yyyyMMdd", "Date");
    Schema schema = new Schema.SchemaBuilder().setSchemaName("testSchema")
        .addTime(incomingTimeGranularitySpec, outgoingTimeGranularitySpec).build();
    String jsonSchema = schema.toSingleLineJsonString();
    Schema schemaFromJson = Schema.fromString(jsonSchema);
    Assert.assertEquals(schemaFromJson, schema);
    Assert.assertEquals(schemaFromJson.hashCode(), schema.hashCode());
  }

  @Test
  public void testByteType()
      throws Exception {
    Schema expectedSchema = new Schema();
    byte[] expectedEmptyDefault = new byte[0];
    byte[] expectedNonEmptyDefault = BytesUtils.toBytes("abcd1234");

    expectedSchema.setSchemaName("test");
    expectedSchema.addField(new MetricFieldSpec("noDefault", FieldSpec.DataType.BYTES));
    expectedSchema.addField(new MetricFieldSpec("emptyDefault", FieldSpec.DataType.BYTES, expectedEmptyDefault));
    expectedSchema.addField(new MetricFieldSpec("nonEmptyDefault", FieldSpec.DataType.BYTES, expectedNonEmptyDefault));

    // Ensure that schema can be serialized and de-serialized (ie byte[] converted to String and back).
    String jsonSchema = expectedSchema.toSingleLineJsonString();
    Schema actualSchema = Schema.fromString(jsonSchema);

    Assert.assertEquals(actualSchema.getFieldSpecFor("noDefault").getDefaultNullValue(), expectedEmptyDefault);
    Assert.assertEquals(actualSchema.getFieldSpecFor("emptyDefault").getDefaultNullValue(), expectedEmptyDefault);
    Assert.assertEquals(actualSchema.getFieldSpecFor("nonEmptyDefault").getDefaultNullValue(), expectedNonEmptyDefault);

    Assert.assertEquals(actualSchema, expectedSchema);
    Assert.assertEquals(actualSchema.hashCode(), expectedSchema.hashCode());
  }

  @Test
  public void testSchemaBackwardCompatibility() {
    Schema oldSchema = new Schema.SchemaBuilder().addSingleValueDimension("svDimension", FieldSpec.DataType.INT)
        .addSingleValueDimension("svDimensionWithDefault", FieldSpec.DataType.INT, 10)
        .addMultiValueDimension("mvDimension", FieldSpec.DataType.STRING)
        .addMultiValueDimension("mvDimensionWithDefault", FieldSpec.DataType.STRING, "default")
        .addMetric("metric", FieldSpec.DataType.INT).addMetric("metricWithDefault", FieldSpec.DataType.INT, 5)
        .addTime("time", TimeUnit.DAYS, FieldSpec.DataType.LONG)
        .addDateTime("dateTime", FieldSpec.DataType.LONG, "1:HOURS:EPOCH", "1:HOURS").build();

    Assert.assertThrows(NullPointerException.class, () -> oldSchema.isBackwardCompatibleWith(null));

    // remove column
    Schema schema1 = new Schema.SchemaBuilder().addSingleValueDimension("svDimension", FieldSpec.DataType.INT)
        // Remove column svDimensionWithDefault
        .addMultiValueDimension("mvDimension", FieldSpec.DataType.STRING)
        .addMultiValueDimension("mvDimensionWithDefault", FieldSpec.DataType.STRING, "default")
        .addMetric("metric", FieldSpec.DataType.INT).addMetric("metricWithDefault", FieldSpec.DataType.INT, 5)
        .addTime("time", TimeUnit.DAYS, FieldSpec.DataType.LONG)
        .addDateTime("dateTime", FieldSpec.DataType.LONG, "1:HOURS:EPOCH", "1:HOURS").build();
    Assert.assertFalse(schema1.isBackwardCompatibleWith(oldSchema));

    // change column type
    Schema schema2 = new Schema.SchemaBuilder().addSingleValueDimension("svDimension", FieldSpec.DataType.INT)
        .addSingleValueDimension("svDimensionWithDefault", FieldSpec.DataType.LONG, 10)  // INT -> LONG
        .addMultiValueDimension("mvDimension", FieldSpec.DataType.STRING)
        .addMultiValueDimension("mvDimensionWithDefault", FieldSpec.DataType.STRING, "default")
        .addMetric("metric", FieldSpec.DataType.INT).addMetric("metricWithDefault", FieldSpec.DataType.INT, 5)
        .addTime("time", TimeUnit.DAYS, FieldSpec.DataType.LONG)
        .addDateTime("dateTime", FieldSpec.DataType.LONG, "1:HOURS:EPOCH", "1:HOURS").build();
    Assert.assertFalse(schema2.isBackwardCompatibleWith(oldSchema));

    // change time column
    Schema schema3 = new Schema.SchemaBuilder().addSingleValueDimension("svDimension", FieldSpec.DataType.INT)
        .addSingleValueDimension("svDimensionWithDefault", FieldSpec.DataType.INT, 10)
        .addMultiValueDimension("mvDimension", FieldSpec.DataType.STRING)
        .addMultiValueDimension("mvDimensionWithDefault", FieldSpec.DataType.STRING, "default")
        .addMetric("metric", FieldSpec.DataType.INT).addMetric("metricWithDefault", FieldSpec.DataType.INT, 5)
        .addTime("time", TimeUnit.HOURS, FieldSpec.DataType.LONG) // DAYS -> HOURS
        .addDateTime("dateTime", FieldSpec.DataType.LONG, "1:HOURS:EPOCH", "1:HOURS").build();
    Assert.assertFalse(schema3.isBackwardCompatibleWith(oldSchema));

    // change datetime column
    Schema schema4 = new Schema.SchemaBuilder().addSingleValueDimension("svDimension", FieldSpec.DataType.INT)
        .addSingleValueDimension("svDimensionWithDefault", FieldSpec.DataType.INT, 10)
        .addMultiValueDimension("mvDimension", FieldSpec.DataType.STRING)
        .addMultiValueDimension("mvDimensionWithDefault", FieldSpec.DataType.STRING, "default")
        .addMetric("metric", FieldSpec.DataType.INT).addMetric("metricWithDefault", FieldSpec.DataType.INT, 5)
        .addTime("time", TimeUnit.DAYS, FieldSpec.DataType.LONG)
        .addDateTime("dateTime", FieldSpec.DataType.LONG, "2:HOURS:EPOCH", "1:HOURS").build();  // timeUnit 1 -> 2
    Assert.assertFalse(schema4.isBackwardCompatibleWith(oldSchema));

    // change default value
    Schema schema5 = new Schema.SchemaBuilder().addSingleValueDimension("svDimension", FieldSpec.DataType.INT)
        .addSingleValueDimension("svDimensionWithDefault", FieldSpec.DataType.INT, 100) // default value 10 -> 100
        .addMultiValueDimension("mvDimension", FieldSpec.DataType.STRING)
        .addMultiValueDimension("mvDimensionWithDefault", FieldSpec.DataType.STRING, "default")
        .addMetric("metric", FieldSpec.DataType.INT).addMetric("metricWithDefault", FieldSpec.DataType.INT, 5)
        .addTime("time", TimeUnit.DAYS, FieldSpec.DataType.LONG)
        .addDateTime("dateTime", FieldSpec.DataType.LONG, "1:HOURS:EPOCH", "1:HOURS").build();
    Assert.assertFalse(schema5.isBackwardCompatibleWith(oldSchema));

    // add a new column
    Schema schema6 = new Schema.SchemaBuilder().addSingleValueDimension("svDimension", FieldSpec.DataType.INT)
        .addSingleValueDimension("svDimensionWithDefault", FieldSpec.DataType.INT, 10)
        .addSingleValueDimension("svDimensionWithDefault1", FieldSpec.DataType.INT, 10)
        .addMultiValueDimension("mvDimension", FieldSpec.DataType.STRING)
        .addMultiValueDimension("mvDimensionWithDefault", FieldSpec.DataType.STRING, "default")
        .addMetric("metric", FieldSpec.DataType.INT).addMetric("metricWithDefault", FieldSpec.DataType.INT, 5)
        .addTime("time", TimeUnit.DAYS, FieldSpec.DataType.LONG)
        .addDateTime("dateTime", FieldSpec.DataType.LONG, "1:HOURS:EPOCH", "1:HOURS").build();
    Assert.assertTrue(schema6.isBackwardCompatibleWith(oldSchema));
  }

  @Test
  public void testUpsertSchema() throws Exception {
    Schema rebuildSchema;

    // test backward compatibility
    Schema oldSchema = Schema.fromString(
        makeSchema(FieldSpec.DataType.LONG, FieldSpec.DataType.STRING, true));
    Assert.assertTrue(oldSchema.validate(LOGGER));
    Assert.assertEquals(false, oldSchema.isSchemaForUpsert());
    Assert.assertEquals("", oldSchema.getPrimaryKey());
    Assert.assertEquals("", oldSchema.getOffsetKey());

    // make deserialization and serialization works
    rebuildSchema = Schema.fromString(oldSchema.toSingleLineJsonString());
    Assert.assertEquals(oldSchema, rebuildSchema);
    rebuildSchema = Schema.fromString(oldSchema.toPrettyJsonString());
    Assert.assertEquals(oldSchema, rebuildSchema);

    // specify the schema as append mode
    Schema appendSchema = Schema.fromString(
        makeUpsertSchema(FieldSpec.DataType.LONG, FieldSpec.DataType.STRING, true, "append",
            "somekey", "otherkey"));
    Assert.assertTrue(appendSchema.validate(LOGGER));
    Assert.assertEquals(false, appendSchema.isSchemaForUpsert());
    Assert.assertEquals("somekey", appendSchema.getPrimaryKey());
    Assert.assertEquals("otherkey", appendSchema.getOffsetKey());

    // make deserialization and serialization works
    rebuildSchema = Schema.fromString(oldSchema.toSingleLineJsonString());
    Assert.assertEquals(oldSchema, rebuildSchema);
    rebuildSchema = Schema.fromString(oldSchema.toPrettyJsonString());
    Assert.assertEquals(oldSchema, rebuildSchema);

    // test if the ingestion mode has random string, should be accept as append table
    appendSchema = Schema.fromString(
        makeUpsertSchema(FieldSpec.DataType.LONG, FieldSpec.DataType.STRING, true, "random",
            "somekey", "otherkey"));
    Assert.assertTrue(appendSchema.validate(LOGGER));
    Assert.assertEquals(false, appendSchema.isSchemaForUpsert());

    // specify schema as upsert
    Schema upsertSchema = Schema.fromString(
        makeUpsertSchema(FieldSpec.DataType.LONG, FieldSpec.DataType.STRING, true, "upsert",
            "somekey", "otherkey"));
    Assert.assertTrue(upsertSchema.validate(LOGGER));
    Assert.assertEquals(true, upsertSchema.isSchemaForUpsert());
    Assert.assertEquals("somekey", upsertSchema.getPrimaryKey());
    Assert.assertEquals("otherkey", upsertSchema.getOffsetKey());

    // make deserialization and serialization works
    rebuildSchema = Schema.fromString(oldSchema.toSingleLineJsonString());
    Assert.assertEquals(oldSchema, rebuildSchema);
    rebuildSchema = Schema.fromString(oldSchema.toPrettyJsonString());
    Assert.assertEquals(oldSchema, rebuildSchema);

    // testing if equals & hashcode work
    Assert.assertNotEquals(oldSchema, upsertSchema);
    Assert.assertNotEquals(oldSchema, appendSchema);
    Assert.assertNotEquals(appendSchema, upsertSchema);

    Assert.assertNotEquals(oldSchema.hashCode(), upsertSchema.hashCode());
    Assert.assertNotEquals(oldSchema.hashCode(), appendSchema.hashCode());
    Assert.assertNotEquals(appendSchema.hashCode(), upsertSchema.hashCode());
  }

  @Test
  public void testUpsertSchemaFields() throws IOException {
    Schema upsertSchema = Schema.fromString(
        makeUpsertSchema(FieldSpec.DataType.LONG, FieldSpec.DataType.STRING, true, "upsert",
            "primary", "offset"));

    DimensionFieldSpec primaryKeyFieldSpec = upsertSchema.getPrimaryKeyFieldSpec();
    Assert.assertNotNull(primaryKeyFieldSpec);
    Assert.assertEquals("primary", primaryKeyFieldSpec.getName());
    Assert.assertEquals(FieldSpec.DataType.STRING, primaryKeyFieldSpec.getDataType());
    Assert.assertEquals(true, primaryKeyFieldSpec.isSingleValueField());

    DimensionFieldSpec offsetField = upsertSchema.getPrimaryKeyFieldSpec();
    Assert.assertNotNull(offsetField);
    Assert.assertEquals("offset", offsetField.getName());
    Assert.assertEquals(FieldSpec.DataType.LONG, primaryKeyFieldSpec.getDataType());
    Assert.assertEquals(true, primaryKeyFieldSpec.isSingleValueField());
  }

  @Test
  public void testSchemaHint() throws IOException {
    Schema upsertSchema = Schema.fromString(
        makeUpsertSchema(FieldSpec.DataType.LONG, FieldSpec.DataType.STRING, true, "upsert",
            "primary", "offset"));

    Schema oldSchema = Schema.fromString(
        makeSchema(FieldSpec.DataType.LONG, FieldSpec.DataType.STRING, true));

    Assert.assertEquals(false, oldSchema.isSchemaForUpsert());
    Assert.assertEquals("", oldSchema.getPrimaryKey());
    Assert.assertEquals("", oldSchema.getOffsetKey());

    oldSchema.withSchemaHint(upsertSchema);

    Assert.assertEquals(true, oldSchema.isSchemaForUpsert());
    Assert.assertEquals("primary", oldSchema.getPrimaryKey());
    Assert.assertEquals("offset", oldSchema.getOffsetKey());
  }

  @Test
  public void test
}
