package cc.xfl12345.mybigdata.server.config;

import cc.xfl12345.mybigdata.server.pojo.ResourceCacheMapBean;
import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.text.SimpleDateFormat;
import java.util.SimpleTimeZone;

@Configuration
public class IndependenceBeansConfig {
    @Bean(name = "defaultTimeZone")
    public SimpleTimeZone getDefaultTimeZone() {
        return new SimpleTimeZone(28800000, "China Standard Time");
    }

    @Bean(name = "defaultDateFormat")
    public SimpleDateFormat getDefaultDateFormat() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    }

    @Bean(name = "millisecondFormatter")
    public SimpleDateFormat getMillisecondFormatter() {
        return new SimpleDateFormat("SSS");
    }

    @Bean(name = "fullDateFormat")
    public SimpleDateFormat getFullDateFormat() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
    }

    @Bean(name = "uuidGenerator")
    public TimeBasedGenerator getTimeBasedGenerator() {
        return Generators.timeBasedGenerator();
    }

    @Bean(name = "resourceCacheMapBean")
    public ResourceCacheMapBean getResourceCacheBean() {
        return new ResourceCacheMapBean();
    }
}
