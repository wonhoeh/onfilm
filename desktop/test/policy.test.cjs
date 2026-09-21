const { test } = require('node:test');
const assert = require('node:assert/strict');
const { serverOrigin, isAppUrl } = require('../src/policy.cjs');

test('defaults to existing local server and accepts explicit loopback origins', () => {
  assert.equal(serverOrigin(), 'http://localhost:8080');
  for (const url of ['http://127.0.0.1:9000', 'http://[::1]:8080', 'https://localhost:8443']) {
    assert.equal(serverOrigin(url), url);
  }
});

test('rejects remote hosts, credentials, paths, queries and unsafe schemes', () => {
  for (const url of ['https://example.com', 'http://localhost.evil.com', 'http://192.168.0.2',
    'http://user:pass@localhost', 'http://localhost/login', 'http://localhost/?x=1',
    'http://localhost/#x', 'file:///tmp/index.html', 'javascript:alert(1)', 'bad']) {
    assert.throws(() => serverOrigin(url), undefined, url);
  }
});

test('only same origin web navigation is accepted', () => {
  const origin = serverOrigin();
  assert.equal(isAppUrl(`${origin}/login.html?next=%2Fedit-profile`, origin), true);
  for (const url of ['http://localhost:8081/', 'https://localhost:8080/',
    'http://127.0.0.1:8080/', 'http://localhost:8080.evil.com/',
    'http://user@localhost:8080/', 'javascript:alert(1)', 'file:///etc/passwd', 'bad']) {
    assert.equal(isAppUrl(url, origin), false, url);
  }
});
