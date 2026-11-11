"""Read dependency declarations before Gradle expands fabric.mod.json."""
import json
from pathlib import Path
from string import Template


def fabric_dependencies(directory, properties):
    path = Path(directory) / 'fabric/src/main/resources/fabric.mod.json'
    dependencies = json.loads(path.read_text())['depends']
    substitutions = {
        'loader_version': properties['fabric_loader_version'],
        'api_min_version': properties['fabric_api_min_version'],
    }
    return {key: Template(value).substitute(substitutions) for key, value in dependencies.items()}
