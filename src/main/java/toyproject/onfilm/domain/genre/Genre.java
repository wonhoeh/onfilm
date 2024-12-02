package toyproject.onfilm.domain.genre;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import toyproject.onfilm.domain.moviegenre.MovieGenre;

import java.util.ArrayList;
import java.util.List;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
public class Genre {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "genre_id")
    private Long id;
    private String name;

    //=== 연관 관계 ===
    @OneToMany(mappedBy = "genre")
    private List<MovieGenre> movieGenres = new ArrayList<>();
}
