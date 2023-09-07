package searchengine.controllers;


import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.MappingJacksonValue;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.statistics.*;
import searchengine.services.IndexingService;
import searchengine.services.SearchService;
import searchengine.services.StatisticsService;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Setter

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class ApiController {

    private final StatisticsService statisticsService;
    private final IndexingService indexingService;
    private final SearchService searchService;

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    public static boolean isIndexing; // индикатор, идет ли сейчас индексация
    private IndexingResponse indexingResponse = new IndexingResponse();
    private SearchResponse searchResponse = new SearchResponse();


    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }


    @GetMapping("/startIndexing")
    public ResponseEntity<MappingJacksonValue> startIndexing() {
        clearIndexingResponse();

        // проверка, идет ли индексация
        if (isIndexing) {
            indexingResponse.setError("Индексация уже запущена");
            MappingJacksonValue jacksonValue = new MappingJacksonValue(indexingResponse);
            return ResponseEntity.ok(jacksonValue);
        }

        isIndexing = true;

        // отдельно запускаем индексацию страниц
        executorService.execute(() -> {
            indexingResponse.setResult(true);
            indexingService.startIndexing();
            isIndexing = false;
        });

        MappingJacksonValue jacksonValue = new MappingJacksonValue(indexingResponse);

        // если есть текст ошибки - то выводим часть сообщения, если нет - то целиком
        if (indexingResponse.getError() == null) {
            jacksonValue.setSerializationView(PartialView.class);
        }
        return ResponseEntity.ok(jacksonValue);
    }


    @GetMapping("/stopIndexing")
    public ResponseEntity<MappingJacksonValue> stopIndexing() {
        clearIndexingResponse();

        // проверяем, идет ли индексация
        if (!isIndexing) {
            indexingResponse.setError("Индексация не запущена");
            MappingJacksonValue jacksonValue = new MappingJacksonValue(indexingResponse);
            return ResponseEntity.ok(jacksonValue);
        }

        // останавливаем индексацию
        indexingService.stopIndexing();
        isIndexing = false;
        MappingJacksonValue jacksonValue = new MappingJacksonValue(indexingResponse);

        if (indexingResponse.getError() == null) {
            indexingResponse.setResult(true);
            jacksonValue.setSerializationView(PartialView.class);
        }
        return ResponseEntity.ok(jacksonValue);
    }


    @PostMapping("/indexPage")
    public ResponseEntity<MappingJacksonValue> indexPage(@RequestParam String url) {
        clearIndexingResponse();
        indexingResponse = indexingService.indexingPage(url);

        MappingJacksonValue jacksonValue = new MappingJacksonValue(indexingResponse);

        if (indexingResponse.getError() == null) {
            indexingResponse.setResult(true);
            jacksonValue.setSerializationView(PartialView.class);
        }

        return ResponseEntity.ok(jacksonValue);
    }


    @GetMapping("search")
    public ResponseEntity<MappingJacksonValue> search(
            @RequestParam(name = "query") Optional<String> queryParam,
            @RequestParam(name = "site", required = false) String site,
            @RequestParam(name = "offset", required = false) Integer offset,
            @RequestParam(name = "limit", required = false) Integer limit
    ) {
        // проверяем, не пустой ли поисковый запрос
        String query = queryParam.orElse(null);
        if (query == null || query.trim().isEmpty()) {
            MappingJacksonValue jacksonValue = searchService.createResponseForNullQuery(searchResponse);
            return ResponseEntity.ok(jacksonValue);
        }

        // формируем ответ на поисковый запрос
        searchResponse = searchService.search(query, site, offset, limit);
        MappingJacksonValue jacksonValue = new MappingJacksonValue(searchResponse);

        if (searchResponse.getError() != null) {
            searchResponse.setResult(false);
            jacksonValue.setSerializationView(PartialView.class);
        } else jacksonValue.setSerializationView(FullView.class);

        return ResponseEntity.ok(jacksonValue);
    }


    private void clearIndexingResponse() {
        indexingResponse.setResult(false);
        indexingResponse.setError(null);
    }
}
