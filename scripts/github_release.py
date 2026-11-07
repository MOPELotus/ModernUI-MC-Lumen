#!/usr/bin/env python3
"""Validate the complete GitHub release: eight branches, both loaders and font editions."""
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import subprocess
import tomllib
import zipfile

ROOT = Path(__file__).resolve().parents[1]
# One import-safe validator is also used by the standalone bundle check.
_BUNDLE_SPEC = importlib.util.spec_from_file_location(
    'lumen_mixinextras_bundle', Path(__file__).with_name('check-mixinextras-bundle.py'))
_BUNDLE_CHECK = importlib.util.module_from_spec(_BUNDLE_SPEC)
_BUNDLE_SPEC.loader.exec_module(_BUNDLE_CHECK)

LOADERS = {'fabric': 'Fabric', 'neoforge': 'NeoForge'}
EDITIONS = ('standard', 'misans')


def require(value, message):
    if not value:
        raise ValueError(message)


def load_plan():
    p = json.loads((ROOT / 'release/github-targets.json').read_text())
    require(p['schema'] == 1, 'unsupported plan schema')
    require({t['branch'] for t in p['targets']} == {'1.21.1', '1.21.8', '1.21.9', '1.21.10', '1.21.11', '26.1', '26.2', 'master'}, 'wrong maintained branches')
    require(re.fullmatch(r'\d+\.\d+\.\d+\.\d+', p['version']), 'invalid version')
    require(len(p['targets']) == 8 and len({t['branch'] for t in p['targets']}) == 8, 'expected eight unique branches')
    require(len({t['minecraft'] for t in p['targets']}) == 8, 'duplicate game build')
    for t in p['targets']:
        require(re.fullmatch(r'[0-9a-f]{40}', t['ref']), 'source must be pinned to a full SHA')
        require(t['java'] == (21 if t['minecraft'].startswith('1.21') else 25), 'wrong Java')
        require(t['metadata']['fabric']['depends'].get('mixinextras') == '>=0.5.5', 'missing bundled MixinExtras requirement')
    require(p['changelog'] == f'release/changelogs/{p["version"]}.md', 'wrong changelog')
    return p


def filenames(plan, target):
    return {(loader, edition): f'ModernUI-Lumen-{label}-{target["minecraft"]}-{plan["version"]}-universal' +
            ('-misans' if edition == 'misans' else '') + '.jar'
            for loader, label in LOADERS.items() for edition in EDITIONS}


def verify_source(source, plan, target):
    source = Path(source)
    head = subprocess.check_output(['git', '-C', str(source), 'rev-parse', 'HEAD'], text=True).strip()
    require(head == target['ref'], 'wrong source checkout')
    props = dict(l.split('=', 1) for l in (source / 'gradle.properties').read_text().splitlines()
                 if '=' in l and not l.lstrip().startswith('#'))
    require(props['mod_version'] == plan['version'] and props['minecraft_version'] == target['minecraft'], 'wrong source version')


def validate(directory, plan, branch=None):
    directory = Path(directory)
    targets = [t for t in plan['targets'] if branch is None or t['branch'] == branch]
    require(targets, 'unknown branch')
    expected = {n for t in targets for n in filenames(plan, t).values()}
    require({p.relative_to(directory).as_posix() for p in directory.rglob('*.jar')} == expected,
            'release must contain every loader/edition pair and no extra JARs')
    rows = []
    for target in targets:
        for (loader, edition), name in filenames(plan, target).items():
            path = directory / name
            require(path.is_file() and not path.is_symlink(), 'expected a regular JAR')
            with zipfile.ZipFile(path) as z:
                require(z.testzip() is None, f'corrupt JAR: {name}')
                entries = set(z.namelist())
                manifest = z.read('META-INF/MANIFEST.MF').decode().replace('\r\n ', '').replace('\n ', '')
                fields = dict(l.split(': ', 1) for l in manifest.splitlines() if ': ' in l)
                require(fields.get('ModernUI-Font-Variant') == edition, f'wrong font edition: {name}')
                require(fields.get('Implementation-Title') == 'ModernUI-Lumen-' + LOADERS[loader], 'wrong loader')
                if loader == 'fabric':
                    m = json.loads(z.read('fabric.mod.json'))
                    _BUNDLE_CHECK.validate_fabric_bundle(z, m)
                    ident, display, version = m['id'], m['name'], m['version']
                    require(m['depends'] == target['metadata']['fabric']['depends'], 'wrong Fabric dependencies')
                    require('forgeconfigapiport' not in m['depends'], 'FCAP dependency')
                    require('icyllis/modernui/mc/fabric/SimpleConfigSpec.class' in entries, 'missing built-in config')
                else:
                    doc = tomllib.loads(z.read('META-INF/neoforge.mods.toml').decode());m = doc['mods'][0]
                    ident, display, version = m['modId'], m['displayName'], m['version']
                    mc = next(d['versionRange'] for d in doc['dependencies']['modernui'] if d['modId'] == 'minecraft')
                    require(mc == target['metadata']['neoforge']['minecraft'], 'wrong Minecraft range')
                require((ident, display, version) == ('modernui', 'ModernUI Lumen' + (' (MiSans)' if edition == 'misans' else ''), plan['version']), 'wrong mod identity/version')
                for cls in ('MiSansInstaller', 'MiSansSetup'):
                    require((f'icyllis/modernui/mc/{cls}.class' in entries) == (edition == 'misans'), 'wrong installer payload')
                if edition == 'standard':
                    require(not any('misans' in e.lower() for e in entries), 'MiSans payload in standard edition')
            with path.open('rb') as f:
                digest = hashlib.file_digest(f, 'sha256').hexdigest()
            rows.append({'file': name, 'sha256': digest, 'bytes': path.stat().st_size,
                         'source_ref': target['ref'], 'branch': target['branch'], 'loader': loader, 'edition': edition})
    return rows


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=['matrix', 'verify-source', 'validate', 'check-tag'])
    parser.add_argument('path', nargs='?')
    parser.add_argument('--branch')
    args = parser.parse_args();plan = load_plan()
    if args.command == 'matrix':
        print(json.dumps({'include': [dict(t, version=plan['version']) for t in plan['targets']]}, separators=(',', ':')))
    elif args.command == 'check-tag':
        require(args.path in (plan['version'], 'v' + plan['version']), 'tag must match the complete release version')
    elif args.command == 'verify-source':
        verify_source(args.path, plan, next(t for t in plan['targets'] if t['branch'] == args.branch))
    else:
        directory = Path(args.path)
        rows = validate(directory, plan, args.branch)
        print(json.dumps(rows, indent=2))


if __name__ == '__main__':
    main()
