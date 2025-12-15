package com.onfilm.domain.genre.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GenreService {

    /**
     * 장르 추가
     * 장르 수정
     * 장르 삭제
     * 장르 목록 조회
     * 영화 검색 시 장르 기반 필터링
     */

}
