const params = new URLSearchParams(window.location.search);
document.getElementById('server').textContent = params.get('server') || '';
if (params.get('state') === 'error') {
  document.getElementById('title').textContent = '로컬 서버에 연결하지 못했어요.';
  document.getElementById('message').textContent = '서버가 꺼져 있거나 응답이 지연되었거나 오류를 반환했습니다.';
  document.getElementById('help').hidden = false;
}
document.getElementById('retry').addEventListener('click', (event) => {
  event.currentTarget.disabled = true;
  window.onfilmDesktop.retry();
});
