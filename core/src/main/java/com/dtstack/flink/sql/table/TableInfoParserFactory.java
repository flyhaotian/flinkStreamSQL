/*
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
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

 

package com.dtstack.flink.sql.table;

import com.dtstack.flink.sql.enums.ETableType;
import com.dtstack.flink.sql.parser.CreateTableParser;
import com.dtstack.flink.sql.side.StreamSideFactory;
import com.dtstack.flink.sql.sink.StreamSinkFactory;
import com.dtstack.flink.sql.source.StreamSourceFactory;
import com.dtstack.flink.sql.util.MathUtil;
import org.apache.flink.calcite.shaded.com.google.common.base.Strings;
import org.apache.flink.shaded.curator.org.apache.curator.shaded.com.google.common.collect.Maps;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 解析创建表语句获得具体的表结构信息
 * Date: 2018/6/25
 * Company: www.dtstack.com
 * @author xuchao
 */

public class TableInfoParserFactory {

    private final static String TYPE_KEY = "type";

    private final static String SIDE_TABLE_SIGN = "(?i)^PERIOD\\s+FOR\\s+SYSTEM_TIME$";

    private final static Pattern SIDE_PATTERN = Pattern.compile(SIDE_TABLE_SIGN);

    private static Map<String, AbsTableParser> sourceTableInfoMap = Maps.newConcurrentMap();

    private static Map<String, AbsTableParser> targetTableInfoMap = Maps.newConcurrentMap();

    private static Map<String, AbsTableParser> sideTableInfoMap = Maps.newConcurrentMap();

    //加载插件中的解析
    public static TableInfo parseWithTableType(int tableType, CreateTableParser.SqlParserResult parserResult,
                                               String localPluginRoot) throws Exception {
        AbsTableParser absTableParser = null;
        Map<String, Object> props = parserResult.getPropMap();
        String type = MathUtil.getString(props.get(TYPE_KEY));

        if(Strings.isNullOrEmpty(type)){
            throw new RuntimeException("create table statement requires property of type");
        }

        if(tableType == ETableType.SOURCE.getType()){
            boolean isSideTable = checkIsSideTable(parserResult.getFieldsInfoStr());

            if(!isSideTable){
                absTableParser = sourceTableInfoMap.get(type);
                if(absTableParser == null){
                    absTableParser = StreamSourceFactory.getSqlParser(type, localPluginRoot);
                    sourceTableInfoMap.put(type, absTableParser);
                }
            }else{
                absTableParser = sideTableInfoMap.get(type);
                if(absTableParser == null){
                    absTableParser = StreamSideFactory.getSqlParser(type, localPluginRoot);
                    sideTableInfoMap.put(type, absTableParser);
                }
            }

        }else if(tableType == ETableType.SINK.getType()){
            absTableParser = targetTableInfoMap.get(type);
            if(absTableParser == null){
                absTableParser = StreamSinkFactory.getSqlParser(type, localPluginRoot);
                targetTableInfoMap.put(type, absTableParser);
            }
        }

        if(absTableParser == null){
            throw new RuntimeException(String.format("not support %s type of table", type));
        }

        Map<String, Object> prop = Maps.newHashMap();

        //屏蔽大小写
        parserResult.getPropMap().forEach((key,val) -> prop.put(key.toLowerCase(), val));

        return absTableParser.getTableInfo(parserResult.getTableName(), parserResult.getFieldsInfoStr(), prop);
    }

    /**
     * 如果表字段中包含PERIOD FOR SYSTEM_TIME则认为是一个维表
     * @param tableField
     * @return
     */
    private static boolean checkIsSideTable(String tableField){
        String[] fieldInfos = tableField.split(",");
        for(String field : fieldInfos){
            Matcher matcher = SIDE_PATTERN.matcher(field.trim());
            if(matcher.find()){
                return true;
            }
        }

        return false;
    }
}
