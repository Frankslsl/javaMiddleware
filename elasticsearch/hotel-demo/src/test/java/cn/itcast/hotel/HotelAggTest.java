package cn.itcast.hotel;

import cn.itcast.hotel.service.IHotelService;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.search.aggregations.*;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 聚合的例子
 */
@SpringBootTest
public class HotelAggTest {
    @Autowired
    private RestHighLevelClient client;
    @Autowired
    private IHotelService hotelService;
    private SearchRequest request = new SearchRequest("hotel");

    @Test
    public void testAggregation() throws IOException {
        //准备request
        //准备dsl
        //1.1去掉文档数据
        request.source().size(0);
        //1.2聚合的dsl
        request.source().aggregation(AggregationBuilders
                .terms("brandAggs")
                .field("brand")
                .size(10)
                .order(BucketOrder.aggregation("_count",true))
        );
        //发送请求
        SearchResponse response = client.search(request, RequestOptions.DEFAULT);
        //处理结果
        //先拿到聚合结果
        Aggregations aggregations = response.getAggregations();
        //通过聚合名字,拿到Terms类型的聚合结果
        Terms brandAggs = aggregations.get("brandAggs");
        //拿到聚合结果中的buckets
        List<? extends Terms.Bucket> buckets = brandAggs.getBuckets();
        //遍历桶中的每个元素
        for (Terms.Bucket bucket: buckets
             ) {
            //取出每个元素的key
            String keyAsString = bucket.getKeyAsString();
            long docCount = bucket.getDocCount();

            System.out.println(keyAsString + docCount);
        }


    }



}
