/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.iotdb.db.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.apache.iotdb.db.conf.IoTDBDescriptor;
import org.apache.iotdb.db.service.IoTDB;
import org.apache.iotdb.db.utils.EnvironmentUtils;
import org.apache.iotdb.jdbc.Config;
import org.apache.iotdb.tsfile.common.conf.TSFileConfig;
import org.apache.iotdb.tsfile.common.conf.TSFileDescriptor;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Notice that, all test begins with "IoTDB" is integration test. All test which will start the IoTDB server should be
 * defined as integration test.
 */
public class IoTDBMultiSeriesIT {

  private static IoTDB daemon;

  private static boolean testFlag = Constant.testFlag;
  private static TSFileConfig tsFileConfig = TSFileDescriptor.getInstance().getConfig();
  private static int maxNumberOfPointsInPage;
  private static int pageSizeInByte;
  private static int groupSizeInByte;

  @BeforeClass
  public static void setUp() throws Exception {

    EnvironmentUtils.closeStatMonitor();

    // use small page setting
    // origin value
    maxNumberOfPointsInPage = tsFileConfig.maxNumberOfPointsInPage;
    pageSizeInByte = tsFileConfig.pageSizeInByte;
    groupSizeInByte = tsFileConfig.groupSizeInByte;

    // new value
    tsFileConfig.maxNumberOfPointsInPage = 1000;
    tsFileConfig.pageSizeInByte = 1024 * 150;
    tsFileConfig.groupSizeInByte = 1024 * 1000;
    IoTDBDescriptor.getInstance().getConfig().setMemtableSizeThreshold(1024 * 1000);

    daemon = IoTDB.getInstance();
    daemon.active();
    EnvironmentUtils.envSetUp();

    insertData();
  }

  @AfterClass
  public static void tearDown() throws Exception {
    daemon.stop();
    // recovery value
    tsFileConfig.maxNumberOfPointsInPage = maxNumberOfPointsInPage;
    tsFileConfig.pageSizeInByte = pageSizeInByte;
    tsFileConfig.groupSizeInByte = groupSizeInByte;
    IoTDBDescriptor.getInstance().getConfig().setMemtableSizeThreshold(groupSizeInByte);

    EnvironmentUtils.cleanEnv();
  }

