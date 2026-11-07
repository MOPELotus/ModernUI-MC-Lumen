#!/usr/bin/env python3
"""Build-plan validation and standard-only CurseForge uploads (Python 3.11+)."""
import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tomllib
import urllib.error
import urllib.request
import uuid
import zipfile

ROOT = Path(__file__).resolve().parents[1]
# One import-safe validator is also used by the standalone bundle check.
_BUNDLE_SPEC = importlib.util.spec_from_file_location(
    'lumen_mixinextras_bundle', Path(__file__).with_name('check-mixinextras-bundle.py'))
_BUNDLE_CHECK = importlib.util.module_from_spec(_BUNDLE_SPEC)
_BUNDLE_SPEC.loader.exec_module(_BUNDLE_CHECK)

API = 'https://minecraft.curseforge.com/api'
LOADERS = {'fabric': 'Fabric', 'neoforge': 'NeoForge'}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def load_plan(path):
    plan = json.loads(Path(path).read_text())
    require(plan['schema'] == 1, 'unsupported plan schema')
    require(re.fullmatch(r'\d+\.\d+\.\d+\.\d+', plan['version']), 'invalid mod version')
    require(plan['release_type'] in ('release', 'beta', 'alpha'), 'invalid release type')
    require(plan['upstream_project'] == 352491, 'upstream relation must identify Modern UI')
    require(plan['changelog'] == f'release/changelogs/{plan["version"]}.md',
            'changelog must match the release version')
    targets = plan['targets']
    require(len(targets) == 8, 'expected all eight maintained branches')
    require(len({t['branch'] for t in targets}) == 8, 'duplicate branch')
    require(len({t['minecraft'] for t in targets}) == 8, 'duplicate build version')
    for t in targets:
        require(re.fullmatch(r'[0-9a-f]{40}', t['ref']), 'source ref must be a full commit SHA')
        require(t['java'] == (21 if t['minecraft'].startswith('1.21') else 25), 'wrong Java version')
        require(t['minecraft'] in t['game_versions'], 'build Minecraft version not tagged')
        require(len(t['game_versions']) == len(set(t['game_versions'])), 'duplicate game version')
        require(all(re.fullmatch(r'\d+\.\d+(?:\.\d+)?', v) for v in t['game_versions']),
                'invalid Minecraft version label')
        require('forgeconfigapiport' not in t['metadata']['fabric']['depends'],
                'Forge Config API Port is forbidden in maintained Fabric releases')
        require(set(t['metadata']['fabric']['depends']) <=
                {'fabricloader', 'minecraft', 'java', 'fabric-api', 'mixinextras'},
                'new required dependency needs an explicit CurseForge relation mapping')
        require(t['metadata']['fabric']['depends'].get('mixinextras') == '>=0.5.5',
                'Fabric must declare the bundled MixinExtras requirement')
        for version in t['game_versions']:
            for loader in LOADERS:
                require(supports(t['metadata'][loader]['minecraft'], version),
                        f'unsupported Minecraft tag {version}: {t["branch"]}/{loader}')
    return plan


def supports(expression, version):
    def number(value):
        parts = tuple(map(int, value.split('.')))
        return parts + (0,) * (3 - len(parts))
    value = number(version)
    if expression.startswith('~'):
        base = expression[1:]
        parts = base.split('.')
        upper = number('.'.join(parts[:-1] + [str(int(parts[-1]) + 1)]))
        return number(base) <= value < upper
    match = re.fullmatch(r'\[([\d.]+),\s*([\d.]+)\)', expression)
    if match:
        return number(match[1]) <= value < number(match[2])
    match = re.fullmatch(r'>=([\d.]+) <([\d.]+)', expression)
    require(match is not None, f'unsupported version expression: {expression}')
    return number(match[1]) <= value < number(match[2])


def filenames(plan, target):
    return {loader: f"ModernUI-Lumen-{label}-{target['minecraft']}-{plan['version']}-universal.jar"
            for loader, label in LOADERS.items()}


