#!/usr/bin/env python3
"""Offline publication guards: no token, network request, or product build."""
import copy
import importlib.util
import json
import io
from pathlib import Path
import tempfile
import unittest
import zipfile

SPEC = importlib.util.spec_from_file_location('curseforge', Path(__file__).with_name('curseforge.py'))
cf = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(cf)
PLAN = cf.load_plan(cf.ROOT / 'release/curseforge-targets.json')


def write_jar(path, target, loader, *, variant='standard', version=None, installer=False, mixin_version='0.5.5', mixin_registered=True, mixin_payload=True, mixin_class=True):
    manifest = ('Manifest-Version: 1.0\r\nModernUI-Font-Variant: ' + variant + '\r\n' +
                'Implementation-Title: ModernUI-Lumen-' + cf.LOADERS[loader] + '\r\n')
    with zipfile.ZipFile(path, 'w') as jar:
        jar.writestr('META-INF/MANIFEST.MF', manifest)
        if loader == 'fabric':
            meta = {'id': 'modernui', 'name': 'ModernUI Lumen', 'version': version or PLAN['version'],
                    'depends': target['metadata']['fabric']['depends']}
            if mixin_registered: meta['jars'] = [{'file': 'META-INF/jars/mixinextras-fabric.jar'}]
            jar.writestr('fabric.mod.json', json.dumps(meta))
            if mixin_payload:
                nested = io.BytesIO()
                with zipfile.ZipFile(nested, 'w') as child:
                    child.writestr('fabric.mod.json', json.dumps({'id': 'mixinextras', 'version': mixin_version}))
                    if mixin_class: child.writestr('com/llamalad7/mixinextras/wrapper/factory/FactoryRedirectWrapperMixinTransformer.class', b'fixture')
                jar.writestr('META-INF/jars/mixinextras-fabric.jar', nested.getvalue())
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
        types = [{'slug': 'minecraft-1-21', 'id': 31}, {'slug': 'modloader', 'id': 32},
                 {'slug': 'environment', 'id': 33}]
        versions = [{'name': name, 'id': i, 'gameVersionTypeID': type_id}
                    for i, (name, type_id) in enumerate([
                        ('1.21.1', 31), ('Fabric', 32), ('NeoForge', 32), ('Client', 33)])]
        self.assertEqual(len(cf.resolve_versions(versions, rows, types)), 4)
        with self.assertRaisesRegex(ValueError, 'missing or ambiguous'):
            cf.resolve_versions(versions[1:], rows, types)
        with self.assertRaisesRegex(ValueError, 'missing or ambiguous'):
            cf.resolve_versions(versions + [versions[0]], rows, types)

    def test_minecraft_mod_types_exclude_same_name_plugin_and_addon_versions(self):
        labels = sorted({v for target in PLAN['targets'] for v in target['game_versions']})
        rows = [{'game_versions': labels}]
        slugs = {'minecraft-' + '-'.join(v.split('.')[:2]) for v in labels}
        slugs |= {'modloader', 'environment'}
        types = [{'slug': slug, 'id': 100 + i} for i, slug in enumerate(sorted(slugs))]
        type_ids = {t['slug']: t['id'] for t in types}
        types += [{'slug': 'addons', 'id': 615}]
        available = []
        expected = {}
        for i, label in enumerate(labels):
            expected[label] = 200 + i
            available += [{'name': label, 'id': 1000 + i, 'gameVersionTypeID': 1},
                          {'name': label, 'id': 2000 + i, 'gameVersionTypeID': 615},
                          {'name': label, 'id': expected[label], 'gameVersionTypeID':
                           type_ids['minecraft-' + '-'.join(label.split('.')[:2])]}]
        for i, label in enumerate(['Fabric', 'NeoForge', 'Client']):
            expected[label] = 300 + i
            slug = 'environment' if label == 'Client' else 'modloader'
            available += [{'name': label, 'id': expected[label], 'gameVersionTypeID': type_ids[slug]},
                          {'name': label, 'id': 3000 + i, 'gameVersionTypeID': 615}]
        self.assertEqual(cf.resolve_versions(available, rows, types), expected)
        duplicate = next(v for v in available if v['name'] == labels[0] and
                         v['id'] == expected[labels[0]])
        with self.assertRaisesRegex(ValueError, 'label missing or ambiguous'):
            cf.resolve_versions(available + [dict(duplicate, id=9999)], rows, types)
        wrong_types_only = [v for v in available if v != duplicate]
        with self.assertRaisesRegex(ValueError, 'label missing or ambiguous'):
            cf.resolve_versions(wrong_types_only, rows, types)
        with self.assertRaisesRegex(ValueError, 'version type missing or ambiguous'):
            cf.resolve_versions(available, rows, types + [types[0]])
        with self.assertRaisesRegex(ValueError, 'version type missing or ambiguous'):
            cf.resolve_versions(available, rows, [t for t in types if t['slug'] != 'modloader'])

    def test_upload_relation_project_ids_are_json_integers(self):
        from types import SimpleNamespace
        from unittest import mock
        name = cf.filenames(PLAN, PLAN['targets'][0])['fabric']
        (self.root / name).write_bytes(b'test upload bytes')
        rows = [{'file': name, 'loader': 'fabric', 'branch': PLAN['targets'][0]['branch'],
                 'game_versions': ['1.21', '1.21.1']}]
        args = SimpleNamespace(directory=self.root, receipt=self.root / 'receipt.json')
        ready = (rows, '1788481', 'test-secret', 'notes',
                 {'1.21': 1, '1.21.1': 2, 'Fabric': 3, 'Client': 4})
        with mock.patch.object(cf, 'prepare_upload', return_value=ready), \
             mock.patch.object(cf, 'api_json', return_value={'id': 123}) as api:
            cf.publish(args, PLAN)
        body = api.call_args.args[2]
        metadata = json.loads(body.split(b'\r\n\r\n', 1)[1].split(b'\r\n--', 1)[0])
        relations = metadata['relations']['projects']
        self.assertEqual([r['projectID'] for r in relations], [352491, 306612])
        self.assertTrue(all(type(r['projectID']) is int for r in relations))

    def test_http_error_diagnostic_redacts_token(self):
        import io
        import urllib.error
        from unittest import mock
        error = urllib.error.HTTPError('https://example.invalid', 400, 'Bad Request', {},
            io.BytesIO(b'{"errorCode":1001,"errorMessage":"bad test-secret","headers":"private"}'))
        opener = mock.Mock()
        opener.open.side_effect = error
        with mock.patch.object(cf.urllib.request, 'build_opener', return_value=opener):
            with self.assertRaises(RuntimeError) as caught:
                cf.api_json('/test', 'test-secret')
        text = str(caught.exception)
        self.assertIn('1001', text)
        self.assertIn('[redacted]', text)
        self.assertNotIn('test-secret', text)
        self.assertNotIn('private', text)

    def test_mixinextras_must_be_registered_and_bundled(self):
        target = PLAN['targets'][0]
        path = self.root / cf.filenames(PLAN, target)['fabric']
        for options in ({'mixin_payload': False}, {'mixin_version': '0.4.1'},
                        {'mixin_registered': False}, {'mixin_class': False}):
            with self.subTest(options=options):
                write_jar(path, target, 'fabric', **options)
                with self.assertRaises(ValueError):
                    cf.validate_jar(path, PLAN, target, 'fabric')

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
