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

package io.sophiadata.flink.catalog;

import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.connector.jdbc.catalog.MySqlCatalog;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.Schema;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;
import org.apache.flink.table.catalog.DefaultCatalogTable;
import org.apache.flink.table.catalog.ObjectPath;
import org.apache.flink.table.runtime.typeutils.InternalTypeInfo;
import org.apache.flink.table.types.DataType;
import org.apache.flink.table.types.logical.LogicalType;
import org.apache.flink.table.types.logical.RowType;

import org.apache.flink.shaded.guava30.com.google.common.collect.Maps;

import io.sophiadata.flink.base.BaseSql;

import java.util.List;
import java.util.Map;

/** (@SophiaData) (@date 2022/12/6 15:27). */
public class MySQLCataLogTest extends BaseSql {

    public static void main(String[] args) throws Exception {

        new MySQLCataLogTest().init(args, "MySQLCataLogTest");
    }

    @Override
    public void handle(String[] args, StreamExecutionEnvironment env, StreamTableEnvironment tEnv) {
        final ParameterTool params = ParameterTool.fromArgs(args);
        env.getConfig().setGlobalJobParameters(params);
        // jdbc catalog test code
        String hostname = params.get("hostname", "localhost");
        int port = params.getInt("port", 3306);
        String username = params.get("username", "root");
        String password = params.get("password", "123456");
        String databaseName = params.get("databaseName", "test");

        MySqlCatalog mysqlCatalog =
                new MySqlCatalog(
                        Thread.currentThread().getContextClassLoader(), // MySQL 8 驱动
                        // Class.forName("com.mysql.cj.jdbc.Driver").getClassLoader()
                        "mysql_catalog",
                        databaseName,
                        username,
                        password,
                        String.format("jdbc:mysql://%s:%d", hostname, port));

        try {
            List<String> tables = mysqlCatalog.listTables(databaseName);
            System.out.println(tables);

            // 创建表名和对应 RowTypeInfo 映射的 Map
            Map<String, RowType> tableRowTypeMap = Maps.newConcurrentMap();
            for (String table : tables) {
                // 获取 MySQL Catalog 中注册的表
                ObjectPath objectPath = new ObjectPath(databaseName, table);
                DefaultCatalogTable catalogBaseTable;
                catalogBaseTable = (DefaultCatalogTable) mysqlCatalog.getTable(objectPath);
                // 获取表的 Schema
                assert catalogBaseTable != null;
                Schema schema = catalogBaseTable.getUnresolvedSchema();
                // 获取表中字段名列表
                String[] fieldNames = new String[schema.getColumns().size()];
                // 获取DataType
                DataType[] fieldDataTypes = new DataType[schema.getColumns().size()];
                LogicalType[] logicalTypes = new LogicalType[schema.getColumns().size()];

                // 获取表字段类型
                TypeInformation<?>[] fieldTypes = new TypeInformation[schema.getColumns().size()];
                // 获取表的主键
                List<String> primaryKeys = null;
                primaryKeys = schema.getPrimaryKey().get().getColumnNames();

                for (int i = 0; i < schema.getColumns().size(); i++) {
                    Schema.UnresolvedPhysicalColumn column =
                            (Schema.UnresolvedPhysicalColumn) schema.getColumns().get(i);
                    fieldNames[i] = column.getName();
                    fieldDataTypes[i] = (DataType) column.getDataType();
                    fieldTypes[i] =
                            InternalTypeInfo.of(((DataType) column.getDataType()).getLogicalType());
                    logicalTypes[i] = ((DataType) column.getDataType()).getLogicalType();
                }
                RowType rowType = RowType.of(logicalTypes, fieldNames);
                tableRowTypeMap.put(table, rowType);
                System.out.println(tableRowTypeMap); // 输出捕获表的元数据信息
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        tEnv.registerCatalog("mysql_catalog", mysqlCatalog);

        tEnv.useCatalog("mysql_catalog");

        tEnv.executeSql("SELECT * FROM mysql_catalog.test.test3").print();
    }
}
