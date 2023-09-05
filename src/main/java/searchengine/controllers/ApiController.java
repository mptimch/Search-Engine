package searchengine.controllers;


import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.MappingJacksonValue;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.statistics.PartialView;
import searchengine.dto.statistics.IndexingResponse;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.IndexingService;
import searchengine.services.StatisticsService;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Setter

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class ApiController {

    private final StatisticsService statisticsService;
    private final IndexingService indexingService;

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    public static boolean isIndexing; // индикатор, идет ли сейчас индексация
    private IndexingResponse response = new IndexingResponse();


    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }


    @GetMapping("/startIndexing")
    public ResponseEntity<MappingJacksonValue> startIndexing() {
        clearResponse();

        // проверка, идет ли индексация
        if (isIndexing) {
            response.setError("Индексация уже запущена");
            MappingJacksonValue jacksonValue = new MappingJacksonValue(response);
            return ResponseEntity.ok(jacksonValue);
        }

        isIndexing = true;

        // отдельно запускаем индексацию страниц
        executorService.execute(() -> {
            response.setResult(true);
            indexingService.startIndexing();
            isIndexing = false;
        });

        MappingJacksonValue jacksonValue = new MappingJacksonValue(response);

        // если есть текст ошибки - то выводим часть сообщения, если нет - то целиком
        if (response.getError() == null) {
            jacksonValue.setSerializationView(PartialView.class);
        }
        return ResponseEntity.ok(jacksonValue);
    }


    @GetMapping("/stopIndexing")
    public ResponseEntity<MappingJacksonValue> stopIndexing() {
        clearResponse();

        if (!isIndexing) {
            response.setError("Индексация не запущена");
            MappingJacksonValue jacksonValue = new MappingJacksonValue(response);
            return ResponseEntity.ok(jacksonValue);
        }

        indexingService.stopIndexing();
        isIndexing = false;
        MappingJacksonValue jacksonValue = new MappingJacksonValue(response);

        if (response.getError() == null) {
            response.setResult(true);
            jacksonValue.setSerializationView(PartialView.class);
        }
        return ResponseEntity.ok(jacksonValue);
    }


    @PostMapping("/indexPage")
    public ResponseEntity indexPage(@RequestParam String url) {

        clearResponse();
        response = indexingService.indexingPage(url);

        MappingJacksonValue jacksonValue = new MappingJacksonValue(response);

        if (response.getError() == null) {
            response.setResult(true);
            jacksonValue.setSerializationView(PartialView.class);
        }

        return ResponseEntity.ok(jacksonValue);
    }


    private void clearResponse() {
        response.setResult(false);
        response.setError(null);
    }
}
