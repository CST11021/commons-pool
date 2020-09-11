package com.whz.commons.pool2;

import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.PooledObjectFactory;
import org.apache.commons.pool2.impl.DefaultPooledObject;

/**
 * PoolableObjectFactory custom impl.
 */
public class JedisFactory implements PooledObjectFactory {

    /**
     * 借出去前调用
     *
     * @param pooledJedis
     * @throws Exception
     */
    @Override
    public void activateObject(PooledObject pooledJedis) throws Exception {
        // final BinaryJedis jedis = pooledJedis.getObject();
        // if (jedis.getDB() != database) {
        //     jedis.select(database);
        // }
    }

    /**
     * 销毁对象
     *
     * @param pooledJedis
     * @throws Exception
     */
    @Override
    public void destroyObject(PooledObject pooledJedis) throws Exception {
        // final BinaryJedis jedis = pooledJedis.getObject();
        // if (jedis.isConnected()) {
        //     try {
        //         try {
        //             jedis.quit();
        //         } catch (Exception e) {
        //         }
        //         jedis.disconnect();
        //     } catch (Exception e) {
        //     }
        // }
    }

    /**
     * 创建对象
     *
     * @return
     * @throws Exception
     */
    @Override
    public PooledObject makeObject() throws Exception {
        // final HostAndPort hp = this.hostAndPort.get();
        // final Jedis jedis = new Jedis(hp.getHost(), hp.getPort(), connectionTimeout, soTimeout,
        //         ssl, sslSocketFactory, sslParameters, hostnameVerifier);
        // try {
        //     jedis.connect();
        //     if (user != null) {
        //         jedis.auth(user, password);
        //     } else if (password != null) {
        //         jedis.auth(password);
        //     }
        //     if (database != 0) {
        //         jedis.select(database);
        //     }
        //     if (clientName != null) {
        //         jedis.clientSetname(clientName);
        //     }
        // } catch (JedisException je) {
        //     jedis.close();
        //     throw je;
        // }

        return new DefaultPooledObject<>(null);
    }

    /**
     * 反初始化，每次回收的时候都会执行这个方法
     *
     * @param pooledJedis
     * @throws Exception
     */
    @Override
    public void passivateObject(PooledObject pooledJedis) throws Exception {
        // TODO maybe should select db 0? Not sure right now.
    }

    /**
     * 检查该对象实例是否是初始化的状态，当对象被借出去前，或者归还到池子前都会调用该方法，如果对象无效，则无法借出或者归还会池子，此时返回false
     *
     * @param pooledJedis
     * @return
     */
    @Override
    public boolean validateObject(PooledObject pooledJedis) {
        // final BinaryJedis jedis = pooledJedis.getObject();
        // try {
        //     HostAndPort hostAndPort = this.hostAndPort.get();
        //
        //     String connectionHost = jedis.getClient().getHost();
        //     int connectionPort = jedis.getClient().getPort();
        //
        //     return hostAndPort.getHost().equals(connectionHost)
        //             && hostAndPort.getPort() == connectionPort && jedis.isConnected()
        //             && jedis.ping().equals("PONG");
        // } catch (final Exception e) {
        //     return false;
        // }

        return true;
    }
}