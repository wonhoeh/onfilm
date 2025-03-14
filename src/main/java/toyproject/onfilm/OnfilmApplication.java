package toyproject.onfilm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

//@EnableMongoRepositories(basePackages = "toyproject.onfilm.like.repository", mongoTemplateRef = "mongoTemplate")
//@EnableMongoRepositories
@SpringBootApplication
public class OnfilmApplication {

	public static void main(String[] args) {
		SpringApplication.run(OnfilmApplication.class, args);
	}

}
