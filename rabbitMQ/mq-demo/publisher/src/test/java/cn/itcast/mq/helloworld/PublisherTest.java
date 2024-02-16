package cn.itcast.mq.helloworld;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

public class PublisherTest {
    @Test
    public void testSendMessage() throws IOException, TimeoutException {
        // 1.建立连接
        ConnectionFactory factory = new ConnectionFactory();
        // 1.1.设置连接参数，分别是：主机名、端口号、vhost、用户名、密码
        factory.setHost("20.14.93.178");
        factory.setPort(5672);
        factory.setVirtualHost("/");
        factory.setUsername("root");
        factory.setPassword("wodediannao");
        // 1.2.建立连接
        Connection connection = factory.newConnection();

        // 2.创建通道Channel
        Channel channel = connection.createChannel();

        // 3.创建队列
        String queueName = "simple.queue";
        //* 如果没有该名字的队列,则自动创建
        channel.queueDeclare(queueName, false, false, false, null);
        /** queueDeclare(String queue, bootean durable, boolean exclusive, boolean autoDelete, Map<String, Object> arguments)
         *  1. queue:   队列名称
         *  2. durable: 是否持久化,当mq重启之后,是否还保留
         *  3. exclusive:
         *      是否独占,只能有一个消费者监听这个队列
         *      当cennection关闭的时候,是否删除队列
         *  4. autoDelete:  是否自动删除.当没有Consumer时,自动删除
         *  5. arguments:   相关参数
         *
         * */


        // 4.发送消息
        String message = "hello, rabbitmq!";
        channel.basicPublish("", queueName, null, message.getBytes());
        /** basicPublish(String exchange, String routingKey, BasicProperties props, byte[] body)
         *  1. exchange:    交换机名称.简单模式下交换机使用默认的名称
         *  2. routingKey:  路由名称
         *  3. properties
         *  4. body:    真实的发送的数据,以字节信息
         *
         * */
        System.out.println("发送消息成功：【" + message + "】");

        // 5.关闭通道和连接
        channel.close();
        connection.close();

    }
}
