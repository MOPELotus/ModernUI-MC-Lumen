#!/usr/bin/env python3
"""Check that Fabric release JARs provide their declared compatible MixinExtras library."""
import argparse
import io
import json
import zipfile

WRAPPER = 'com/llamalad7/mixinextras/wrapper/factory/FactoryRedirectWrapperMixinTransformer.class'


def validate_fabric_bundle(jar, meta=None):
    """Validate a caller-owned ZipFile, or a JAR path; never close a caller-owned file."""
    if not isinstance(jar, zipfile.ZipFile):
        try:
            with zipfile.ZipFile(jar) as opened:
                return validate_fabric_bundle(opened, meta)
        except (OSError, zipfile.BadZipFile) as error:
            raise ValueError('Cannot read Fabric JAR') from error
    try:
        if meta is None:
            meta = json.loads(jar.read('fabric.mod.json'))
        if meta['depends'].get('mixinextras') != '>=0.5.5':
            raise ValueError('Missing MixinExtras >=0.5.5 requirement')
        embedded = []
        registered = set()
        for entry in meta.get('jars', []):
            name = entry['file']
            if name in registered:
                raise ValueError('Duplicate nested JAR registration')
            registered.add(name)
            with zipfile.ZipFile(io.BytesIO(jar.read(name))) as nested:
                if 'fabric.mod.json' not in nested.namelist():
                    continue
                child = json.loads(nested.read('fabric.mod.json'))
                if child.get('id') == 'mixinextras':
                    embedded.append(child['version'])
                    if WRAPPER not in nested.namelist():
                        raise ValueError('Incomplete embedded MixinExtras library')
        if embedded != ['0.5.5']:
            raise ValueError('Exactly one registered MixinExtras 0.5.5 library is required')
    except (KeyError, TypeError, json.JSONDecodeError, zipfile.BadZipFile, OSError) as error:
        raise ValueError('Invalid registered MixinExtras bundle') from error


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('jars', nargs='+')
    for filename in p.parse_args().jars:
        validate_fabric_bundle(filename)
        print(f'PASS: {filename}: bundled MixinExtras 0.5.5 satisfies the runtime requirement')


if __name__ == '__main__':
    main()
