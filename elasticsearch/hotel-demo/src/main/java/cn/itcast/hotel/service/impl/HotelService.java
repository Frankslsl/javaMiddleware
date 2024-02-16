package cn.itcast.hotel.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.json.JSONUtil;
import cn.itcast.hotel.mapper.HotelMapper;
import cn.itcast.hotel.pojo.Hotel;
import cn.itcast.hotel.pojo.HotelDoc;
import cn.itcast.hotel.pojo.PageResult;
import cn.itcast.hotel.pojo.RequestParams;
import cn.itcast.hotel.service.IHotelService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.geo.GeoPoint;
import org.elasticsearch.common.unit.DistanceUnit;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.BucketOrder;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.elasticsearch.search.suggest.Suggest;
import org.elasticsearch.search.suggest.SuggestBuilder;
import org.elasticsearch.search.suggest.SuggestBuilders;
import org.elasticsearch.search.suggest.completion.CompletionSuggestion;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.*;

@Service
@Slf4j
public class HotelService extends ServiceImpl<HotelMapper, Hotel> implements IHotelService {
    @Autowired
    private RestHighLevelClient client;
//    private SearchRequest request = new SearchRequest("hotel");

    @Override
    public PageResult search(RequestParams params) {
        SearchRequest request = new SearchRequest("hotel");
        //准备request
        //准备DSL
        try {
            //构建boolenQuery,通过boolenQuery来携带所有的条件, 封装具体代码到单独的方法中
            BoolQueryBuilder boolQuery = getBoolQueryBuilder(params);
            //分页
            int page = params.getPage();
            int size = params.getSize();
            //根据地理坐标排序
            String location = params.getLocation();
            if (!StringUtils.isEmpty(location)) {
                request.source().sort(SortBuilders.geoDistanceSort("location", new GeoPoint(location))
                        .unit(DistanceUnit.KILOMETERS).order(SortOrder.ASC)
                );
            }
            request.source().from((page - 1) * size).size(params.getSize());
            //完成所有request
            request.source().query(boolQuery);
            //发送请求
            SearchResponse response = client.search(request, RequestOptions.DEFAULT);
            //解析结果
            long totle = response.getHits().getTotalHits().value;
            SearchHits hits = response.getHits();
            log.info("总共有{}", totle);
            ArrayList<HotelDoc> hotelDocs = new ArrayList<>();
            for (SearchHit hit : hits
            ) {
                String sourceAsString = hit.getSourceAsString();
                HotelDoc bean = JSONUtil.toBean(sourceAsString, HotelDoc.class);
                //处理sort结果,也就是距离中心点位置的距离
                Object[] sortValues = hit.getSortValues();
                if (!CollectionUtil.isEmpty(Arrays.asList(sortValues))) {
                    Object sortValue = sortValues[0];
                    bean.setDistance(sortValue);
                }
                hotelDocs.add(bean);
                log.info(bean.toString());
            }
            return new PageResult(totle, hotelDocs);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    //当用户进行关键字搜索的时候,选项也要根据搜索进行修改,所以要先进行筛选
    @Override
    public Map<String, List<String>> filters(RequestParams params) {
        SearchRequest request = new SearchRequest("hotel");
        Map<String, List<String>> result = new HashMap<>();
        //根据穿进来的params来构建boolenBuilder的request来构架查询信息
        BoolQueryBuilder boolQueryBuilder = getBoolQueryBuilder(params);
        request.source().query(boolQueryBuilder);
        //准备requestdsl
        String[] condition = {"city", "brand", "starName"};
        request.source().size(0);
        for (String string : condition
        ) {
            request.source().aggregation(AggregationBuilders.terms(string + "Agg")
                    .size(100)
                    .field(string)
                    .order(BucketOrder.aggregation("_count", false)));
            System.out.println(string + "Agg");
        }
        //发送请求
        SearchResponse response = null;
        try {
            response = client.search(request, RequestOptions.DEFAULT);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        //解析结果
        Aggregations aggregations = response.getAggregations();
        for (String string : condition
        ) {
            Terms aggregation = aggregations.get(string + "Agg");
            List<? extends Terms.Bucket> buckets = aggregation.getBuckets();
            List<String> list = new ArrayList<>();
            for (Terms.Bucket bucket : buckets
            ) {
                String keyAsString = bucket.getKeyAsString();
                list.add(keyAsString);

            }

            result.put(string, list);
        }

        return result;
    }

    @Override
    public List<String> getSuggestions(String prefix) {
        ArrayList<String> result = new ArrayList<>();
        //准备request
        SearchRequest request = new SearchRequest("hotel");
        //准备dsl
        request.source().suggest(new SuggestBuilder().addSuggestion("suggestions",
                SuggestBuilders.completionSuggestion("suggestion")
                        .prefix(prefix)
                        .skipDuplicates(true)
                        .size(10)));
        try {
            SearchResponse response = client.search(request, RequestOptions.DEFAULT);
            Suggest suggest = response.getSuggest();
            CompletionSuggestion suggestions = suggest.getSuggestion("suggestions");
            List<CompletionSuggestion.Entry.Option> options = suggestions.getOptions();
            if (CollectionUtil.isEmpty(options)) {
                return Collections.emptyList();
            }
            for (CompletionSuggestion.Entry.Option option : options) {
                result.add(option.getText().toString());
            }
            return result;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void deleteByIdFromEs(Long id) {
        try {
            DeleteRequest request = new DeleteRequest("hotel").id(String.valueOf(id));
            DeleteResponse delete = client.delete(request, RequestOptions.DEFAULT);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void insertByIdIntoEs(Long id) {
        try {
            //根据id查询hotel数据
            Hotel hotel = getById(id);
            HotelDoc hotelDoc = new HotelDoc(hotel);
            //准备request
            IndexRequest request = new IndexRequest("hotel").id(hotel.getId().toString());
            //准备DSL
            request.source(JSONUtil.toJsonStr(hotelDoc), XContentType.JSON);
            //发送请求
            IndexResponse response = client.index(request, RequestOptions.DEFAULT);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    private static BoolQueryBuilder getBoolQueryBuilder(RequestParams params) {
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();

        //全文检索,关键字搜索
        String key = params.getKey();
        if (StringUtils.isEmpty(key)) {
            boolQuery.must(QueryBuilders.matchAllQuery());
        } else {
            boolQuery.must(QueryBuilders.matchQuery("all", key));
        }
        //条件过滤
        //城市条件
        if (!StringUtils.isEmpty(params.getCity())) {
            boolQuery.filter(QueryBuilders.termQuery("city", params.getCity()));
        }
        //品牌条件
        if (!StringUtils.isEmpty(params.getBrand())) {
            boolQuery.filter(QueryBuilders.termQuery("brand", params.getBrand()));
        }
        //星级判断
        if (!StringUtils.isEmpty(params.getStartName())) {
            boolQuery.filter(QueryBuilders.termQuery("startName", params.getStartName()));
        }
        //价格,是rangeQuery
        if (params.getMaxPrice() != null && params.getMinPrice() != null) {
            boolQuery.filter(QueryBuilders.rangeQuery("price").lt(params.getMaxPrice()).gt(params.getMinPrice()));
        }
        return boolQuery;
    }
}
