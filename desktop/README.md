# OnFilm Desktop — 첫 실행 프로토타입

기존 OnFilm 웹 화면을 Electron 전용 창에서 사용한다. 기존 서버 연결 모드와 API 자동 실행 모드를 지원한다. DB·Kafka·Worker 자동 실행과 Windows 설치 파일은 아직 후속 단계다.

## API 자동 실행 (`start:local`)

현재는 개발용 자동화다. 기존 MySQL과 Kafka는 먼저 실행한다. 이 모드는 기존 DB를 그대로 사용하며 Flyway의 미적용 마이그레이션이 시작 시 적용될 수 있다. 기존 DB를 임의로 초기화하지 않는다.

1. 프로젝트 루트에서 `./gradlew bootJar` 실행(Windows는 `gradlew.bat bootJar`). Java 코드·설정 변경 시 다시 빌드한다.
2. `desktop/.env.example`을 `desktop/.env`로 복사해 값을 채운다. 이 파일은 Git에서 제외된다.
3. `DB_PASSWORD`는 MySQL의 API 계정 비밀번호로 지정한다. IntelliJ 환경변수는 Electron에 자동 전달되지 않는다.
4. `JWT_SECRET`은 32바이트 이상의 지속적인 비밀값, `MEDIA_ENCODE_CALLBACK_SECRET`은 Worker와 동일한 값으로 지정한다.
5. `LOCAL_STORAGE_ROOT`는 기존 DB가 참조하는 미디어의 실제 루트 폴더를 지정한다. `onfilm-media` 하위 폴더를 제외한 경로다. 새 빈 폴더로 바꾸면 기존 이미지가 보이지 않을 수 있다. 앱은 폴더를 임의로 이동·삭제하지 않는다.
6. IntelliJ에서 실행 중인 API와 기존 Electron 앱을 종료하고 `desktop/`에서 실행한다. macOS에서 기존 앱은 Cmd+Q로 완전히 종료한다(단일 인스턴스 잠금 때문에 모드 변경 시 필요).

```sh
npm run start:local
```

```text
Electron 시작 → 설정 검사 → 포트 충돌 확인 → Java JAR(local) 실행
→ 최대 90초 동안 실행 인스턴스 확인 → 준비되면 기존 화면 표시
```

Java 17 이상이 PATH에 없으면 `.env`의 `ONFILM_JAVA_PATH`에 `java`/`java.exe` 전체 경로를 지정한다. API JAR를 직접 실행하므로 Gradle 데몬을 API의 자식 프로세스로 만들지 않는다. 경로에 공백이 있어도 명령 문자열로 이어붙이지 않는다.

`start:local`은 `ONFILM_SERVER_URL` 대신 `ONFILM_API_PORT`(기본 8080)를 사용하며 `127.0.0.1`에만 바인딩한다. 인스턴스 ID는 비밀 토큰이 아니라 다른 서버와 구분하기 위한 값이다. 기존 `dev`와 달리 CSRF 검증을 유지하므로 API·화면 기능을 별도 확인해야 한다. `local,dev`를 함께 활성화하지 않는다.

포트가 사용 중이면 기존 프로세스를 종료하거나 그대로 접속하지 않고 오류를 안내한다. 시작 실패·제한 시간 초과 시 앱이 실행한 API만 정리한다. API가 비정상 종료되면 다시 연결로 재실행할 수 있다. `.env`를 수정했다면 앱을 재시작한다. 시스템 환경변수가 `.env`보다 우선한다.

자동 실행 모드에서는 마지막 창을 닫으면 macOS에서도 앱과 소유 API를 종료한다. MySQL·Kafka·Worker는 종료하지 않는다. 서버 로그는 실행한 터미널로 출력한다. 운영체제 강제 종료·전원 차단까지 정리 완료를 보장하지 않는다. Windows에서는 프로세스 종료 신호가 Unix처럼 graceful shutdown을 보장하지 않으므로 실제 Windows 종료 검증이 필요하다.

현재는 인코딩/게시 중 종료 확인, 전체 구성요소 자동화, 영구 로그 회전, 비밀값 자동 생성·OS 보안 저장, Java 번들링을 구현하지 않았다. 영상 처리 중에는 앱을 종료하지 않는다. Worker dev는 H2이므로 재시작 복구 검증은 아직 불가하다.

## 기존 서버 연결 (`start`)

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
- 최종 Windows 패키지·DB/Kafka/Worker 자동 실행·게시 기능·CPU 사용 제한은 미구현이다.

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