def validate_jar(path, plan, target, loader):
    require(path.name == filenames(plan, target)[loader], f'unexpected filename: {path.name}')
    with zipfile.ZipFile(path) as jar:
        require(jar.testzip() is None, f'corrupt JAR: {path.name}')
        entries = set(jar.namelist())
        require(not any('misans' in p.lower() for p in entries), f'MiSans payload: {path.name}')
        manifest = jar.read('META-INF/MANIFEST.MF').decode().replace('\r\n ', '').replace('\n ', '')
        fields = dict(line.split(': ', 1) for line in manifest.splitlines() if ': ' in line)
        require(fields.get('ModernUI-Font-Variant') == 'standard', f'not standard: {path.name}')
        require(fields.get('Implementation-Title') == f'ModernUI-Lumen-{LOADERS[loader]}',
                f'wrong implementation title: {path.name}')
        if loader == 'fabric':
            meta = json.loads(jar.read('fabric.mod.json'))
            _BUNDLE_CHECK.validate_fabric_bundle(jar, meta)
            ident, name, version = meta['id'], meta['name'], meta['version']
            minecraft = meta['depends']['minecraft']
            java = meta['depends']['java']
            require('forgeconfigapiport' not in meta['depends'], f'Forge Config API Port dependency: {path.name}')
            require('icyllis/modernui/mc/fabric/SimpleConfigSpec.class' in entries,
                    f'missing independent configuration implementation: {path.name}')
            forbidden = (b'fuzs/forgeconfigapiport/', b'net/neoforged/fml/config/',
                         b'net/minecraftforge/fml/config/', b'com/electronwill/nightconfig/')
            for entry in entries:
                if entry.endswith('.class'):
                    require(not entry.startswith(tuple(p.decode() for p in forbidden)),
                            f'external configuration class: {path.name}: {entry}')
                    if entry.startswith('icyllis/modernui/'):
                        bytecode = jar.read(entry)
                        require(not any(p in bytecode for p in forbidden),
                                f'external configuration reference: {path.name}: {entry}')
        else:
            document = tomllib.loads(jar.read('META-INF/neoforge.mods.toml').decode())
            meta = document['mods'][0]
            ident, name, version = meta['modId'], meta['displayName'], meta['version']
            minecraft = next(d['versionRange'] for d in document['dependencies']['modernui']
                             if d['modId'] == 'minecraft')
            java = None
        require((ident, name, version) == ('modernui', 'ModernUI Lumen', plan['version']),
                f'wrong mod identity/version: {path.name}')
        # Compare the actual supported range with the pinned source, not a guessed range.
        expected = target['metadata'][loader]
        require(minecraft == expected['minecraft'], f'wrong Minecraft range: {path.name}')
        if java is not None:
            require(java == expected['java'], f'wrong Java requirement: {path.name}')
            require(meta['depends'] == expected['depends'], f'wrong dependencies: {path.name}')
        for cls in ('MiSansInstaller', 'MiSansSetup'):
            require(f'icyllis/modernui/mc/{cls}.class' not in entries, f'installer present: {path.name}')
    return {'file': path.name, 'bytes': path.stat().st_size,
            'sha256': sha256(path),
            'branch': target['branch'], 'source_ref': target['ref'], 'loader': loader,
            'game_versions': target['game_versions']}


