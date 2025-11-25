package com.onfilm.domain.search.document;


import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.elasticsearch.annotations.Document;

import java.util.List;

@Getter @Setter
@NoArgsConstructor
//@Document(indexName = "movies_autocomplete")
@Document(indexName = "movies_kor")
public class MovieDocument {

    @Id
    private String id;
    private String title;
    private List<String> actors;

    // 생성자, Getter/Setter

    public MovieDocument(String id, String title, List<String> actors) {
        this.id = id;
        this.title = title;
        this.actors = actors;
    }

}
