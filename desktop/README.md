# OnFilm Desktop — 첫 실행 프로토타입

기존 OnFilm 웹 화면을 Electron 전용 창에서 사용한다. API·DB·Kafka·Worker를 자동 실행하거나 설치하지는 않는다. `local` 프로필 및 Windows 설치 파일은 후속 단계다.

## 실행

개발용 Node.js는 22.12 이상이 필요하다(LTS 사용 권장). 최종 사용자가 Node.js를 설치하는 방식은 아니며, 지금은 개발 실행 단계다.

1. 기존 개발 절차로 DB·Kafka와 API를 실행한다. 영상 처리까지 확인하려면 Worker·FFmpeg도 준비한다.
2. 이 디렉터리에서 실행한다.

```sh
npm ci
npm start
```

기본 주소는 `http://localhost:8080/`이다. 다른 loopback 포트를 쓸 때:

```sh
# macOS
ONFILM_SERVER_URL=http://localhost:9090 npm start
```

```powershell
# Windows PowerShell
$env:ONFILM_SERVER_URL = 'http://localhost:9090'
npm start
```

주소에는 경로·쿼리·계정 정보를 넣을 수 없다. 원격 서버 주소도 거부한다. 이 제한은 대상 주소 검증이며 서버 자체를 loopback에 바인딩하는 설정을 대신하지 않는다.

서버가 없거나 시작 시 15초 내에 연결하지 못하면 앱 안에 오류 안내와 **다시 연결** 버튼이 나타난다. 시작한 다음 서버를 켜고 버튼을 누를 수 있다. HTTP 페이지 오류도 안내한다. 화면 내부 API 요청 실패는 현재 웹 화면의 기존 처리에 맡긴다.

앱은 서버를 종료하지 않는다. macOS에서는 창을 닫아도 앱은 남으며 Dock에서 다시 열거나 Cmd+Q로 종료할 수 있다. Windows에서는 마지막 창을 닫으면 종료한다. Electron 전용 세션의 로그인 쿠키는 일반 브라우저와 공유하지 않는다.

## 보안·현재 제한

- sandbox, context isolation, web security 활성화. 페이지의 Node.js 접근 금지.
- 파일 기반 안내 화면에만 인자 없는 재시도 브리지 제공. 메인 프로세스에서 발신 창·최상위 프레임·주소 재확인.
- 동일 서버 출처의 페이지 이동만 허용. 외부 링크와 외부 새 창은 현재 차단한다. SNS 링크 등 외부 브라우저 연결은 후속 작업이다.
- 같은 출처의 새 창 요청은 현재 창에서 연다. 팝업에 의존하는 기능은 별도 확인이 필요하다.
- 카메라·마이크 등 권한 요청 거부. 일반 파일 선택은 기존 HTML 파일 입력을 이용한다.
- 기존 웹 화면의 외부 CDN 의존성은 아직 제거하지 않았다. 지금은 오프라인 완성 버전이 아니다.
- Electron은 Java·DB·Kafka·FFmpeg 설치 문제를 해결하지 않는다.
- 최종 Windows 패키지·자동 서버 실행·게시 기능·CPU 사용 제한은 미구현이다.

보안 설정 기준: [Electron security checklist](https://www.electronjs.org/docs/latest/tutorial/security).

## 검증

```sh
npm test
npm run test:smoke
```

`test`는 주소 제한 정책을 검증한다. `test:smoke`는 실제 Electron과 임시 HTTP 서버로 연결 실패→재시도→성공, 렌더러 격리, 외부 이동 차단, HTTP 오류, 연결 제한 시간을 검증한다. 테스트는 임시 사용자 데이터 폴더와 독립 세션을 사용하고 실제 OnFilm DB에 접근하지 않는다. GUI 사용 가능한 환경이 필요하며 임시 폴더 경로를 출력한다.

실제 서비스 인수 체크리스트:

- [ ] macOS에서 전용 창 표시·기존 로그인.
- [ ] 프로필 편집·저장·재조회.
- [ ] 파일 선택·사진 업로드.
- [ ] 영상 인코딩·HLS 재생·구간 이동.
- [ ] 종료·재실행·중복 실행 확인.
- [ ] Windows에서 위 항목 재검증.

위 항목은 테스트용 서버 성공과 별개다. [전체 계획](../docs/plans/local-desktop-development-plan.md)에서 진행 현황을 관리한다.
