#!/usr/bin/env python3
"""Offline guards for the complete GitHub bundle; never publishes or builds Minecraft."""
import copy
import importlib.util
import json
import io
from pathlib import Path
import tempfile
import unittest
import zipfile

spec = importlib.util.spec_from_file_location('github_release', Path(__file__).with_name('github_release.py'))
gh = importlib.util.module_from_spec(spec);spec.loader.exec_module(gh)
PLAN = gh.load_plan()


def jar(path, target, loader, edition, *, version=None, display=None, manifest_edition=None, installers=None, mixin_version='0.5.5', mixin_registered=True, mixin_payload=True, mixin_class=True):
    with zipfile.ZipFile(path, 'w') as z:
        z.writestr('META-INF/MANIFEST.MF', f'Manifest-Version: 1.0\r\nModernUI-Font-Variant: {manifest_edition or edition}\r\nImplementation-Title: ModernUI-Lumen-{gh.LOADERS[loader]}\r\n')
        name = display or ('ModernUI Lumen' + (' (MiSans)' if edition == 'misans' else ''))
        version = version or PLAN['version']
        if loader == 'fabric':
            meta = {'id':'modernui','name':name,'version':version,'depends':target['metadata']['fabric']['depends']}
            if mixin_registered: meta['jars'] = [{'file': 'META-INF/jars/mixinextras-fabric.jar'}]
            z.writestr('fabric.mod.json', json.dumps(meta))
            if mixin_payload:
                nested = io.BytesIO()
                with zipfile.ZipFile(nested, 'w') as child:
                    child.writestr('fabric.mod.json', json.dumps({'id': 'mixinextras', 'version': mixin_version}))
                    if mixin_class: child.writestr('com/llamalad7/mixinextras/wrapper/factory/FactoryRedirectWrapperMixinTransformer.class', b'fixture')
                z.writestr('META-INF/jars/mixinextras-fabric.jar', nested.getvalue())
            z.writestr('icyllis/modernui/mc/fabric/SimpleConfigSpec.class', b'fixture')
        else:
            z.writestr('META-INF/neoforge.mods.toml', f'[[mods]]\nmodId="modernui"\ndisplayName="{name}"\nversion="{version}"\n[[dependencies.modernui]]\nmodId="minecraft"\nversionRange="{target["metadata"]["neoforge"]["minecraft"]}"\n')
        if installers if installers is not None else edition == 'misans':
            for c in ['MiSansInstaller','MiSansSetup']:
                z.writestr(f'icyllis/modernui/mc/{c}.class', b'fixture')


class ReleaseGuards(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup);self.root=Path(self.temp.name)

    def fill(self, plan=PLAN):
        for t in plan['targets']:
            for (loader,edition),name in gh.filenames(plan,t).items():jar(self.root/name,t,loader,edition)

    def test_all32_and_missing_edition(self):
        self.fill();self.assertEqual(len(gh.validate(self.root,PLAN)),32)
        next(self.root.glob('*-misans.jar')).unlink()
        with self.assertRaisesRegex(ValueError,'every loader/edition'):gh.validate(self.root,PLAN)

    def test_unexpected_file(self):
        self.fill();(self.root/'wrong.jar').write_bytes(b'bad')
        with self.assertRaisesRegex(ValueError,'no extra'):gh.validate(self.root,PLAN)

    def test_disguised_edition_and_installers(self):
        t=PLAN['targets'][0]
        for opts in [{'manifest_edition':'misans'},{'installers':True}]:
            self.fill();p=self.root/gh.filenames(PLAN,t)['fabric','standard'];jar(p,t,'fabric','standard',**opts)
            with self.assertRaises(ValueError):gh.validate(self.root,PLAN)

    def test_wrong_version_and_range(self):
        self.fill();t=PLAN['targets'][0];p=self.root/gh.filenames(PLAN,t)['fabric','standard'];jar(p,t,'fabric','standard',version='3.13.0.9')
        with self.assertRaisesRegex(ValueError,'identity/version'):gh.validate(self.root,PLAN)
        self.fill();bad=copy.deepcopy(t);bad['metadata']['neoforge']['minecraft']='[1.20,1.21)';p=self.root/gh.filenames(PLAN,t)['neoforge','standard'];jar(p,bad,'neoforge','standard')
        with self.assertRaisesRegex(ValueError,'Minecraft range'):gh.validate(self.root,PLAN)

    def test_misans_requires_its_payload(self):
        self.fill();t=PLAN['targets'][0];p=self.root/gh.filenames(PLAN,t)['fabric','misans'];jar(p,t,'fabric','misans',installers=False)
        with self.assertRaisesRegex(ValueError,'installer payload'):gh.validate(self.root,PLAN)

    def test_mixinextras_must_be_registered_and_bundled(self):
        self.fill()
        target = PLAN['targets'][0]
        path = self.root / gh.filenames(PLAN, target)['fabric', 'standard']
        for options in ({'mixin_payload': False}, {'mixin_version': '0.4.1'},
                        {'mixin_registered': False}, {'mixin_class': False}):
            with self.subTest(options=options):
                jar(path, target, 'fabric', 'standard', **options)
                with self.assertRaises(ValueError): gh.validate(self.root, PLAN)

    def test_branch_bundle_has_exact_four(self):
        t=PLAN['targets'][0]
        for (loader,edition),name in gh.filenames(PLAN,t).items():jar(self.root/name,t,loader,edition)
        self.assertEqual(len(gh.validate(self.root,PLAN,t['branch'])),4)
        with self.assertRaises(ValueError):gh.validate(self.root,PLAN)


if __name__=='__main__':unittest.main()
