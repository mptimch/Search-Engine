package searchengine.dto.statistics;

import com.fasterxml.jackson.annotation.JsonView;
import lombok.Data;

@Data
public class IndexingResponse {

    @JsonView(PartialView.class)
    boolean result;

    String error;
}
