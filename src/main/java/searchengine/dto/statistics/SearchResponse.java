package searchengine.dto.statistics;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonView;
import lombok.Data;

import java.util.List;

@Data
public class SearchResponse {

    @JsonView({PartialView.class, FullView.class})
    Boolean result;

    @JsonView (PartialView.class)
    String error;

    @JsonView(FullView.class)
    Integer count;

    @JsonView(FullView.class)
    List<PagesToResponse> data;

}
