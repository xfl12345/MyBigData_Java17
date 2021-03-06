package cc.xfl12345.mybigdata.server.model.database.handler.impl;


import cc.xfl12345.mybigdata.server.appconst.CURD;
import cc.xfl12345.mybigdata.server.appconst.CoreTableNames;
import cc.xfl12345.mybigdata.server.appconst.SimpleCoreTableCurdResult;
import cc.xfl12345.mybigdata.server.model.database.error.SqlErrorHandler;
import cc.xfl12345.mybigdata.server.model.database.constant.StringContentConstant;
import cc.xfl12345.mybigdata.server.model.database.error.TableDataException;
import cc.xfl12345.mybigdata.server.model.database.error.TableOperationException;
import cc.xfl12345.mybigdata.server.model.database.handler.NumberTypeHandler;
import cc.xfl12345.mybigdata.server.model.database.handler.StringTypeHandler;
import cc.xfl12345.mybigdata.server.model.database.table.GlobalDataRecord;
import cc.xfl12345.mybigdata.server.model.database.table.IntegerContent;
import cc.xfl12345.mybigdata.server.model.database.table.StringContent;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.teasoft.bee.osql.*;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import static cc.xfl12345.mybigdata.server.utility.BeeOrmUtils.getSuidRich;

@Slf4j
public class NumberTypeHandlerImpl extends AbstractCoreTableHandler implements NumberTypeHandler {

    @Getter
    @Setter
    protected SqlErrorHandler sqlErrorHandler = null;

    @Getter
    @Setter
    protected volatile StringTypeHandler stringTypeHandler = null;

    @Override
    public void afterPropertiesSet() throws Exception {
        super.afterPropertiesSet();
        if (stringTypeHandler == null) {
            throw new IllegalArgumentException(fieldCanNotBeNullMessageTemplate.formatted("stringTypeHandler"));
        }
        if (sqlErrorHandler == null) {
            throw new IllegalArgumentException(fieldCanNotBeNullMessageTemplate.formatted("sqlErrorHandler"));
        }
    }

    protected Long insertInteger(Long value) {
        Date date = new Date();
        GlobalDataRecord globalDataRecord = getNewRegisteredGlobalDataRecord(
            date,
            coreTableCache.getTableNameCache().getValue(CoreTableNames.INTEGER_CONTENT.getName())
        );
        SuidRich suid = getSuidRich();
        IntegerContent content = new IntegerContent();
        content.setContent(value);
        content.setGlobalId(globalDataRecord.getId());
        return suid.insertAndReturnId(content);
    }

    protected Long insertNumberAsString(BigDecimal value) {
        Date date = new Date();
        GlobalDataRecord globalDataRecord = getNewRegisteredGlobalDataRecord(
            date,
            coreTableCache.getTableNameCache().getValue(CoreTableNames.STRING_CONTENT.getName())
        );
        SuidRich suid = getSuidRich();
        StringContent content = new StringContent();
        content.setContent(value.toPlainString());
        content.setGlobalId(globalDataRecord.getId());
        return suid.insertAndReturnId(content);
    }

    @Override
    public Long insert(BigDecimal value) {
        if (isLongInteger(value)) { // ????????? long??? ?????????????????? ??????
            return insertInteger(value.longValue());
        } else { // ???????????? long??? ?????????????????? ????????????????????????????????????????????????????????????
            return insertNumberAsString(value);
        }
    }

    @Override
    public Long insertOrSelect4Id(BigDecimal value) throws Exception {
        Long globalId;
        try {
            globalId = insert(value);
        } catch (BeeException beeException) {
            SimpleCoreTableCurdResult curdResult = sqlErrorHandler.getSimpleCoreTableCurdResult(beeException);
            if (curdResult.equals(SimpleCoreTableCurdResult.DUPLICATE)) {
                globalId = selectId(value);
            } else {
                throw beeException;
            }
        }

        return globalId;
    }

    @Override
    public Long selectId(BigDecimal value) throws TableOperationException {
        Long result = null;
        SuidRich suid = getSuidRich();

        if (isLongInteger(value)) {
            IntegerContent integerContent = new IntegerContent();
            integerContent.setContent(value.longValue());
            List<IntegerContent> integerContents = suid.select(integerContent);
            int affectedRowCount = integerContents.size();
            // ????????????????????????????????????
            if (affectedRowCount == 1) {
                result = integerContents.get(0).getGlobalId();
            } else if (affectedRowCount != 0) {
                throw getAffectedRowShouldBe1Exception(
                    affectedRowCount,
                    CURD.RETRIEVE,
                    CoreTableNames.INTEGER_CONTENT.getName()
                );
            }
        } else {
            result = stringTypeHandler.selectId(value.toPlainString());
        }

        return result;
    }

