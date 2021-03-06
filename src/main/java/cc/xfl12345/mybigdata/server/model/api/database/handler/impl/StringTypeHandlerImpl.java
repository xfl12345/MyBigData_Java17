package cc.xfl12345.mybigdata.server.model.api.database.handler.impl;

import cc.xfl12345.mybigdata.server.appconst.CoreTableNames;
import cc.xfl12345.mybigdata.server.appconst.SimpleCoreTableCurdResult;
import cc.xfl12345.mybigdata.server.model.api.database.handler.StringTypeHandler;
import cc.xfl12345.mybigdata.server.model.database.association.StringContentGlobalRecordAssociation;
import cc.xfl12345.mybigdata.server.model.database.constant.StringContentConstant;
import cc.xfl12345.mybigdata.server.model.api.database.result.MultipleResultBase;
import cc.xfl12345.mybigdata.server.model.api.database.result.PackedData;
import cc.xfl12345.mybigdata.server.model.api.database.result.StringTypeResult;
import cc.xfl12345.mybigdata.server.model.database.table.GlobalDataRecord;
import cc.xfl12345.mybigdata.server.model.database.table.StringContent;
import cc.xfl12345.mybigdata.server.utility.StringEscapeUtils;
import lombok.extern.slf4j.Slf4j;
import org.teasoft.bee.osql.*;
import org.teasoft.bee.osql.transaction.Transaction;
import org.teasoft.bee.osql.transaction.TransactionIsolationLevel;
import org.teasoft.honey.osql.core.BeeFactory;
import org.teasoft.honey.osql.core.ConditionImpl;
import org.teasoft.honey.osql.core.HoneyFactory;
import org.teasoft.honey.osql.core.SessionFactory;

import java.util.Date;
import java.util.List;

@Slf4j
public class StringTypeHandlerImpl extends AbstractCoreTableHandler implements StringTypeHandler {
    @Override
    public StringTypeResult insertString(String value) {
        Date date = new Date();
        StringTypeResult result = new StringTypeResult();

        // 预备一个 StringContent对象 空间
        StringContent content = new StringContent();
        content.setContent(value);
        content.setContentLength((short) value.length());

        // 开启事务
        Transaction transaction = SessionFactory.getTransaction();
        try {
            transaction.begin();
            transaction.setTransactionIsolation(TransactionIsolationLevel.TRANSACTION_REPEATABLE_READ);
            HoneyFactory honeyFactory = BeeFactory.getHoneyFactory();
            SuidRich suid = honeyFactory.getSuidRich();

            GlobalDataRecord globalDataRecord = new GlobalDataRecord();
            globalDataRecord.setUuid(getUuidInString());
            globalDataRecord.setCreateTime(date);
            globalDataRecord.setUpdateTime(date);
            globalDataRecord.setModifiedCount(1L);
            globalDataRecord.setTableName(
                coreTableCache.getTableNameCache().getValue(CoreTableNames.STRING_CONTENT.getName())
            );
            Long globalId = suid.insertAndReturnId(globalDataRecord);
            globalDataRecord.setId(globalId);

            content.setGlobalId(globalId);

            // 插入数据
            int affectedRowCount = 0;
            affectedRowCount = suid.insert(content);
            if (affectedRowCount == 1) {
                transaction.commit();
                result.setGlobalDataRecord(globalDataRecord);
                result.setStringContent(content);
                result.setSimpleResult(SimpleCoreTableCurdResult.SUCCEED);
            } else {
                transaction.rollback();
                result.setSimpleResult(SimpleCoreTableCurdResult.FAILED_OPERATION_REJECTED);
                result.setMessage("未知错误，插入字符串表失败。");
            }
        } catch (BeeException | NullPointerException e) {
            sqlErrorHandler.defaultErrorHandler(e, transaction, result);
            if (result.getSimpleResult().equals(SimpleCoreTableCurdResult.DUPLICATE)) {
                // 既然重复了，那就把它查出来。
                StringTypeResult query = selectStringByFullText(value, null);
                if (query.getSimpleResult().equals(SimpleCoreTableCurdResult.SUCCEED)) {
                    result.setGlobalDataRecord(query.getGlobalDataRecord());
                    result.setStringContent(query.getStringContent());
                }
            }
        }

        return result;
    }

