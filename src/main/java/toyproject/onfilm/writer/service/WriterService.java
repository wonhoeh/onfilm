package toyproject.onfilm.writer.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import toyproject.onfilm.writer.dto.CreateWriterRequest;
import toyproject.onfilm.writer.entity.Writer;
import toyproject.onfilm.writer.repository.WriterRepository;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class WriterService {

    private final WriterRepository writerRepository;

    public Long createWriter(CreateWriterRequest request) {
        Writer writer = Writer.builder()
                .name(request.getName())
                .age(request.getAge())
                .sns(request.getSns())
                .build();

        Writer savedWriter = writerRepository.save(writer);

        return savedWriter.getId();
    }
}