def sha256(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def validate_bundle(directory, plan, branch=None):
    directory = Path(directory)
    targets = [t for t in plan['targets'] if branch is None or t['branch'] == branch]
    require(targets, 'unknown branch')
    expected = {name for t in targets for name in filenames(plan, t).values()}
    actual = {p.relative_to(directory).as_posix() for p in directory.rglob('*.jar')}
    require(actual == expected, f'JAR set mismatch: missing={sorted(expected-actual)}, unexpected={sorted(actual-expected)}')
    require(all((directory / name).is_file() and not (directory / name).is_symlink() for name in expected),
            'JARs must be regular files directly inside the bundle')
    return [validate_jar(directory / name, plan, target, loader)
            for target in targets for loader, name in filenames(plan, target).items()]


def verify_source(directory, plan, branch):
    target = next(t for t in plan['targets'] if t['branch'] == branch)
    directory = Path(directory)
    head = subprocess.check_output(['git', '-C', str(directory), 'rev-parse', 'HEAD'], text=True).strip()
    require(head == target['ref'], 'checkout does not match pinned source')
    properties = dict(line.split('=', 1) for line in (directory / 'gradle.properties').read_text().splitlines()
                      if '=' in line and not line.lstrip().startswith('#'))
    require(properties['mod_version'] == plan['version'], 'source mod version mismatch')
    require(properties['minecraft_version'] == target['minecraft'], 'source Minecraft version mismatch')
    fabric = json.loads((directory / 'fabric/src/main/resources/fabric.mod.json').read_text())
    neo = tomllib.loads((directory / 'neoforge/src/main/resources/META-INF/neoforge.mods.toml').read_text())
    mc_range = next(d['versionRange'] for d in neo['dependencies']['modernui'] if d['modId'] == 'minecraft')
    require(target['metadata'] == {'fabric': {'minecraft': fabric['depends']['minecraft'],
                                            'java': fabric['depends']['java'], 'depends': fabric['depends']},
                                   'neoforge': {'minecraft': mc_range}}, 'source metadata differs from plan')


def api_json(path, token, data=None, content_type=None):
    headers = {'X-Api-Token': token, 'User-Agent': 'ModernUI-Lumen-release/1'}
    if content_type:
        headers['Content-Type'] = content_type
    request = urllib.request.Request(API + path, data=data, headers=headers)
    # Never follow redirects with an authentication header or print API bodies.
    class NoRedirect(urllib.request.HTTPRedirectHandler):
        def redirect_request(self, *args, **kwargs):
            return None
    try:
        with urllib.request.build_opener(NoRedirect).open(request, timeout=180) as response:
            return json.load(response)
    except urllib.error.HTTPError as error:
        # Retain only structured diagnostic fields, never headers, tokens or arbitrary HTML.
        detail = ''
        try:
            body = json.loads(error.read(65536))
            if isinstance(body, dict):
                fields = {k: body[k] for k in ('errorCode', 'errorMessage', 'message', 'code', 'error', 'detail')
                          if k in body and isinstance(body[k], (str, int))}
                detail = json.dumps(fields, ensure_ascii=True).replace(token, '[redacted]')[:800]
        except (ValueError, OSError):
            pass
        raise RuntimeError(f'CurseForge HTTP {error.code}; {detail}; no automatic upload retry') from None
    except urllib.error.URLError:
        raise RuntimeError('CurseForge connection failed; check remote files before retrying') from None


def resolve_versions(available, rows, version_types):
    needed = {v for row in rows for v in row['game_versions']} | set(LOADERS.values()) | {'Client'}
    found = {}
    for label in sorted(needed):
        if label in LOADERS.values():
            slug = 'modloader'
        elif label == 'Client':
            slug = 'environment'
        else:
            require(re.fullmatch(r'\d+\.\d+(?:\.\d+)?', label), 'invalid Minecraft version label')
            slug = 'minecraft-' + '-'.join(label.split('.')[:2])
        types = [t for t in version_types if t.get('slug') == slug]
        require(len(types) == 1, f'CurseForge version type missing or ambiguous: {slug}')
        # The API also returns identically named Bukkit/Addons versions. Only the
        # Minecraft Mods family, loader, or environment type is valid here.
        matches = [v for v in available if v['name'] == label and
                   v.get('gameVersionTypeID') == types[0]['id']]
        require(len(matches) == 1, f'CurseForge label missing or ambiguous: {label} ({slug}); do not drop tags')
        found[label] = matches[0]['id']
    return found


def prepare_upload(args, plan):
    rows = validate_bundle(args.directory, plan)
    require(args.tag in (plan['version'], 'v' + plan['version']), 'release tag does not match pinned plan')
    token = os.environ.get('CURSEFORGE_TOKEN', '')
    project = os.environ.get('CURSEFORGE_PROJECT_ID', '')
    require(token and re.fullmatch(r'[1-9]\d*', project), 'set token and numeric project ID')
    require(project != str(plan['upstream_project']), 'refusing to upload to the upstream project')
    changelog = Path(args.changelog or ROOT / plan['changelog']).read_text()
    require(changelog.strip(), 'empty changelog')
    available = api_json('/game/versions', token)
    version_types = api_json('/game/version-types', token)
    ids = resolve_versions(available, rows, version_types)
    return rows, project, token, changelog, ids


def publish(args, plan):
    receipt = Path(args.receipt)
    require(not receipt.exists(), 'receipt already exists; inspect remote files before retrying')
    rows, project, token, changelog, ids = prepare_upload(args, plan)
    # All local JAR checks and all remote label checks finish before the first POST.
    results = {'version': plan['version'], 'project_id': project, 'files': [], 'status': 'uploading'}
    receipt.parent.mkdir(parents=True, exist_ok=True)
    receipt.write_text(json.dumps(results, indent=2) + '\n')
    for row in rows:
        metadata = {'changelog': changelog, 'changelogType': 'markdown',
                    'displayName': row['file'].removesuffix('.jar'),
                    'releaseType': plan['release_type'],
                    'gameVersions': [ids[v] for v in row['game_versions']] +
                                    [ids[LOADERS[row['loader']]], ids['Client']],
                    'relations': {'projects': [{'slug': 'modern-ui', 'projectID': 352491,
                                                 'type': 'incompatible'}]}}
        if row['loader'] == 'fabric':
            target = next(t for t in plan['targets'] if t['branch'] == row['branch'])
            dependencies = target['metadata']['fabric']['depends']
            for mod, slug, project_id in [('fabric-api', 'fabric-api', 306612)]:
                if mod in dependencies:
                    metadata['relations']['projects'].append({'slug': slug, 'projectID': project_id,
                                                             'type': 'requiredDependency'})
        boundary = 'lumen-' + uuid.uuid4().hex
        prefix = (f'--{boundary}\r\nContent-Disposition: form-data; name="metadata"\r\n'
                  f'Content-Type: application/json\r\n\r\n{json.dumps(metadata)}\r\n'
                  f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{row["file"]}"\r\n'
                  'Content-Type: application/java-archive\r\n\r\n').encode()
        payload = prefix + (Path(args.directory) / row['file']).read_bytes() + f'\r\n--{boundary}--\r\n'.encode()
        result = api_json(f'/projects/{project}/upload-file', token, payload,
                          f'multipart/form-data; boundary={boundary}')
        require(isinstance(result.get('id'), int), 'upload returned no numeric file ID; inspect remote files')
        results['files'].append(dict(row, curseforge_file_id=result['id']))
        receipt.write_text(json.dumps(results, indent=2) + '\n')
        print(f"Uploaded {row['file']}: file ID {result['id']}")
    results['status'] = 'uploaded-awaiting-platform-review'
    receipt.write_text(json.dumps(results, indent=2) + '\n')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--plan', default=str(ROOT / 'release/curseforge-targets.json'))
    sub = parser.add_subparsers(dest='command', required=True)
    sub.add_parser('matrix')
    source = sub.add_parser('verify-source')
    source.add_argument('directory')
    source.add_argument('--branch', required=True)
    validate = sub.add_parser('validate')
    validate.add_argument('directory')
    validate.add_argument('--branch')
    upload = sub.add_parser('publish')
    upload.add_argument('directory')
    upload.add_argument('--tag', required=True)
    upload.add_argument('--changelog', help='Defaults to the versioned changelog in the plan')
    upload.add_argument('--receipt', required=True)
    preflight = sub.add_parser('preflight', help='Read-only API checks; never uploads files')
    preflight.add_argument('directory')
    preflight.add_argument('--tag', required=True)
    preflight.add_argument('--changelog')
    args = parser.parse_args()
    plan = load_plan(args.plan)
    if args.command == 'matrix':
        print(json.dumps({'include': plan['targets']}, separators=(',', ':')))
    elif args.command == 'verify-source':
        verify_source(args.directory, plan, args.branch)
        print('Pinned source and metadata verified')
    elif args.command == 'validate':
        print(json.dumps(validate_bundle(args.directory, plan, args.branch), indent=2))
    elif args.command == 'preflight':
        rows, project, _, _, ids = prepare_upload(args, plan)
        print(json.dumps({'files': len(rows), 'project_id': project, 'resolved_version_ids': ids}, indent=2))
    else:
        publish(args, plan)


if __name__ == '__main__':
    try:
        main()
    except (ValueError, KeyError, OSError, RuntimeError, zipfile.BadZipFile) as error:
        print(f'ERROR: {error}', file=sys.stderr)
        sys.exit(1)
