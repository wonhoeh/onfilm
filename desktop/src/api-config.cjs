const path = require('node:path');
const fs = require('node:fs');
const { randomUUID } = require('node:crypto');

function apiConfig(env, repoRoot) {
  for (const name of ['DB_PASSWORD', 'JWT_SECRET', 'MEDIA_ENCODE_CALLBACK_SECRET', 'LOCAL_STORAGE_ROOT']) {
    if (!env[name]?.trim()) throw new Error(`desktop/.env 또는 환경변수에 ${name}을 설정해 주세요.`);
  }
  if (Buffer.byteLength(env.JWT_SECRET) < 32) throw new Error('JWT_SECRET은 32바이트 이상으로 설정해 주세요.');
  if (!path.isAbsolute(env.LOCAL_STORAGE_ROOT)) throw new Error('LOCAL_STORAGE_ROOT는 절대 경로로 설정해 주세요.');
  if (!fs.statSync(env.LOCAL_STORAGE_ROOT, { throwIfNoEntry: false })?.isDirectory()) {
    throw new Error('LOCAL_STORAGE_ROOT 폴더가 없습니다. 기존 미디어 폴더를 확인해 주세요.');
  }
  const port = Number(env.ONFILM_API_PORT || 8080);
  if (!Number.isInteger(port) || port < 1024 || port > 65535) throw new Error('ONFILM_API_PORT는 1024~65535 정수여야 합니다.');
  const jar = path.resolve(repoRoot, env.ONFILM_API_JAR || 'build/libs/onfilm-0.0.1-SNAPSHOT.jar');
  if (!fs.statSync(jar, { throwIfNoEntry: false })?.isFile()) {
    throw new Error('API JAR가 없습니다. 프로젝트 루트에서 ./gradlew bootJar를 먼저 실행해 주세요.');
  }
  return {
    origin: `http://127.0.0.1:${port}`,
    command: env.ONFILM_JAVA_PATH || 'java',
    args: ['-jar', jar, '--spring.profiles.active=local', '--server.address=127.0.0.1', `--server.port=${port}`],
    cwd: repoRoot,
    env: { ...env, ONFILM_API_PORT: String(port), ONFILM_INSTANCE_ID: randomUUID() },
  };
}

module.exports = { apiConfig };