    protected StringTypeResult updateStringByCondition(String value, Condition condition) {
        Date nowTime = new Date();
        StringTypeResult result = new StringTypeResult();

        // 开启事务
        Transaction transaction = SessionFactory.getTransaction();
        try {
            transaction.begin();
            transaction.setTransactionIsolation(TransactionIsolationLevel.TRANSACTION_REPEATABLE_READ);
            HoneyFactory honeyFactory = BeeFactory.getHoneyFactory();
            SuidRich suid = honeyFactory.getSuidRich();
            MoreTable moreTable = honeyFactory.getMoreTable();

            // 查询数据
            StringContentGlobalRecordAssociation association = moreTable.select(new StringContentGlobalRecordAssociation(), condition).get(0);

            // 创建更新缓存
            GlobalDataRecord gdrDataInDb = association.getGlobalDataRecords().get(0);

            StringContent data2update = new StringContent();
            data2update.setGlobalId(gdrDataInDb.getId());
            data2update.setContent(value);
            data2update.setContentLength((short) value.length());

            GlobalDataRecord gdrData2update = new GlobalDataRecord();
            gdrData2update.setId(gdrDataInDb.getId());
            gdrData2update.setUpdateTime(nowTime);
            gdrData2update.setModifiedCount(gdrDataInDb.getModifiedCount() + 1);

            // 更新数据
            int affectedRowCount = 0;
            // 更新 全局ID表
            affectedRowCount = suid.update(gdrData2update);
            if (affectedRowCount == 1) {
                // 更新 字符串表
                affectedRowCount = suid.update(data2update);
                if (affectedRowCount == 1) {
                    transaction.commit();
                    result.setSimpleResult(SimpleCoreTableCurdResult.SUCCEED);
                    result.setStringContent(data2update);
                } else {
                    transaction.rollback();
                    result.setSimpleResult(SimpleCoreTableCurdResult.FAILED_OPERATION_REJECTED);
                }
            } else {
                transaction.rollback();
                result.setSimpleResult(SimpleCoreTableCurdResult.FAILED_OPERATION_REJECTED);
            }
        } catch (Exception e) {
            sqlErrorHandler.defaultErrorHandler(e, transaction, result);
        }

        return result;
    }

    @Override
    public StringTypeResult updateStringByGlobalId(String value, Long globalId) {
        Condition condition = new ConditionImpl();
        condition.op(StringContentConstant.GLOBAL_ID, Op.eq, globalId);
        return updateStringByCondition(value, condition);
    }

    public StringTypeResult updateStringByFullText(String oldValue, String value) {
        Condition condition = new ConditionImpl();
        condition.op(StringContentConstant.DB_CONTENT, Op.eq, oldValue);
        return updateStringByCondition(value, condition);
    }

    @Override
    public StringTypeResult selectStringByFullText(String value, String[] fields) {
        StringTypeResult result = new StringTypeResult();

        StringContentGlobalRecordAssociation association = new StringContentGlobalRecordAssociation();
        association.setContent(value);

        Condition condition = new ConditionImpl();
        if (fields != null) {
            condition.selectField(fields);
        }

        // 开启事务
        Transaction transaction = SessionFactory.getTransaction();
        try {
            transaction.begin();
            transaction.setTransactionIsolation(TransactionIsolationLevel.TRANSACTION_REPEATABLE_READ);
            HoneyFactory honeyFactory = BeeFactory.getHoneyFactory();
            MoreTable moreTable = honeyFactory.getMoreTable();

            // 查询数据
            association = moreTable.select(association, condition).get(0);
            result.setGlobalDataRecord(association.getGlobalDataRecords().get(0));

            transaction.commit();

            StringContent stringContent = new StringContent();
            stringContent.setGlobalId(association.getGlobalId());
            stringContent.setContent(association.getContent());
            stringContent.setContentLength(association.getContentLength());
            stringContent.setDataFormat(association.getDataFormat());
            result.setStringContent(stringContent);

            result.setSimpleResult(SimpleCoreTableCurdResult.SUCCEED);
        } catch (Exception e) {
            sqlErrorHandler.defaultErrorHandler(e, transaction, result);
        }

        return result;
    }

