package cn.itcast.hotel;

import cn.hutool.json.JSONUtil;
import cn.itcast.hotel.constants.Hotel;
import cn.itcast.hotel.pojo.HotelDoc;
import cn.itcast.hotel.service.IHotelService;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.common.xcontent.XContentType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.util.List;

/**
 * 关于index的例子
 */
@SpringBootTest
@Slf4j
public class HotelIndexTest {
    //!进行RestHighLevelClient的初始化,每次需要使用的时候,都要初始化
    @Autowired
    private RestHighLevelClient client;
    @Autowired
    IHotelService hotelService;

    @Test
    public void testInit() {
        System.out.println(client);
    }

    @Test
    public void testCreateIndex() throws IOException {
        //创建request对象
        CreateIndexRequest request = new CreateIndexRequest("hotel");
        //准备请求的参数,也就是dsl语句
        request.source(Hotel.HOTEL_MAPPING_DSL, XContentType.JSON);
        //发送请求
        client.indices().create(request, RequestOptions.DEFAULT);
    }

    @Test
    public void deleteIndex() throws IOException {
        DeleteIndexRequest request = new DeleteIndexRequest("hotel");
        client.indices().delete(request, RequestOptions.DEFAULT);
    }

    @Test
    public void getIndex() throws IOException {
        GetIndexRequest request = new GetIndexRequest("hotel");
        boolean exists = client.indices().exists(request, RequestOptions.DEFAULT);
        System.out.println("exists = " + exists);
    }

    @Test
    public void insertDoc() throws IOException {
        cn.itcast.hotel.pojo.Hotel hotel = hotelService.getById(61083L);
        HotelDoc hotelDoc = new HotelDoc(hotel);
        IndexRequest request = new IndexRequest("hotel").id(hotel.getId().toString());
        IndexRequest source = request.source(JSONUtil.toJsonStr(hotelDoc), XContentType.JSON);
        client.index(request, RequestOptions.DEFAULT);
    }

    @Test
    public void getDocById() throws IOException {
        GetRequest request = new GetRequest("hotel", "61083");
        GetResponse response = client.get(request, RequestOptions.DEFAULT);
        String json = response.getSourceAsString();
        log.error(json);
        HotelDoc bean = JSONUtil.toBean(json, HotelDoc.class);
        log.error(bean.toString());
    }

    @Test
    public void updateById() throws IOException {
        UpdateRequest request = new UpdateRequest("hotel", "61083");
        request.doc(
                "starName", "四星"
        );
        client.update(request, RequestOptions.DEFAULT);

    }

    @Test
    public void deleteById() throws IOException {
        DeleteRequest request = new DeleteRequest("hetel", "61083");
        DeleteResponse delete = client.delete(request, RequestOptions.DEFAULT);
    }

    @Test
    public void testBulkRequest() throws IOException {
        //创建request
        BulkRequest request = new BulkRequest();
        //准备参数
        List<cn.itcast.hotel.pojo.Hotel> list = hotelService.list();
        for (cn.itcast.hotel.pojo.Hotel hotel : list
        ) {
            //转换类型
            HotelDoc hotelDoc = new HotelDoc(hotel);
            //发送bulk命令
            request.add(new IndexRequest("hotel").id(hotelDoc.getId().toString()).source(JSONUtil.toJsonStr(hotelDoc), XContentType.JSON));

        }

        //发送请求
        BulkResponse bulk = client.bulk(request, RequestOptions.DEFAULT);
    }

    void setUp() {//将这个初始化的代码写在config中了,不需要写这儿了
        client = new RestHighLevelClient(RestClient.builder(
                HttpHost.create("192.168.137.137:9200")
        ));
    }


    @AfterEach
    void tearDown() throws IOException {
        client.close();
    }

    @Test
    public void checkExist() throws IOException {
        GetIndexRequest request = new GetIndexRequest("hotel");
        boolean exists = client.indices().exists(request, RequestOptions.DEFAULT);
        log.info("hotel index is existed + {}", exists);

    }
}
