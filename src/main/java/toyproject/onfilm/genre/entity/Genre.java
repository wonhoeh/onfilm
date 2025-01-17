package toyproject.onfilm.genre.entity;

import jakarta.persistence.Id;
import lombok.Getter;
import org.springframework.data.mongodb.core.mapping.Document;


@Getter
@Document(collection = "genres")
public class Genre {
    @Id
    private String id;
    private String name;    //장르 이름

    private void addGenreName(String name) {
        this.name = name;
    }

    public static Genre createGenre(String name) {
        Genre genre = new Genre();
        genre.addGenreName(name);
        return genre;
    }

    public Genre(String name) {
        this.name = name;
    }

    public Genre() {}
}
