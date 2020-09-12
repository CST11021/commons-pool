package com.whz.commons.pool2;

import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

public class JedisPoolConfig extends GenericObjectPoolConfig {

    public JedisPoolConfig() {

        // 驱逐测试后，如果对象还保留在池子里，则再次对该对象进行初始化和反初始化测试，测试失败，则销毁对象
        setTestWhileIdle(true);
        // 对象的空闲1分钟后自动被销毁
        setMinEvictableIdleTimeMillis(60000);
        // 半分钟执行一次驱逐测试
        setTimeBetweenEvictionRunsMillis(30000);
        setNumTestsPerEvictionRun(-1);
    }

}