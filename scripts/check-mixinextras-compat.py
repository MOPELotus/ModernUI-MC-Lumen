#!/usr/bin/env python3
"""Run actual MixinExtras redirect transformers against built Lumen classes, without Minecraft.

Supply ASM, ASM-tree and a Fabric Mixin JAR in --classpath. Library JARs are read-only inputs;
no dependency is downloaded and no game or graphical renderer is started.
"""
import argparse
import os
from pathlib import Path
import re
import subprocess
import tempfile

p = argparse.ArgumentParser(description=__doc__)
p.add_argument('--java-home', type=Path, required=True)
p.add_argument('--classpath', required=True)
p.add_argument('--old-extras', type=Path, required=True)
p.add_argument('--fixed-extras', type=Path, required=True)
p.add_argument('jars', nargs='+', type=Path)
a = p.parse_args()
source = Path(__file__).with_name('tests') / 'MixinExtrasRedirectCheck.java'
negative_failures = 0
with tempfile.TemporaryDirectory(prefix='lumen-extras-check-') as tmp:
    subprocess.run([str(a.java_home / 'bin/javac'), '-proc:none', '-cp', a.classpath,
                    '-d', tmp, str(source)], check=True)
    for jar in a.jars:
        for label, extra in [('0.5.4-negative', a.old_extras), ('0.5.5-fixed', a.fixed_extras)]:
            cp = os.pathsep.join([tmp, a.classpath, str(extra)])
            r = subprocess.run([str(a.java_home / 'bin/java'), '-cp', cp,
                                'MixinExtrasRedirectCheck', str(jar)], capture_output=True,
                               text=True, check=True)
            match = re.search(r'redirectClasses=(\d+) classCastFailures=(\d+)', r.stdout)
            if not match:
                raise AssertionError(r.stdout + r.stderr)
            count, failures = map(int, match.groups())
            if count == 0 or (label == '0.5.5-fixed' and failures):
                raise AssertionError(f'{jar.name} {label}: {r.stdout}')
            if label == '0.5.4-negative':
                negative_failures += failures
            print(f'{jar.name}: {label}: {count} redirect classes, {failures} incompatible annotations')
if not negative_failures:
    raise AssertionError('Negative fixtures did not reproduce the original incompatibility')
print('PASS: old-library incompatibility reproduced; fixed library accepts all supplied production redirects')
