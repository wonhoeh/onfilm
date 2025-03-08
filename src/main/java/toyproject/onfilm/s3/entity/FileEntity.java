package toyproject.onfilm.s3.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 나중에 사용 예정
 * - 해상도별 파일을 유지
 * - 업로드 시간, 파일 크기, 다운로드 횟수 등을 저장
 */

@Entity
@Getter
@NoArgsConstructor
public class FileEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String fileName;
    private String fileUrl;
    private String fileType; // IMAGE, TRAILER, MOVIE

    @Builder
    public FileEntity(String fileName, String fileUrl, String fileType) {
        this.fileName = fileName;
        this.fileUrl = fileUrl;
        this.fileType = fileType;
    }
}
