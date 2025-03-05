package toyproject.onfilm.genre.entity;

import jakarta.persistence.Id;
import lombok.Builder;
import lombok.Getter;
import org.springframework.data.mongodb.core.mapping.Document;


@Getter
@Document(collection = "genres")
public class Genre {
    @Id
    private String id;
    private String name;    //장르 이름

    @Builder
    public Genre(String name) {
        this.name = name;
    }
}
