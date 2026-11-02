import hashlib
import importlib.util
import io
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec=importlib.util.spec_from_file_location('downloader',Path(__file__).parents[1]/'scripts/download-root-files.py')
downloader=importlib.util.module_from_spec(spec)
spec.loader.exec_module(downloader)

class Response(io.BytesIO):
    headers={'Content-Type':'application/octet-stream'}
    def geturl(self): return 'https://xdaforums.com/attachments/example/'

class DownloaderTest(unittest.TestCase):
    def test_only_complete_verified_file_receives_final_name(self):
        data=b'original verified download'
        with tempfile.TemporaryDirectory() as directory:
            target=Path(directory)/'file.zip'
            with patch.object(downloader,'urlopen',return_value=Response(data)):
                downloader.download('https://xdaforums.com/file',target,hashlib.sha256(data).hexdigest(),100)
            self.assertEqual(target.read_bytes(),data)
            self.assertEqual(list(Path(directory).iterdir()),[target])

    def test_wrong_digest_and_size_limit_leave_no_partial_files(self):
        for digest,limit in [('0'*64,100),(hashlib.sha256(b'payload').hexdigest(),2)]:
            with self.subTest(limit=limit),tempfile.TemporaryDirectory() as directory:
                target=Path(directory)/'file.zip'
                with patch.object(downloader,'urlopen',return_value=Response(b'payload')):
                    with self.assertRaises(ValueError):downloader.download('https://xdaforums.com/file',target,digest,limit)
                self.assertEqual(list(Path(directory).iterdir()),[])

    def test_existing_destination_is_not_overwritten(self):
        with tempfile.TemporaryDirectory() as directory:
            target=Path(directory)/'file.zip';target.write_bytes(b'keep')
            with patch.object(downloader,'urlopen',return_value=Response(b'payload')):
                with self.assertRaises(FileExistsError):
                    downloader.download('https://xdaforums.com/file',target,hashlib.sha256(b'payload').hexdigest(),100)
            self.assertEqual(target.read_bytes(),b'keep')
            self.assertEqual(list(Path(directory).iterdir()),[target])

    def test_webpage_is_not_accepted_as_a_package(self):
        response=Response(b'login page');response.headers={'Content-Type':'text/html'}
        with tempfile.TemporaryDirectory() as directory,patch.object(downloader,'urlopen',return_value=response):
            with self.assertRaises(ValueError):downloader.download('https://xdaforums.com/file',Path(directory)/'file.zip','0'*64,100)
            self.assertEqual(list(Path(directory).iterdir()),[])

    def test_untrusted_or_local_urls_are_rejected(self):
        for url in ('file:///etc/passwd','http://xdaforums.com/file','https://example.com/file','https://user:secret@xdaforums.com/file'):
            with self.subTest(url=url),self.assertRaises(ValueError):downloader.validate_url(url,downloader.UNLOCK)

if __name__=='__main__': unittest.main()