    @Override
    public StringTypeResult deleteStringByFullText(String value) {
        StringTypeResult result = new StringTypeResult();

        // 预备一个 StringContent对象 空间
        StringContent searchStringContent = new StringContent();
        searchStringContent.setContent(value);

        // 开启事务
        Transaction transaction = SessionFactory.getTransaction();
        try {
            transaction.begin();
            transaction.setTransactionIsolation(TransactionIsolationLevel.TRANSACTION_REPEATABLE_READ);
            HoneyFactory honeyFactory = BeeFactory.getHoneyFactory();
            SuidRich suid = honeyFactory.getSuidRich();

            // 删除数据
            int affectedRowCount = 0;
            affectedRowCount = suid.delete(searchStringContent);
            if (affectedRowCount == 0) {
                transaction.rollback();
                result.setSimpleResult(SimpleCoreTableCurdResult.FAILED_NOT_FOUND);
            } else {
                transaction.commit();
                result.setSimpleResult(SimpleCoreTableCurdResult.SUCCEED);
            }
        } catch (Exception e) {
            sqlErrorHandler.defaultErrorHandler(e, transaction, result);
        }

        return result;
    }

    @Override
    public MultipleResultBase<StringContent> selectStringByPrefix(String prefix, String[] fields) {
        MultipleResultBase<StringContent> result = null;
        try {
            result = new MultipleResultBase<>(StringContent.class);
        } catch (ClassNotFoundException | NoSuchFieldException e) {
            e.printStackTrace();
            log.error(e.getMessage());
            return null;
        }

        StringContentGlobalRecordAssociation associationQuery = new StringContentGlobalRecordAssociation();

        Condition condition = new ConditionImpl();
        condition.op(
            StringContentConstant.DB_CONTENT,
            Op.like,
            StringEscapeUtils.escapeSql4Like("mysql", prefix) + "%"
        );

        if (fields != null) {
            condition.selectField(fields);
        }


        // 开启事务
        Transaction transaction = SessionFactory.getTransaction();
        try {
            transaction.begin();
            transaction.setTransactionIsolation(TransactionIsolationLevel.TRANSACTION_REPEATABLE_READ);
            HoneyFactory honeyFactory = BeeFactory.getHoneyFactory();

            MoreTable moreTable = honeyFactory.getMoreTable();
            // String sql = ((MoreObjSQL) moreTable).getMoreObjToSQL().toSelectSQL(association, condition);
            // log.info(sql);

            // 查询数据
            List<StringContentGlobalRecordAssociation> associations = moreTable.select(associationQuery, condition);
            transaction.commit();

            for (StringContentGlobalRecordAssociation association : associations) {
                PackedData<StringContent> packedData = new PackedData<>();
                packedData.globalDataRecord = association.getGlobalDataRecords().get(0);
                StringContent stringContent = new StringContent();
                stringContent.setGlobalId(association.getGlobalId());
                stringContent.setDataFormat(association.getDataFormat());
                stringContent.setContentLength(association.getContentLength());
                stringContent.setContent(association.getContent());
                packedData.content = stringContent;
                result.add(packedData);
            }

            result.setSimpleResult(SimpleCoreTableCurdResult.SUCCEED);
        } catch (Exception e) {
            sqlErrorHandler.defaultErrorHandler(e, transaction, result);
        }

        return result;
    }
}
