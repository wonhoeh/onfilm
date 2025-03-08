package toyproject.onfilm.s3.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import toyproject.onfilm.s3.entity.FileEntity;

/**
 * 나중에 사용 예정
 * - 해상도별 파일을 유지
 * - 업로드 시간, 파일 크기, 다운로드 횟수 등을 저장
 */

public interface FileRepository extends JpaRepository<FileEntity, Long> {
}
