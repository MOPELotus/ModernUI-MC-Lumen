#!/usr/bin/env python3
"""Validate every published loader/edition pair using the final, shaded JARs."""
import json
from pathlib import Path
import re
import sys
import tomllib
import zipfile

root = Path(__file__).resolve().parents[1]
properties = dict(line.split('=', 1) for line in (root / 'gradle.properties').read_text().splitlines()
                  if '=' in line and not line.lstrip().startswith('#'))
mc, version = properties['minecraft_version'], properties['mod_version']
output, *loaders = sys.argv[1:]
assert loaders and len(loaders) == len(set(loaders)), 'specify unique loader names'
assert re.fullmatch(r'\d+\.\d+\.\d+\.\d+', version), f'unexpected release version: {version}'
expected = set()
for loader in loaders:
    label = {'fabric': 'Fabric', 'forge': 'Forge', 'neoforge': 'NeoForge'}[loader]
    for misans in (False, True):
        variant = 'misans' if misans else 'standard'
        suffix = '-misans' if misans else ''
        name = f'ModernUI-Lumen-{label}-{mc}-{version}-universal{suffix}.jar'
        expected.add(name)
        with zipfile.ZipFile(Path(output) / name) as jar:
            entries = set(jar.namelist())
            assert jar.testzip() is None, name
            if loader == 'neoforge':
                # Nested compiler JARs bypass FML's Mixin-transforming loader.
                assert 'icyllis/arc3d/compiler/spirv/SPIRVCodeGenerator.class' in entries, name
                assert not any('arc3d-compiler' in entry and entry.endswith('.jar')
                               for entry in entries), name
                mixin = 'icyllis/modernui/mc/mixin/MixinSPIRVCodeGenerator.class'
                assert mixin in entries, name
                config = json.loads(jar.read('mixins.modernui-neoforge.json'))
                assert 'MixinSPIRVCodeGenerator' in config['client'], name
                assert config['plugin'] == 'icyllis.modernui.mc.NeoMixinConfigPlugin', name
                assert 'MixinNeoVulkanRenderPipeline' in config['client'], name
                for cls in ('NeoMixinConfigPlugin', 'VulkanPipelineCleanupCompat',
                            'mixin/MixinNeoVulkanRenderPipeline'):
                    assert f'icyllis/modernui/mc/{cls}.class' in entries, (name, cls)
            manifest = jar.read('META-INF/MANIFEST.MF').decode().replace('\r\n ', '')
            assert re.search(rf'^ModernUI-Font-Variant: {variant}\r?$', manifest, re.M), name
            for title in ('Specification-Title', 'Implementation-Title'):
                assert re.search(rf'^{title}: ModernUI-Lumen-{label}\r?$', manifest, re.M), (name, title)
            if 'pack.mcmeta' in entries:
                assert json.loads(jar.read('pack.mcmeta'))['pack']['description'] == 'ModernUI Lumen for Minecraft', name
            if loader == 'fabric':
                meta = json.loads(jar.read('fabric.mod.json'))
                ident, actual_version, display = meta['id'], meta['version'], meta['name']
                assert meta['authors'] == ['BloCamLimb', 'MOPELotus'], name
                assert meta['contact'] == {
                    'homepage': 'https://github.com/MOPELotus/ModernUI-MC-Lumen',
                    'issues': 'https://github.com/MOPELotus/ModernUI-MC-Lumen/issues',
                    'sources': 'https://github.com/MOPELotus/ModernUI-MC-Lumen'}, name
            else:
                path = 'META-INF/neoforge.mods.toml' if loader == 'neoforge' else 'META-INF/mods.toml'
                document = tomllib.loads(jar.read(path).decode())
                if loader == 'neoforge' and mc == '26.3':
                    assert document['loaderVersion'] == '[12,)', name
                    dependencies = document['dependencies']['modernui']
                    assert any(d['modId'] == 'neoforge' and d['versionRange'] == '[26.3.0.101,)'
                               for d in dependencies), name
                meta = document['mods'][0]
                ident, actual_version, display = meta['modId'], meta['version'], meta['displayName']
                assert meta['authors'] == 'BloCamLimb, MOPELotus', name
                assert meta['displayURL'] == 'https://github.com/MOPELotus/ModernUI-MC-Lumen', name
            assert 'not an official upstream release' in meta['description'], name
            assert (ident, actual_version, display) == ('modernui', version,
                    'ModernUI Lumen (MiSans)' if misans else 'ModernUI Lumen'), name
            for cls in ('MiSansInstaller', 'MiSansSetup'):
                assert (f'icyllis/modernui/mc/{cls}.class' in entries) == misans, (name, cls)
            if not misans:
                for cls in ('ModernUIClient', 'mixin/MixinMinecraft'):
                    data = jar.read(f'icyllis/modernui/mc/{cls}.class')
                    assert b'icyllis/modernui/mc/MiSansSetup' not in data, (name, cls)
            for cls in ('text/TextRunRenderState', 'text/TextSampling', 'FontVariantConfig'):
                assert f'icyllis/modernui/mc/{cls}.class' in entries, (name, cls)
            assert jar.read('icyllis/modernui/mc/FontVariant.class').find(
                    f'client-{variant}.toml'.encode()) >= 0, name
        print(f'PASS: {name}')
actual = {p.name for p in Path(output).glob('*.jar')}
assert actual == expected, f'missing={expected-actual}, unexpected={actual-expected}'
print(f'PASS: {len(expected)} artifacts; all loaders have standard and MiSans editions')
