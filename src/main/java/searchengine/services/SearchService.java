package searchengine.services;

import org.springframework.http.converter.json.MappingJacksonValue;
import searchengine.dto.statistics.SearchResponse;

public interface SearchService {

    SearchResponse search(String query,
                          String site,
                          Integer offset,
                          Integer limit);

    MappingJacksonValue createResponseForNullQuery(SearchResponse response);
}
