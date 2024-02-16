package cn.itcast.hotel.mq;

import cn.itcast.hotel.constants.MqConstants;
import cn.itcast.hotel.service.IHotelService;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 *
 */
@Component
public class Listeners {
    @Autowired
    private RestHighLevelClient client;
    @Autowired
    private IHotelService hotelService;

    @RabbitListener(queues = MqConstants.HOTEL_INSERT_QUEUE)
    public void listenHotelInsertOrUpdate(Long id) {
        hotelService.insertByIdIntoEs(id);
    }

    @RabbitListener(queues = MqConstants.HOTEL_DELETE_QUEUE)
    public void listenHoteldelete(Long id) {
        hotelService.deleteByIdFromEs(id);
    }
}
