package toyproject.onfilm.config;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@Profile("prod")
@Configuration
@EnableMongoRepositories(basePackages = {
        "toyproject.onfilm.like.repository",
        "toyproject.onfilm.genre.repository"
})
public class MongoConfig {

    @Bean
    public MongoTemplate mongoTemplate() {
        //MongoDB 연결 문자열을 사용하여 MongoClient를 생성합니다.
        MongoClient mongoClient = MongoClients.create("mongodb://onfilm.p-e.kr:27017");
        return new MongoTemplate(mongoClient, "admin");
    }
}
