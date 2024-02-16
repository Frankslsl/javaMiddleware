package cn.itcast.hotel;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.json.JSONUtil;
import cn.itcast.hotel.pojo.HotelDoc;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.search.TotalHits;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.elasticsearch.search.sort.SortOrder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.util.Map;

/**
 * 检索的例子
 */
@SpringBootTest
@Slf4j
public class HotelQueryDemo {
    @Autowired
    private RestHighLevelClient client;
    //准备request
    private final SearchRequest request = new SearchRequest("hotel");
    @Test
    public void queryMatchAll() throws IOException {
        //准备request

        //组织DSL
        request.source().query(QueryBuilders.matchAllQuery());
        //发送请求
        SearchResponse search = client.search(request, RequestOptions.DEFAULT);
        //处理结果

        handleResponse(search);
    }


    @Test
    public void queryMatch() throws IOException {
        //准备request
        //组织DSL
        request.source().query(QueryBuilders.matchQuery("name", "上海"));
        SearchResponse response = client.search(request, RequestOptions.DEFAULT);

        //处理返回结果
        handleResponse(response);
    }

    @Test
    public void term() throws IOException {
        request.source().query(QueryBuilders.termQuery("brand", "如家"));
        SearchResponse response = client.search(request, RequestOptions.DEFAULT);
        handleResponse(response);
    }
    @Test
    public void boolenQuery() throws IOException {
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        boolQueryBuilder.must(QueryBuilders.termQuery("city", "上海"));
        boolQueryBuilder.filter(QueryBuilders.rangeQuery("price").lt(500));
        request.source().query(boolQueryBuilder);
        SearchResponse response = client.search(request, RequestOptions.DEFAULT);
        handleResponse(response);
    }

    @Test
    public void sortTest() throws IOException {
        request.source().query(QueryBuilders.matchAllQuery());
        request.source().sort("price", SortOrder.ASC);
        request.source().from(0).size(20);
        SearchResponse search = client.search(request, RequestOptions.DEFAULT);
        handleResponse(search);
    }
    @Test
    public void hightLightQuery() throws IOException {
        //准备highlight的请求
        request.source().query(QueryBuilders.matchQuery("name", "上海"));
        request.source().highlighter(new HighlightBuilder().field("name").requireFieldMatch(false));

        SearchResponse response = client.search(request, RequestOptions.DEFAULT);
        //处理highlight结果
        TotalHits totalHits = response.getHits().getTotalHits();
        System.out.println("totalHits.value = " + totalHits.value);
        SearchHits hits = response.getHits();
        for (SearchHit hit:hits
             ) {
            //获取文本结果
            String sourceAsString = hit.getSourceAsString();
            //获得对应的bean
            HotelDoc bean = JSONUtil.toBean(sourceAsString, HotelDoc.class);
            //获得高亮结果
            Map<String, HighlightField> highlightFields = hit.getHighlightFields();
            if (!CollectionUtil.isEmpty(highlightFields)){
            //根据字段获得高亮结果
            HighlightField name = highlightFields.get("name");
            if (name != null){
            //获得高良知
            String string = name.getFragments()[0].string();
            //覆盖非高亮值
                bean.setName(string);
            }

                System.out.println(bean.toString());
            }
        }

    }


    private static void handleResponse(SearchResponse response) {
        SearchHits hits = response.getHits();
        long value = hits.getTotalHits().value;
        System.out.println("value = " + value);
        for (SearchHit hit:hits
             ) {
            HotelDoc hitString = JSONUtil.toBean(hit.getSourceAsString(), HotelDoc.class);
            System.out.println("hitString = " + hitString);
        }
    }
}


