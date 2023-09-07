package searchengine.dto.statistics;

import com.fasterxml.jackson.annotation.JsonView;
import lombok.Data;

@Data
public class PagesToResponse {

    @JsonView(FullView.class)
    String site;

    @JsonView(FullView.class)
    String sitename;

    @JsonView(FullView.class)
    String uri;

    @JsonView(FullView.class)
    String title;

    @JsonView(FullView.class)
    String snippet;

    @JsonView(FullView.class)
    Double relevance;
}
