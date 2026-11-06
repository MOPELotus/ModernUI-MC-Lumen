#!/usr/bin/env python3
"""Offline publication guards: no token, network request, or product build."""
import copy
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
import zipfile

SPEC = importlib.util.spec_from_file_location('curseforge', Path(__file__).with_name('curseforge.py'))
cf = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(cf)
PLAN = cf.load_plan(cf.ROOT / 'release/curseforge-targets.json')


def write_jar(path, target, loader, *, variant='standard', version=None, installer=False):
    manifest = ('Manifest-Version: 1.0\r\nModernUI-Font-Variant: ' + variant + '\r\n' +
                'Implementation-Title: ModernUI-Lumen-' + cf.LOADERS[loader] + '\r\n')
    with zipfile.ZipFile(path, 'w') as jar:
        jar.writestr('META-INF/MANIFEST.MF', manifest)
        if loader == 'fabric':
            jar.writestr('fabric.mod.json', json.dumps({'id': 'modernui', 'name': 'ModernUI Lumen',
                'version': version or PLAN['version'], 'depends': target['metadata']['fabric']['depends']}))
            jar.writestr('icyllis/modernui/mc/fabric/SimpleConfigSpec.class', b'test fixture')
        else:
            jar.writestr('META-INF/neoforge.mods.toml',
                '[[mods]]\nmodId="modernui"\ndisplayName="ModernUI Lumen"\nversion="' +
                (version or PLAN['version']) + '"\n[[dependencies.modernui]]\nmodId="minecraft"\nversionRange="' +
                target['metadata']['neoforge']['minecraft'] + '"\n')
        if installer:
            jar.writestr('icyllis/modernui/mc/MiSansInstaller.class', b'not a real class')


class Guards(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)

    def test_exact_sixteen_standard_bundle(self):
        for target in PLAN['targets']:
            for loader, name in cf.filenames(PLAN, target).items():
                write_jar(self.root / name, target, loader)
        self.assertEqual(len(cf.validate_bundle(self.root, PLAN)), 16)
        (self.root / 'unexpected-universal-misans.jar').write_bytes(b'anything')
        with self.assertRaisesRegex(ValueError, 'JAR set mismatch'):
            cf.validate_bundle(self.root, PLAN)

    def test_disguised_misans_and_wrong_version(self):
        target = PLAN['targets'][0]
        name = cf.filenames(PLAN, target)['fabric']
        path = self.root / name
        for options, message in [({'variant': 'misans'}, 'not standard'),
                                 ({'installer': True}, 'MiSans payload'),
                                 ({'version': '3.13.0.9'}, 'wrong mod identity/version')]:
            write_jar(path, target, 'fabric', **options)
            with self.assertRaisesRegex(ValueError, message):
                cf.validate_jar(path, PLAN, target, 'fabric')

    def test_wrong_loader_and_dependencies_rejected(self):
        target = PLAN['targets'][0]
        path = self.root / cf.filenames(PLAN, target)['neoforge']
        write_jar(path, target, 'fabric')
        with self.assertRaisesRegex(ValueError, 'wrong implementation title'):
            cf.validate_jar(path, PLAN, target, 'neoforge')
        bad = copy.deepcopy(target)
        bad['metadata']['fabric']['depends'].pop('fabric-api')
        path = self.root / cf.filenames(PLAN, target)['fabric']
        write_jar(path, bad, 'fabric')
        with self.assertRaisesRegex(ValueError, 'wrong dependencies'):
            cf.validate_jar(path, PLAN, target, 'fabric')

    def test_unsupported_tag_and_mutable_ref_rejected(self):
        for key, value, message in [('game_versions', ['1.21.1', '1.21.8'], 'unsupported Minecraft tag'),
                                    ('ref', 'master', 'full commit SHA')]:
            bad = copy.deepcopy(PLAN)
            bad['targets'][0][key] = value
            path = self.root / 'plan.json'
            path.write_text(json.dumps(bad))
            with self.assertRaisesRegex(ValueError, message):
                cf.load_plan(path)

    def test_missing_or_ambiguous_remote_label_fails(self):
        rows = [{'game_versions': ['1.21.1']}]
        versions = [{'name': name, 'id': i} for i, name in enumerate(['1.21.1', 'Fabric', 'NeoForge', 'Client'])]
        self.assertEqual(len(cf.resolve_versions(versions, rows)), 4)
        with self.assertRaisesRegex(ValueError, 'missing or ambiguous'):
            cf.resolve_versions(versions[1:], rows)
        with self.assertRaisesRegex(ValueError, 'missing or ambiguous'):
            cf.resolve_versions(versions + [versions[0]], rows)

    def test_all_fabric_targets_are_independent_of_config_port(self):
        branches = {t['branch'] for t in PLAN['targets'] if
                    'forgeconfigapiport' in t['metadata']['fabric']['depends']}
        self.assertEqual(branches, set())
        self.assertTrue(all('fabric-api' in t['metadata']['fabric']['depends'] for t in PLAN['targets']))
        self.assertEqual(len({v for t in PLAN['targets'] for v in t['game_versions']}), 13)

    def test_external_config_dependency_and_reference_rejected(self):
        target = PLAN['targets'][0]
        path = self.root / cf.filenames(PLAN, target)['fabric']
        bad = copy.deepcopy(target)
        bad['metadata']['fabric']['depends']['forgeconfigapiport'] = '>=21.0.5'
        write_jar(path, bad, 'fabric')
        with self.assertRaisesRegex(ValueError, 'Forge Config API Port dependency'):
            cf.validate_jar(path, PLAN, target, 'fabric')
        write_jar(path, target, 'fabric')
        with zipfile.ZipFile(path, 'a') as jar:
            jar.writestr('icyllis/modernui/mc/fabric/ConfigImpl.class',
                         b'fake class containing com/electronwill/nightconfig/core/Config')
        with self.assertRaisesRegex(ValueError, 'external configuration reference'):
            cf.validate_jar(path, PLAN, target, 'fabric')


if __name__ == '__main__':
    unittest.main()
