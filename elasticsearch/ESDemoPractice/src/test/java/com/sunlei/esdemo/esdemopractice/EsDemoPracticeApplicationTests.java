package com.sunlei.esdemo.esdemopractice;

import com.sunlei.esdemo.esdemopractice.config.MyIndexConstants;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.admin.indices.alias.Alias;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest;
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.support.ActiveShardCount;
import org.elasticsearch.action.support.master.AcknowledgedResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.XContentType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;

@SpringBootTest
@Slf4j
class EsDemoPracticeApplicationTests {
    private final static String indexName= "my_index";
    @Autowired
    private RestHighLevelClient client;

    @Test
    void contextLoads() {
    }
    @Test
    public void createIndex() throws IOException {
        //创建request
        CreateIndexRequest request = new CreateIndexRequest(indexName);
        //设置request
        request.settings(Settings.builder()
                .put("number_of_shards","1")
                        .put("number_of_replicas","1")
                .build());
        request.alias(new Alias("default_index"));
        //可选的参数
        request.timeout(TimeValue.timeValueSeconds(5));//设置超时时间
        request.masterNodeTimeout(TimeValue.timeValueSeconds(5));//主节点超时设置
        request.waitForActiveShards(ActiveShardCount.from(1));//设置创建索引api返回响应之前等待活动分片的数量,默认是1, 可以设置all但是会阻塞
        //完善请求
        request.source(MyIndexConstants.MY_INDEX_JSON, XContentType.JSON);
        //发送请求
        CreateIndexResponse response = client.indices().create(request, RequestOptions.DEFAULT);
        //查看索引
        boolean acknowledged = response.isAcknowledged();
        log.info("index has been created ==> {}", acknowledged);
    }
    @Test
    public void deleteIndex() throws IOException {
        DeleteIndexRequest request = new DeleteIndexRequest(indexName);
        AcknowledgedResponse response = client.indices().delete(request, RequestOptions.DEFAULT);
        boolean acknowledged = response.isAcknowledged();
        log.info("deleting is done + {}", acknowledged);
    }
    @Test
    public void checkIndexExist() throws IOException {//查看索引是否存在
        GetIndexRequest request = new GetIndexRequest(indexName);
        //配置一些参数
        request.local(false);//从主节点返回本地索引状态
        request.humanReadable(true);//以适合人类的格式返回
        request.includeDefaults(false);//是否返回每个索引的所有默认配置
        boolean response = client.indices().exists(request, RequestOptions.DEFAULT);
        log.info("the index {} is existed ===> {}",indexName,response);
    }

}
