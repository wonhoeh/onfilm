const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { apiConfig } = require('../src/api-config.cjs');

test('explicit config preserves media root and forces local, loopback, shell-free java args', (t) => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'onfilm-config-'));
  t.after(() => fs.rmSync(dir, { recursive: true, force: true }));
  const jar = path.join(dir, '앱 파일.jar');
  fs.writeFileSync(jar, 'fixture');
  const env = { DB_PASSWORD: 'test-only', JWT_SECRET: 'a'.repeat(32), MEDIA_ENCODE_CALLBACK_SECRET: 'test-only',
    LOCAL_STORAGE_ROOT: dir, ONFILM_API_JAR: jar, ONFILM_API_PORT: '18080' };
  const config = apiConfig(env, dir);
  assert.equal(config.origin, 'http://127.0.0.1:18080');
  assert.equal(config.env.LOCAL_STORAGE_ROOT, dir);
  assert.equal(config.args[1], jar);
  assert.ok(config.args.includes('--spring.profiles.active=local'));
  assert.ok(!config.args.join(' ').includes(env.DB_PASSWORD));
  assert.notEqual(config.env.ONFILM_INSTANCE_ID, apiConfig(env, dir).env.ONFILM_INSTANCE_ID);
  for (const name of ['DB_PASSWORD', 'JWT_SECRET', 'MEDIA_ENCODE_CALLBACK_SECRET', 'LOCAL_STORAGE_ROOT']) {
    assert.throws(() => apiConfig({ ...env, [name]: '' }, dir), new RegExp(name));
  }
  assert.throws(() => apiConfig({ ...env, ONFILM_API_PORT: '0' }, dir), /PORT/);
  assert.throws(() => apiConfig({ ...env, LOCAL_STORAGE_ROOT: './relative' }, dir), /절대 경로/);
  assert.throws(() => apiConfig({ ...env, ONFILM_API_JAR: 'missing.jar' }, dir), /JAR/);
});
