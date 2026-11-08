# Uploads the release bundle to Google Play through its publishing API, with its release notes.
# The service account's key file is named by playKey in keystore.properties, kept outside git like the upload key.
# By default the release is left as a draft on the track, to be checked and sent from Play Console; --publish sends it.
# Usage: python tools/play_upload.py --track internal|alpha|production --notes docs/release-notes.txt [--publish]
#   notes file: one block per language, as Play Console takes them: <ar>…</ar> <en-US>…</en-US>
import argparse, os, re
from google.oauth2 import service_account
from googleapiclient.discovery import build
from googleapiclient.http import MediaFileUpload

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
PACKAGE = 'com.readqurantoday.quran'
BUNDLE = os.path.join(ROOT, 'app', 'build', 'outputs', 'bundle', 'release', 'app-release.aab')

ask = argparse.ArgumentParser()
ask.add_argument('--track', required=True, choices=['internal', 'alpha', 'beta', 'production'])
ask.add_argument('--notes', required=True)
ask.add_argument('--publish', action='store_true', help='send the release, not leave it a draft')
args = ask.parse_args()

props = dict(line.split('=', 1) for line in open(os.path.join(ROOT, 'keystore.properties'), encoding='utf-8')
             if '=' in line and not line.lstrip().startswith('#'))
key = props.get('playKey', '').strip()
if not key or not os.path.exists(key):
    raise SystemExit('playKey in keystore.properties must name the Play service account key file')

notes = [{'language': lang, 'text': text.strip()}
         for lang, text in re.findall(r'<([\w-]+)>(.*?)</\1>', open(args.notes, encoding='utf-8').read(), re.S)]
if not notes:
    raise SystemExit('no <language>…</language> blocks in ' + args.notes)

creds = service_account.Credentials.from_service_account_file(key, scopes=['https://www.googleapis.com/auth/androidpublisher'])
play = build('androidpublisher', 'v3', credentials=creds, cache_discovery=False)
edits = play.edits()

edit = edits.insert(packageName=PACKAGE, body={}).execute()['id']
print(f'uploading {os.path.getsize(BUNDLE) / 1e6:.0f} MB…')
media = MediaFileUpload(BUNDLE, mimetype='application/octet-stream', resumable=True, chunksize=8 * 1024 * 1024)
upload = edits.bundles().upload(packageName=PACKAGE, editId=edit, media_body=media)
done = None
while done is None:
    sent, done = upload.next_chunk()
    if sent:
        print(f'  {int(sent.progress() * 100)}%')
code = done['versionCode']
edits.tracks().update(packageName=PACKAGE, editId=edit, track=args.track, body={
    'track': args.track,
    'releases': [{'versionCodes': [str(code)], 'status': 'completed' if args.publish else 'draft', 'releaseNotes': notes}],
}).execute()
edits.commit(packageName=PACKAGE, editId=edit).execute()
print(f'version {code} on {args.track}, ' + ('sent' if args.publish else 'saved as a draft: check it in Play Console and send it'))
