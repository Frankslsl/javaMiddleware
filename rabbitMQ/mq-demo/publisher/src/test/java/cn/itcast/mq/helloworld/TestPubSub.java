package cn.itcast.mq.helloworld;

import com.rabbitmq.client.*;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

public class TestPubSub {

    public static void main(String[] args) throws IOException, TimeoutException {
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


        //声明交换机
        /**
         * exchangeDeclare(String exchange, BuiltinExchangeType var2, boolean durable, boolean autoDelete, boolean internal, Map<String, Object> arguments);
         * 1. exchange: 交换机名称
         * 2. BuiltinExchangeType: 交换机的类型 -->
         *      DIRECT("direct"),:定向方式
         *     FANOUT("fanout"),: 扇形(广播) --> 发送消息道每一个与之绑定的队列
         *     TOPIC("topic"),: 通配符的方式
         *     HEADERS("headers"): 参数匹配
         * 3. durable: 持久化
         * 4. autoDelete: 是否自动删除
         * 5. internal: 内部使用,一般设置false
         * 6. arguments: 参数列表
         */
        String exchangeName = "test_fanout";
        channel.exchangeDeclare(exchangeName, BuiltinExchangeType.FANOUT, true, false, false, null);
        //声明队列
        // 3.创建队列
        String queueName1 = "simple.queue1";
        String queueName2 = "simple.queue2";
        channel.queueDeclare(queueName1, false, false, false, null);
        channel.queueDeclare(queueName2, false, false, false, null);

        /*队列和交换机绑定
        *    queue名字
        *   exchange名字
        *  routingKey路由键 ---> 如果交换机的类型为fanout,则routingKey设置为""
        * */

        channel.queueBind(queueName1,exchangeName,"");
        channel.queueBind(queueName2,exchangeName,"");
        // 4.订阅消息
        String body="日志信息,方法已经被调用";
        channel.basicPublish(exchangeName,"",null, body.getBytes());
        channel.close();
        connection.close();

        System.out.println("等待接收消息。。。。");
    }
}