    @Override
    public void updateById(BigDecimal value, Long globalId) throws TableOperationException, TableDataException {
        Date date = new Date();
        boolean isInteger = isLongInteger(value);
        String numberInString = value.toPlainString();

        SuidRich suid = getSuidRich();

        GlobalDataRecord gdrInDb = suid.selectById(new GlobalDataRecord(), globalId);
        String tableName = coreTableCache.getTableNameCache().getKey(gdrInDb.getTableName());
        switch (CoreTableNames.valueOf(tableName)) {
            case STRING_CONTENT -> {
                // ?????????????????? ?????????????????????????????? ????????????
                // ????????????????????? ????????? ?????? ??????????????????
                // ????????????????????? global_id ???????????? ???????????????
                // ??????????????????????????????????????????????????????????????????????????????
                // ????????? ??????????????? ???
                if (isInteger) {
                    StringContent stringContent = new StringContent();
                    stringContent.setGlobalId(globalId);
                    int affectedRowCount = 0;
                    // ?????????????????????
                    affectedRowCount = suid.delete(stringContent);
                    if (affectedRowCount == 1) { // ?????????????????????????????????????????? ?????????
                        IntegerContent content = new IntegerContent();
                        content.setContent(value.longValue());
                        content.setGlobalId(globalId);

                        // ?????????????????????
                        affectedRowCount = suid.insert(content);
                        // ?????????????????????
                        if (affectedRowCount == 1) {
                            // ?????????????????? ???????????????
                            GlobalDataRecord gdr4update = new GlobalDataRecord();
                            gdr4update.setId(gdrInDb.getId());
                            // ??????????????????
                            gdr4update.setUpdateTime(date);
                            // ??????????????????
                            gdr4update.setModifiedCount(gdrInDb.getModifiedCount() + 1);
                            // ???????????? ????????? ?????????
                            gdr4update.setTableName(
                                coreTableCache.getTableNameCache()
                                    .getValue(CoreTableNames.INTEGER_CONTENT.getName())
                            );

                            // ????????????
                            affectedRowCount = suid.update(gdr4update);
                            if (affectedRowCount != 1) {
                                throw getUpdateShouldBe1Exception(
                                    affectedRowCount,
                                    CoreTableNames.GLOBAL_DATA_RECORD.getName()
                                );
                            }

                        } else {
                            throw getAffectedRowShouldBe1Exception(
                                affectedRowCount,
                                CURD.CREATE,
                                CoreTableNames.INTEGER_CONTENT.getName()
                            );
                        }

                    } else {
                        throw getAffectedRowShouldBe1Exception(
                            affectedRowCount,
                            CURD.DELETE,
                            CoreTableNames.STRING_CONTENT.getName()
                        );
                    }

                } else {
                    stringTypeHandler.updateStringByGlobalId(numberInString, globalId);
                }
            }

            case INTEGER_CONTENT -> {
                // ??????????????????????????????????????????????????????
                if (isInteger) {
                    IntegerContent integerContent = new IntegerContent();
                    integerContent.setGlobalId(globalId);
                    integerContent.setContent(value.longValue());

                    int affectedRowCount = 0;
                    affectedRowCount = suid.update(integerContent);
                    if (affectedRowCount != 1) {
                        throw getUpdateShouldBe1Exception(
                            affectedRowCount,
                            CoreTableNames.INTEGER_CONTENT.getName()
                        );
                    }
                } else {
                    // ????????????????????? ????????????????????????????????? ?????????
                    IntegerContent integerContent = new IntegerContent();
                    integerContent.setGlobalId(globalId);
                    int affectedRowCount = 0;
                    // ????????????????????????
                    affectedRowCount = suid.delete(integerContent);
                    if (affectedRowCount == 1) { // ?????????????????????????????????????????? ????????????
                        StringContent content = new StringContent();
                        content.setContent(numberInString);
                        content.setGlobalId(gdrInDb.getId());

                        // ????????????
                        affectedRowCount = suid.insert(content);
                        // ?????????????????????
                        if (affectedRowCount == 1) {
                            // ?????????????????? ???????????????
                            GlobalDataRecord gdr4update = new GlobalDataRecord();
                            gdr4update.setId(globalId);
                            // ??????????????????
                            gdr4update.setUpdateTime(date);
                            // ??????????????????
                            gdr4update.setModifiedCount(gdrInDb.getModifiedCount() + 1);
                            // ????????? ????????? ????????????
                            gdr4update.setTableName(
                                coreTableCache.getTableNameCache()
                                    .getValue(CoreTableNames.STRING_CONTENT.getName())
                            );

                            // ????????????
                            affectedRowCount = suid.update(gdr4update);
                            if (affectedRowCount == 1) {
                                throw getUpdateShouldBe1Exception(
                                    affectedRowCount,
                                    CoreTableNames.GLOBAL_DATA_RECORD.getName()
                                );
                            }


                        } else {
                            throw getAffectedRowShouldBe1Exception(
                                affectedRowCount,
                                CURD.CREATE,
                                CoreTableNames.STRING_CONTENT.getName()
                            );
                        }
                    } else {
                        throw getAffectedRowShouldBe1Exception(
                            affectedRowCount,
                            CURD.DELETE,
                            CoreTableNames.INTEGER_CONTENT.getName()
                        );
                    }
                }

            }
            default -> {
                throw new TableDataException(
                    "????????????????????? ????????? ?????????????????? ???????????? ?????????????????????????????????????????????"
                );
            }
        }
    }

    @Override
    public void delete(BigDecimal value) throws TableOperationException {
        SuidRich suid = getSuidRich();
        Long globalId = selectId(value);
        int affectedRowCount;

        // ????????????
        if (isLongInteger(value)) {
            IntegerContent integerContent = new IntegerContent();
            integerContent.setGlobalId(globalId);
            affectedRowCount = suid.delete(integerContent);
            checkAffectedRowShouldBe1(affectedRowCount, CURD.DELETE, CoreTableNames.INTEGER_CONTENT.getName());
        } else {
            StringContent stringContent = new StringContent();
            stringContent.setGlobalId(globalId);
            affectedRowCount = suid.delete(stringContent);
            checkAffectedRowShouldBe1(affectedRowCount, CURD.DELETE, CoreTableNames.STRING_CONTENT.getName());
        }

        GlobalDataRecord globalDataRecord = new GlobalDataRecord();
        globalDataRecord.setId(globalId);
        affectedRowCount = suid.delete(globalDataRecord);
        checkAffectedRowShouldBe1(affectedRowCount, CURD.DELETE, CoreTableNames.GLOBAL_DATA_RECORD.getName());
    }

    public boolean isLongInteger(BigDecimal value) {
        return value.scale() == 0 && new BigDecimal(value.longValue()).compareTo(value) == 0;
    }
}
