package cn.itcast.hotel.constants;

/**
 *
 */
public class MqConstants {
    //交换机的名字
    public static final String HOTEL_EXCHANGE = "hotel.topic";
    //添加或者修改的队列
    public static final String HOTEL_INSERT_QUEUE = "hotel.insert.queue";
    //删除队列
    public static final String HOTEL_DELETE_QUEUE = "hotel.delete.queue";
    //添加修改的routing key
    public static final String HOTEL_INSERT_KEY = "hotel.insert.key";
    //删除的routing key
    public static final String HOTEL_DELETE_KEY = "hotel.delete.key";
}
