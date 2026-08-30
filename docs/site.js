const checksum = document.querySelector('#checksum');
const copy = document.querySelector('#copy-checksum');

const apkChecksum = 'cbd7aa5c53f2c919cbf725519d06ee1fdfba013cf2723a55eb0c947944dca0ba';

if (apkChecksum) {
  checksum.textContent = apkChecksum;
  copy.disabled = false;
  copy.addEventListener('click', async () => {
    await navigator.clipboard.writeText(apkChecksum);
    copy.textContent = 'Copied';
  });
}
