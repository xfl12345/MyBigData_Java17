package cc.xfl12345.mybigdata.server;

import cc.xfl12345.mybigdata.server.initializer.MyDatabaseInitializer;
import cc.xfl12345.mybigdata.server.listener.ContextFinalizer;
import com.alibaba.druid.pool.DruidDataSource;

public class TestDatabaseReset {
    public static void main(String[] args) throws Exception {
        DruidDataSource dataSource = TestLoadDataSource.getDataSource();

        MyDatabaseInitializer databaseInitializer = new MyDatabaseInitializer();
        databaseInitializer.setUrl(dataSource.getUrl());
        databaseInitializer.setDriverClassName(dataSource.getDriverClassName());
        databaseInitializer.setUsername(dataSource.getUsername());
        databaseInitializer.setPassword(dataSource.getPassword());

        dataSource.getConnection().createStatement()
            .execute("drop database if exists xfl_mybigdata");

        databaseInitializer.afterPropertiesSet();
        ContextFinalizer.deregisterJdbcDriver(null);
    }

}
