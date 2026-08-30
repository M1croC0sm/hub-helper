const checksum = document.querySelector('#checksum');
const copy = document.querySelector('#copy-checksum');

const apkChecksum = 'a50973decb20f04d29267bbd11915f2f1e79d83c98171d7dda9e7d0ddb782b2b';

if (apkChecksum) {
  checksum.textContent = apkChecksum;
  copy.disabled = false;
  copy.addEventListener('click', async () => {
    await navigator.clipboard.writeText(apkChecksum);
    copy.textContent = 'Copied';
  });
}