  private static void insertData()
      throws ClassNotFoundException, SQLException {
    Class.forName(Config.JDBC_DRIVER_NAME);
    try (Connection connection = DriverManager
        .getConnection(Config.IOTDB_URL_PREFIX + "127.0.0.1:6667/", "root", "root");
        Statement statement = connection.createStatement()) {

      for (String sql : Constant.create_sql) {
        statement.execute(sql);
      }

      // insert large amount of data time range : 13700 ~ 24000
      for (int time = 13700; time < 24000; time++) {

        String sql = String
            .format("insert into root.vehicle.d0(timestamp,s0) values(%s,%s)", time, time % 70);
        statement.execute(sql);
        sql = String
            .format("insert into root.vehicle.d0(timestamp,s1) values(%s,%s)", time, time % 40);
        statement.execute(sql);
        sql = String
            .format("insert into root.vehicle.d0(timestamp,s2) values(%s,%s)", time, time % 123);
        statement.execute(sql);
      }

      // insert large amount of data time range : 3000 ~ 13600
      for (int time = 3000; time < 13600; time++) {
        // System.out.println("===" + time);
        String sql = String
            .format("insert into root.vehicle.d0(timestamp,s0) values(%s,%s)", time, time % 100);
        statement.execute(sql);
        sql = String
            .format("insert into root.vehicle.d0(timestamp,s1) values(%s,%s)", time, time % 17);
        statement.execute(sql);
        sql = String
            .format("insert into root.vehicle.d0(timestamp,s2) values(%s,%s)", time, time % 22);
        statement.execute(sql);
        sql = String.format("insert into root.vehicle.d0(timestamp,s3) values(%s,'%s')", time,
            Constant.stringValue[time % 5]);
        statement.execute(sql);
        sql = String.format("insert into root.vehicle.d0(timestamp,s4) values(%s, %s)", time,
            Constant.booleanValue[time % 2]);
        statement.execute(sql);
        sql = String.format("insert into root.vehicle.d0(timestamp,s5) values(%s, %s)", time, time);
        statement.execute(sql);
      }

      statement.execute("flush");
      statement.execute("merge");

      // buffwrite data, unsealed file
      for (int time = 100000; time < 101000; time++) {

        String sql = String
            .format("insert into root.vehicle.d0(timestamp,s0) values(%s,%s)", time, time % 20);
        statement.execute(sql);
        sql = String
            .format("insert into root.vehicle.d0(timestamp,s1) values(%s,%s)", time, time % 30);
        statement.execute(sql);
        sql = String
            .format("insert into root.vehicle.d0(timestamp,s2) values(%s,%s)", time, time % 77);
        statement.execute(sql);
      }

      statement.execute("flush");

      // sequential data, memory data
      for (int time = 200000; time < 201000; time++) {

        String sql = String
            .format("insert into root.vehicle.d0(timestamp,s0) values(%s,%s)", time, -time % 20);
        statement.execute(sql);
        sql = String
            .format("insert into root.vehicle.d0(timestamp,s1) values(%s,%s)", time, -time % 30);
        statement.execute(sql);
        sql = String
            .format("insert into root.vehicle.d0(timestamp,s2) values(%s,%s)", time, -time % 77);
        statement.execute(sql);
      }

      // unseq insert, time < 3000
      for (int time = 2000; time < 2500; time++) {

        String sql = String
            .format("insert into root.vehicle.d0(timestamp,s0) values(%s,%s)", time, time);
        statement.execute(sql);
        sql = String
            .format("insert into root.vehicle.d0(timestamp,s1) values(%s,%s)", time, time + 1);
        statement.execute(sql);
        sql = String
            .format("insert into root.vehicle.d0(timestamp,s2) values(%s,%s)", time, time + 2);
        statement.execute(sql);
        sql = String.format("insert into root.vehicle.d0(timestamp,s3) values(%s,'%s')", time,
            Constant.stringValue[time % 5]);
        statement.execute(sql);
      }

      // seq insert, time > 200000
      for (int time = 200900; time < 201000; time++) {

        String sql = String
            .format("insert into root.vehicle.d0(timestamp,s0) values(%s,%s)", time, 6666);
        statement.execute(sql);
        sql = String.format("insert into root.vehicle.d0(timestamp,s1) values(%s,%s)", time, 7777);
        statement.execute(sql);
        sql = String.format("insert into root.vehicle.d0(timestamp,s2) values(%s,%s)", time, 8888);
        statement.execute(sql);
        sql = String
            .format("insert into root.vehicle.d0(timestamp,s3) values(%s,'%s')", time, "goodman");
        statement.execute(sql);
        sql = String.format("insert into root.vehicle.d0(timestamp,s4) values(%s, %s)", time,
            Constant.booleanValue[time % 2]);
        statement.execute(sql);
        sql = String.format("insert into root.vehicle.d0(timestamp,s5) values(%s, %s)", time, 9999);
        statement.execute(sql);
      }

    } catch (Exception e) {
      e.printStackTrace();
      fail(e.getMessage());
    }
  }

  // "select * from root.vehicle" : test select wild data
  @Test
  public void selectAllTest() throws ClassNotFoundException {
    String selectSql = "select * from root.vehicle";

    Class.forName(Config.JDBC_DRIVER_NAME);
    try (Connection connection = DriverManager
        .getConnection(Config.IOTDB_URL_PREFIX + "127.0.0.1:6667/", "root", "root");
        Statement statement = connection.createStatement()) {
      boolean hasResultSet = statement.execute(selectSql);
      Assert.assertTrue(hasResultSet);
      try (ResultSet resultSet = statement.getResultSet()) {
        int cnt = 0;
        while (resultSet.next()) {
          String ans =
              resultSet.getString(Constant.TIMESTAMP_STR) + "," + resultSet.getString(Constant.d0s0)
                  + "," + resultSet.getString(Constant.d0s1) + "," + resultSet
                  .getString(Constant.d0s2) + ","
                  + resultSet.getString(Constant.d0s3) + "," + resultSet.getString(Constant.d0s4)
                  + ","
                  + resultSet.getString(Constant.d0s5);
          cnt++;
        }
        assertEquals(23400, cnt);
      }
    } catch (Exception e) {
      e.printStackTrace();
      fail(e.getMessage());
    }
  }

