package cc.xfl12345.mybigdata.server.model.database.producer;

import cc.xfl12345.mybigdata.server.model.database.table.GlobalDataRecord;
import com.fasterxml.uuid.NoArgGenerator;
import lombok.extern.slf4j.Slf4j;
import org.teasoft.bee.osql.*;
import org.teasoft.bee.osql.transaction.Transaction;
import org.teasoft.honey.osql.core.BeeFactory;
import org.teasoft.honey.osql.core.BeeFactoryHelper;
import org.teasoft.honey.osql.core.ConditionImpl;
import org.teasoft.honey.osql.core.SessionFactory;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class GlobalDataRecordProducer extends AbstractPooledProducer<GlobalDataRecord> {


    public GlobalDataRecordProducer(NoArgGenerator uuidGenerator) {
        super();
        this.uuidGenerator = uuidGenerator;
        GlobalDataRecordProducer myself = this;
        producingThread = new Thread() {
            @Override
            public void run() {
                SuidRich suid = BeeFactory.getHoneyFactory().getSuidRich();

                // 先缓存 SQL条件判断组件
                Condition condition = new ConditionImpl();
                // 如果 "table_name" 字段是空的，那么意味着它将被回收用作它途。
                condition.op("table_name", Op.eq, null);

                while (keepProduce) {
                    // 先取出，投入到资源池里
                    List<GlobalDataRecord> items = suid.select(new GlobalDataRecord(), condition);
                    int itemsCount = items.size();
                    for (int i = 0; i < itemsCount && keepProduce; i++) {
                        try {
                            resourcePool.putLast(items.get(i));
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                            log.error(e.getMessage());
                        }
                    }
                    if (keepProduce) {
                        // 不够再凑数
                        int remainingCapacity = resourcePool.remainingCapacity();
                        int produceCount = remainingCapacity + 1;
                        // 预分配至少 2倍 空间，防止触发扩容
                        ArrayList<GlobalDataRecord> records = new ArrayList<>((produceCount << 1));
                        for (int i = 0; i < produceCount && keepProduce; i++) {
                            GlobalDataRecord globalDataRecord = new GlobalDataRecord();
                            globalDataRecord.setUuid(myself.uuidGenerator.generate().toString());
                            records.add(globalDataRecord);
                        }
                        if (keepProduce) {
                            suid.insert(records);
                        }
                    }
                }
            }
        };
        preProduceThread = new Thread() {
            @Override
            public void run() {
                if (keepProduce) {
                    producingThread.start();
                }
            }
        };
        preProduceThread.start();
    }
}