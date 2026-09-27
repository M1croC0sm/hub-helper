const checksum = document.querySelector('#checksum');
const copy = document.querySelector('#copy-checksum');

copy?.addEventListener('click', async () => {
  try {
    await navigator.clipboard.writeText(checksum.textContent.trim());
    copy.textContent = 'Copied';
  } catch {
    copy.textContent = 'Select the checksum to copy';
  }
});
