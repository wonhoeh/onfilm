package toyproject.onfilm.elasticsearch;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.List;

public interface MovieSearchRepository extends ElasticsearchRepository<MovieDocument, String> {
    List<MovieDocument> findByTitleContaining(String keyword);
    List<MovieDocument> findByActorsContaining(String actorName);
}
