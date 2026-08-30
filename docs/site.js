const download = document.querySelector('#drive-download');
const checksum = document.querySelector('#checksum');
const copy = document.querySelector('#copy-checksum');

const driveUrl = 'https://drive.google.com/file/d/1gvog7Q0RZFW7Qn-9jbNiZDpJMavgq7XH/view?usp=drive_link';
const apkChecksum = 'a50973decb20f04d29267bbd11915f2f1e79d83c98171d7dda9e7d0ddb782b2b';

if (driveUrl) {
  download.href = driveUrl;
  download.textContent = 'Download testing build';
  download.classList.remove('disabled');
  download.removeAttribute('aria-disabled');
}

if (apkChecksum) {
  checksum.textContent = apkChecksum;
  copy.disabled = false;
  copy.addEventListener('click', async () => {
    await navigator.clipboard.writeText(apkChecksum);
    copy.textContent = 'Copied';
  });
}
