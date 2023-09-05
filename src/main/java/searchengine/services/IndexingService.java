package searchengine.services;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.MappingJacksonValue;
import searchengine.dto.statistics.IndexingResponse;

public interface IndexingService {

    void startIndexing ();

    void stopIndexing();

    IndexingResponse indexingPage (String url);

}