  // "select s0 from root.vehicle.d0 where s0 >= 20" : test select same series with same series filter
  @Test
  public void selectOneSeriesWithValueFilterTest() throws ClassNotFoundException, SQLException {

    String selectSql = "select s0 from root.vehicle.d0 where s0 >= 20";

    Class.forName(Config.JDBC_DRIVER_NAME);
    try (Connection connection = DriverManager
        .getConnection(Config.IOTDB_URL_PREFIX + "127.0.0.1:6667/", "root", "root");
        Statement statement = connection.createStatement()) {

      boolean hasResultSet = statement.execute(selectSql);
      Assert.assertTrue(hasResultSet);
      try (ResultSet resultSet = statement.getResultSet()) {
        int cnt = 0;
        while (resultSet.next()) {
          String ans =
              resultSet.getString(Constant.TIMESTAMP_STR) + "," + resultSet.getString(Constant.d0s0);
          // System.out.println("===" + ans);
          cnt++;
        }
        assertEquals(16440, cnt);
      }

    } catch (Exception e) {
      e.printStackTrace();
      fail(e.getMessage());
    }
  }

  // "select s0 from root.vehicle.d0 where time > 22987 " : test select clause with only global time filter
  @Test
  public void seriesGlobalTimeFilterTest() throws ClassNotFoundException, SQLException {

    Class.forName(Config.JDBC_DRIVER_NAME);

    boolean hasResultSet;

    try (Connection connection = DriverManager
        .getConnection(Config.IOTDB_URL_PREFIX + "127.0.0.1:6667/", "root", "root");
        Statement statement = connection.createStatement();) {
      hasResultSet = statement.execute("select s0 from root.vehicle.d0 where time > 22987");
      assertTrue(hasResultSet);
      try (ResultSet resultSet = statement.getResultSet()) {
        int cnt = 0;
        while (resultSet.next()) {
          String ans =
              resultSet.getString(Constant.TIMESTAMP_STR) + "," + resultSet.getString(Constant.d0s0);
          // System.out.println(ans);
          cnt++;
        }

        assertEquals(3012, cnt);
      }

    } catch (Exception e) {
      e.printStackTrace();
      fail(e.getMessage());
    }
  }

  // "select s1 from root.vehicle.d0 where s0 < 111" : test select clause with different series filter
  @Test
  public void crossSeriesReadUpdateTest() throws ClassNotFoundException, SQLException {
    Class.forName(Config.JDBC_DRIVER_NAME);

    boolean hasResultSet;

    try (Connection connection = DriverManager
        .getConnection(Config.IOTDB_URL_PREFIX + "127.0.0.1:6667/", "root", "root");
        Statement statement = connection.createStatement()) {
      hasResultSet = statement.execute("select s1 from root.vehicle.d0 where s0 < 111");
      assertTrue(hasResultSet);
      try (ResultSet resultSet = statement.getResultSet()) {
        int cnt = 0;
        while (resultSet.next()) {
          long time = Long.valueOf(resultSet.getString(Constant.TIMESTAMP_STR));
          String value = resultSet.getString(Constant.d0s1);
          if (time > 200900) {
            assertEquals("7777", value);
          }
          // String ans = resultSet.getString(d0s1);
          cnt++;
        }
        assertEquals(22800, cnt);
      }

    } catch (Exception e) {
      e.printStackTrace();
      fail(e.getMessage());
    }
  }
}
