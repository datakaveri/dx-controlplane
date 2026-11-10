#!/usr/bin/env python3
"""
Regenerate docs/central-openapi/openapi.yaml from docs/controlplane-openapi/openapi.yaml.

The central spec is the full control-plane spec plus the central-catalogue paths. With
isCentralCatEnabled=true the ApiServerVerticle loads docs/central-openapi.yaml *instead of*
docs/openapi.yaml, so every operation registered by the control plane must be present in it.

Usage:
    python3 scripts/sync-central-openapi-root.py   (run automatically by `make docs`)

Kept from the existing central root: openapi, info, servers, and any paths/schemas that are not
in the control-plane root (e.g. /iudx/v2/central/*). Everything else is taken from the
control-plane root with $refs rebased to ../controlplane-openapi/.
"""

import os

import yaml

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CP_ROOT = os.path.join(BASE_DIR, 'docs', 'controlplane-openapi', 'openapi.yaml')
CENTRAL_ROOT = os.path.join(BASE_DIR, 'docs', 'central-openapi', 'openapi.yaml')
CP_PREFIX = '../controlplane-openapi/'


def rebase(node):
    """Prefix relative $refs so they resolve from docs/central-openapi/."""
    if isinstance(node, dict):
        return {
            k: (CP_PREFIX + v if k == '$ref' and not v.startswith(('#', CP_PREFIX)) else rebase(v))
            for k, v in node.items()
        }
    if isinstance(node, list):
        return [rebase(v) for v in node]
    return node


def merge(cp_section, central_section):
    """Control-plane entries (rebased) first, then central-only entries."""
    merged = {k: rebase(v) for k, v in (cp_section or {}).items()}
    for k, v in (central_section or {}).items():
        merged.setdefault(k, v)
    return merged


def main():
    with open(CP_ROOT) as f:
        cp = yaml.safe_load(f)
    with open(CENTRAL_ROOT) as f:
        central = yaml.safe_load(f)

    root = {
        'openapi': central['openapi'],
        'info': central['info'],
        'servers': central['servers'],
    }
    if cp.get('tags') or central.get('tags'):
        tags = list(cp.get('tags', []))
        names = {t['name'] for t in tags}
        tags += [t for t in central.get('tags', []) if t['name'] not in names]
        root['tags'] = tags

    root['paths'] = merge(cp.get('paths'), central.get('paths'))

    cp_components = cp.get('components', {})
    central_components = central.get('components', {})
    components = {}
    for section in list(cp_components) + [s for s in central_components if s not in cp_components]:
        cp_sec, central_sec = cp_components.get(section), central_components.get(section)
        if isinstance(cp_sec, dict) and '$ref' in cp_sec:
            components[section] = rebase(cp_sec)
        else:
            components[section] = merge(cp_sec, central_sec)
    root['components'] = components

    with open(CENTRAL_ROOT, 'w') as f:
        yaml.safe_dump(root, f, sort_keys=False, width=120, allow_unicode=True)
    print(f"Synced {os.path.relpath(CENTRAL_ROOT, BASE_DIR)}: {len(root['paths'])} paths")


if __name__ == '__main__':
    main()
