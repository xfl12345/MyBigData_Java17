package cc.xfl12345.mybigdata.server.listener;

import com.alibaba.druid.pool.DruidDataSource;
import com.mysql.cj.jdbc.AbandonedConnectionCleanupThread;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.vfs2.FileSystemManager;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.stereotype.Component;
import sun.misc.Signal;
import sun.misc.SignalHandler;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;
import javax.sql.DataSource;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Enumeration;
import java.util.List;
import java.util.TreeSet;

/**
 * 这是一个用来执行正常退出任务的类
 */
@Component
@WebListener
@Slf4j
public class ContextFinalizer implements ServletContextListener, ApplicationListener<ContextClosedEvent>, ApplicationContextAware, SignalHandler {
    protected ApplicationContext applicationContext;

    public ContextFinalizer() {
        // 注册要监听的信号
        Signal.handle(new Signal("INT"), this);     // 2  : 中断（同 ctrl + c ）
        Signal.handle(new Signal("TERM"), this);    // 15 : 正常终止
    }

    @Autowired
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    public void contextInitialized(ServletContextEvent sce) {
    }

    public void handle(Signal signal) {
        String signalName = signal.getName();
        log.info(signalName+":"+signal.getNumber());
        if(signalName.equals("INT") || signalName.equals("TERM")) {
            SpringApplication.exit(applicationContext);
        }
    }

    public void contextDestroyed(ServletContextEvent sce) {
        // AbandonedConnectionCleanupThread.shutdown();
        Thread thread = new Thread() {
            @Override
            public void run() {
                try {
                    AbandonedConnectionCleanupThread.checkedShutdown();
                    long checkPerMillionSecond = 200L;
                    int checkRound = 20;
                    //尝试休眠2秒，等待JDBC驱动完全从内存释放，防止过快被热部署重新注册JDBC驱动
                    log.info("Wait clean thread task finished...");
                    for (; checkRound > 0 && AbandonedConnectionCleanupThread.isAlive(); checkRound--) {
                        AbandonedConnectionCleanupThread.checkedShutdown();
                        Thread.sleep(checkPerMillionSecond);
                    }
                } catch (Exception e) {
                    log.error(e.getMessage());
                }
                if (AbandonedConnectionCleanupThread.isAlive()) {
                    log.info("Clean thread failed.");
                } else {
                    log.info("Clean thread succeed.");
                }
            }
        };
        thread.start();
    }

    @Override
    public void onApplicationEvent(ContextClosedEvent event) {
        ApplicationContext applicationContext = event.getApplicationContext();
        if (applicationContext.getParent() == null) {
            deregisterMybatisJdbcDriver(applicationContext);
            FileSystemManager fileSystemManager = applicationContext.getBean("apacheVfsFileSystemManager", FileSystemManager.class);
            fileSystemManager.close();
        }
    }

    /**
     * 这是一个用来结束JDBC驱动的函数，防止redeploy时被警告存在内存泄露风险
     */
    public void deregisterMybatisJdbcDriver(ApplicationContext springAppContext) {
        Enumeration<Driver> drivers = DriverManager.getDrivers();
        TreeSet<String> dataSourceBeanNames = new TreeSet<>();
        if (drivers.hasMoreElements()) {
            if (springAppContext != null) {
                dataSourceBeanNames = new TreeSet<>(List.of(springAppContext.getBeanNamesForType(DruidDataSource.class)));
            }
            Driver d = null;
            DataSource dataSource;
            String driverInstanceName;
            int dataSourceBeanNamesCount = dataSourceBeanNames.size();
            boolean isFirstOneDeregister = dataSourceBeanNamesCount == 0;
            while (drivers.hasMoreElements()) {
                try {
                    d = drivers.nextElement();
                    //优先卸载JDBC驱动
                    if (isFirstOneDeregister) {
                        driverInstanceName = d.toString();
                        DriverManager.deregisterDriver(d);
                        log.info(String.format("Driver %s deregistered", driverInstanceName));
                    } else {
                        boolean isHitAsLeaseOnce = false;
                        @SuppressWarnings("unchecked")
                        TreeSet<String> tmpCopy = (TreeSet<String>) dataSourceBeanNames.clone();
                        for (String beanName : tmpCopy) {
                            dataSource = springAppContext.getBean(beanName, DataSource.class);
                            String dataSourceDriverName = "";
                            if (d.getClass().getCanonicalName().equals(dataSourceDriverName)) {
                                driverInstanceName = d.toString();
                                ((BeanDefinitionRegistry) springAppContext.getAutowireCapableBeanFactory()).removeBeanDefinition(beanName);
                                Runtime.getRuntime().gc();
                                log.info(String.format("Bean[%s] has been removed definition from Spring context.", beanName));
                                if (!isHitAsLeaseOnce) {
                                    isHitAsLeaseOnce = true;
                                    DriverManager.deregisterDriver(d);
                                    log.info(String.format("Driver %s deregistered", driverInstanceName));
                                }
                                dataSourceBeanNames.remove(beanName);
                                dataSourceBeanNamesCount--;
                                isFirstOneDeregister = dataSourceBeanNamesCount == 0;
                            }
                        }
                    }
                } catch (SQLException ex) {
                    log.error(String.format("Error deregistering driver %s", d) + ":" + ex);
                }
                //像队列一样遍历列表。循环到队列为空的时候才退出
                if (!drivers.hasMoreElements()) {
                    //如果没有MyBatis的DataSource，则不需要先卸载JDBC驱动
                    if (!isFirstOneDeregister) {
                        isFirstOneDeregister = true;
                    }
                    drivers = DriverManager.getDrivers();
                }
            }
            log.info("JDBC driver clean.");
        }
    }
}
