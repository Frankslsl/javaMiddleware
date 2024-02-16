package cn.itcast.hotel;

import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.search.suggest.Suggest;
import org.elasticsearch.search.suggest.SuggestBuilder;
import org.elasticsearch.search.suggest.SuggestBuilders;
import org.elasticsearch.search.suggest.completion.CompletionSuggestion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.util.List;

/**
 * suggestion的例子
 */
@SpringBootTest
public class SuggestionTest {
    @Autowired
    private RestHighLevelClient client;

    @Test
    public void suggestionTest() throws IOException {
        SearchRequest request = new SearchRequest("hotel");
        //准备dsl
        request.source().suggest(new SuggestBuilder().addSuggestion(
                "suggestions", SuggestBuilders.completionSuggestion("suggestion")
                        .prefix("h")
                        .skipDuplicates(true)
                        .size(10)
        ));
        //发送请求
        SearchResponse response = client.search(request, RequestOptions.DEFAULT);
        System.out.println("response = " + response);
        //处理结果
        Suggest suggest = response.getSuggest();
        //获得suggestions名字的结果集
        CompletionSuggestion suggestions = suggest.getSuggestion("suggestions");
        List<CompletionSuggestion.Entry.Option> options = suggestions.getOptions();
        for (CompletionSuggestion.Entry.Option option : options) {
            String string = option.getText().toString();
        }
    }
}
