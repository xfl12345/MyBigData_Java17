package cc.xfl12345.mybigdata.server;

import cc.xfl12345.mybigdata.server.listener.ContextFinalizer;
import cc.xfl12345.mybigdata.server.model.database.association.StringContentGlobalRecordAssociation;
import cc.xfl12345.mybigdata.server.model.database.table.GlobalDataRecord;
import cc.xfl12345.mybigdata.server.model.database.table.StringContent;
import com.alibaba.druid.DbType;
import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.dialect.mysql.ast.clause.MySqlSelectIntoStatement;
import com.alibaba.fastjson2.JSON;
import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedGenerator;
import org.teasoft.bee.osql.*;
import org.teasoft.honey.osql.core.BeeFactory;
import org.teasoft.honey.osql.core.ConditionImpl;
import org.teasoft.honey.osql.core.HoneyFactory;
import org.teasoft.honey.osql.core.ObjectToSQLRich;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class StudyBeeOrm {
    public static void main(String[] args) throws IOException, SQLException {
        DruidDataSource dataSource = TestLoadDataSource.getDataSource();

        BeeFactory beeFactory = BeeFactory.getInstance();
        beeFactory.setDataSource(dataSource);

        StudyBeeOrm studyBeeOrm = new StudyBeeOrm();
        studyBeeOrm.justTest(BeeFactory.getHoneyFactory());
        ContextFinalizer.deregisterJdbcDriver(null);
    }

    public void justTest(HoneyFactory honeyFactory) {
        TimeBasedGenerator uuidGenerator = Generators.timeBasedGenerator();

        SuidRich suid = honeyFactory.getSuidRich();

        ArrayList<GlobalDataRecord> records = new ArrayList<>();
        GlobalDataRecord globalDataRecord = new GlobalDataRecord();
        globalDataRecord.setUuid(uuidGenerator.generate().toString());
        records.add(globalDataRecord);
        globalDataRecord = new GlobalDataRecord();
        globalDataRecord.setUuid(uuidGenerator.generate().toString());
        records.add(globalDataRecord);
        System.out.println(suid.insert(records));

        Condition condition = new ConditionImpl();
//        Condition condition = BeeFactoryHelper.getCondition();
        condition.op("table_name", Op.eq, null);
        List<GlobalDataRecord> recordList = suid.select(new GlobalDataRecord(), condition);

        System.out.println(recordList);

        // ?????????????????????
        StringContent stringContent = new StringContent();
        stringContent.setContent("0123456789".repeat(100));
        stringContent.setGlobalId(recordList.get(0).getId());
        executeInsert(honeyFactory, stringContent);

        // unique key ????????????
        stringContent = new StringContent();
        stringContent.setContent("text");
        stringContent.setGlobalId(recordList.get(0).getId());
        executeInsert(honeyFactory, stringContent);

        // ??????????????????
        globalDataRecord = new GlobalDataRecord();
        globalDataRecord.setUuid(uuidGenerator.generate().toString());
        globalDataRecord.setId(recordList.get(0).getId());
        executeInsert(honeyFactory, globalDataRecord);


        // ??????????????????
        StringContentGlobalRecordAssociation associationQuery = new StringContentGlobalRecordAssociation();
        associationQuery.setContent("text");
        MoreTable moreTable = honeyFactory.getMoreTable();
        System.out.println(JSON.toJSONString(moreTable.select(associationQuery)));


        // ??????????????????????????????
        stringContent = new StringContent();
        stringContent.setContent("text");
        try {
            suid.delete(stringContent);
        } catch (Exception e) {
            System.out.println(e.getMessage());
            Throwable cause = e.getCause();
            if (cause instanceof SQLException sqlException) {
                handleSqlException(sqlException, stringContent);
            }
        }

        // honeyFactory.getPreparedSql()
        // honeyFactory.getBeeSql().select()
        // List<Long> globalIds = honeyFactory.getCallableSql().select(
        //     "insert_string_content(?, ?, ?, ?)",
        //     Long.MAX_VALUE,
        //     new Object[]{
        //         uuidGenerator.generate(),
        //         "????????????",
        //         uuidGenerator.generate(),
        //         "xfl666"
        //     }
        // );
        // System.out.println("globalIds:" + globalIds);
    }

    public int executeInsert(HoneyFactory honeyFactory, Object obj) {
        // ??????????????????
        int rowCount = 0;
        try {
            rowCount = honeyFactory.getSuidRich().insert(obj);
        } catch (BeeException e) {
            System.out.println(e.getMessage());
            Throwable cause = e.getCause();
            if (cause instanceof SQLException sqlException) {
                handleSqlException(sqlException, obj);
            }
        }

        System.out.println(rowCount);
        return rowCount;
    }

    public void handleSqlException(SQLException sqlException, Object toy) {
        int errorCode = sqlException.getErrorCode();
        BeeFactory beeFactory = BeeFactory.getInstance();
        String dbTypeInString = ((DruidDataSource) beeFactory.getDataSource()).getDbType();
        DbType dbType = DbType.valueOf(dbTypeInString);
        switch (dbType) {
            // ????????? MySQL ??????
            case mysql -> {
                switch (errorCode) {
                    case 1059 -> { // ER_TOO_LONG_IDENT -- Identifier name '%s' is too long
                        System.out.println("????????????");
                    }
                    case 1060 -> { // ER_DUP_FIELDNAME -- Duplicate column name '%s'
                        System.out.println("?????????????????????unique?????????");
                    }
                    case 1061 -> { // ER_DUP_KEYNAME -- Duplicate key name '%s'
                        System.out.println("????????????");
                    }
                    case 1062 -> { // ER_DUP_ENTRY -- Duplicate entry '%s' for key %d
                        System.out.println("?????????????????????unique?????????");
                        if (!(toy instanceof StringContent)) {
                            break;
                        }
                        ObjectToSQLRich objectToSQLRich = new ObjectToSQLRich();
                        String sqlStringSelectGDR = objectToSQLRich.toSelectSQL(new GlobalDataRecord());
                        SQLStatement sqlStatement = SQLUtils.parseSingleStatement(sqlStringSelectGDR, DbType.mysql);
                        SQLStatement sqlStatement2 = new MySqlSelectIntoStatement();


                        // MoreTable moreTable = honeyFactory.getMoreTable();
                        // GlobalDataRecord crossQueryEntity = new GlobalDataRecord() {
                        //     @Getter
                        //     @Setter
                        //     @JoinTable(mainField="id", subField="global_id", joinType= JoinType.JOIN)
                        //     private StringContent stringContent = (StringContent) obj;
                        // };
                        // List<GlobalDataRecord> globalDataRecords = moreTable.select(crossQueryEntity);
                    }
                    case 1406 -> { // ER_DATA_TOO_LONG -- Data too long for column '%s' at row %ld
                        System.out.println("????????????????????????");
                    }

                    case 1451 -> { // ER_ROW_IS_REFERENCED_2 -- Cannot delete or update a parent row: a foreign key constraint fails (%s)
                        System.out.println("???????????????????????????????????????????????????????????????");
                    }
                    default -> {
                        System.out.println("???????????? code=" + errorCode);
                    }
                }
            }
            default -> System.out.println("Not supported database.");
        }
    }

}
