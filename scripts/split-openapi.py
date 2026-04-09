#!/usr/bin/env python3
"""
Split monolithic OpenAPI YAML files into modular multi-file structure.

Usage:
    python scripts/split-openapi.py

This reads the bundled docs/openapi.yaml, docs/acl-openapi.yaml, and
docs/central-openapi.yaml files and produces the split structure under
docs/controlplane-openapi/, docs/acl-openapi/, and docs/central-openapi/.
"""

import yaml
import os
import sys
import copy
from collections import defaultdict

# Preserve YAML formatting
class LiteralStr(str):
    pass

def literal_str_representer(dumper, data):
    if '\n' in data:
        return dumper.represent_scalar('tag:yaml.org,2002:str', data, style='|')
    return dumper.represent_scalar('tag:yaml.org,2002:str', data)

yaml.add_representer(LiteralStr, literal_str_representer)

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DOCS_DIR = os.path.join(BASE_DIR, 'docs')


def write_yaml(filepath, data):
    """Write YAML data to file, creating directories as needed."""
    os.makedirs(os.path.dirname(filepath), exist_ok=True)
    with open(filepath, 'w') as f:
        yaml.dump(data, f, default_flow_style=False, allow_unicode=True, sort_keys=False, width=120)
    print(f"  Created: {os.path.relpath(filepath, BASE_DIR)}")


def get_tag_for_path(path_key, path_data):
    """Determine the primary tag for a path based on its operations."""
    for method in ['get', 'post', 'put', 'patch', 'delete', 'head', 'options']:
        if method in path_data and 'tags' in path_data[method]:
            return path_data[method]['tags'][0]
    return 'Unknown'


# Tag to filename mapping for openapi.yaml paths
TAG_TO_FILE = {
    'Resource Servers': 'resource-servers',
    'Organisation APIs': 'organisations',
    'Role APIs': 'roles',
    'Credit APIs': 'credits',
    'Compute APIs': 'compute',
    'KYC APIs': 'kyc',
    'Delegation APIs': 'delegation',
    'Asset APIs': 'assets',
    'Client APIs': 'client',
    'Certificate API': 'certificate',
    'CAT Entity': 'catalogue',
    'Discovery \u2013 Public': 'discovery',
    'Discovery \u2013 My Assets': 'discovery',
    'Organisation Assets Management': 'org-assets',
    'Platform Assets Management': 'platform-assets',
    'List Available Filters': 'filters',
    'Activity Logs': 'auditing',
    'Activity Report': 'auditing',
    'Subscriptions': 'subscriptions',
    'App': 'app',
    'Leaderboard': 'leaderboard',
    'User Interactions': 'interactions',
    'Summary': 'summary',
    'token': 'certificate',  # token endpoints grouped with JWKS/certificate
    # Central-specific tags
    'List Available Central CAT Filters': 'central-catalogue',
    'Central CAT Discovery \u2013 Public': 'central-catalogue',
}

# Schema to filename mapping for openapi.yaml
SCHEMA_TO_FILE = {
    # Common/error schemas
    'ErrorResponse': 'common',
    'Success': 'common',
    'BadRequest': 'common',
    'Forbidden': 'common',
    'NotFound': 'common',
    'UnauthorizedResponse': 'common',
    'ForbiddenResponse': 'common',
    'BadRequestResponse': 'common',
    'InternalServerErrorResponse': 'common',
    'GenericBadRequestResponse': 'common',
    'Unauthorized': 'common',
    # Resource server
    'ResourceServer': 'resource-server',
    'ResourceServerCreateRequest': 'resource-server',
    'ResourceServerResponse': 'resource-server',
    # Subscription
    'NgsiLdSubscriptionResponse': 'subscription',
    'NgsiLdSubscriptionErrorResponse400': 'subscription',
    'NgsiLdSubscriptionErrorResponse404': 'subscription',
    'NgsiLdSubscriptionErrorResponse401': 'subscription',
    'NgsiLdSubscriptionErrorResponse409': 'subscription',
    'NgsiLdSubscriptionErrorResponse500': 'subscription',
    # Catalogue / search
    'Allqueue': 'catalogue',
    'DataBankResourceCreateResult': 'catalogue',
    'AutocompleteFuzzyTextSearchRequest': 'catalogue',
    'SearchCriteriaRequest': 'catalogue',
    'TextSearchRequest': 'catalogue',
    'ComplexSearchRequest': 'catalogue',
    'SearchCriteria': 'catalogue',
    'SearchResponse': 'catalogue',
    'AttributesBasedFilteringCount': 'catalogue',
    'TextOnly': 'catalogue',
    'ComplexRequestForCount': 'catalogue',
    'TextFuzzyAuto': 'catalogue',
    # Interaction / feedback / bookmark
    'UserInteractionRequest': 'interaction',
    'InteractionResponse': 'interaction',
    'UserFeedbackRequest': 'interaction',
    'UserFeedbackPaginatedResponse': 'interaction',
    'ProviderFeedbackPaginatedResponse': 'interaction',
    'PostItemVoteRequest': 'interaction',
    'ItemVoteResponse': 'interaction',
    'PostBookmarkRequest': 'interaction',
    'BookmarkResponse': 'interaction',
    'BookmarkResult': 'interaction',
    'GetBookmarksSuccessResponse': 'interaction',
    'BookmarkSuccessResult': 'interaction',
    'BulkSyncFailure': 'interaction',
    'BulkSyncResult': 'interaction',
    'UserInteractionSyncResponse': 'interaction',
    'UserInteractionResponse': 'interaction',
    'UserInteractionState': 'interaction',
    # Leaderboard
    'LeaderBoardResponseAsset': 'leaderboard',
    'LeaderBoardProvider': 'leaderboard',
    'LeaderBoardOrg': 'leaderboard',
    # App
    'PostAppIdRequest': 'app',
    'AppIdResponse': 'app',
    'AppIdResult': 'app',
    'GetAppsSuccessResponse': 'app',
    'AppTokenRequest': 'app',
    # Pagination
    'PaginationInfo': 'pagination',
    'PaginationInfoResponse': 'pagination',
    'GetPaginationInfo': 'pagination',
    'PageInfo': 'pagination',
    # Auditing
    'AuditActivitySuccessResponse': 'auditing',
    'AuditActivityLogItem': 'auditing',
    # Access / asset request
    'AssetRequest': 'access',
    'UpdateAccessRequest': 'access',
    'AccessRequest': 'access',
    'AssetSummary': 'access',
    # Delegation
    'DelegationRole': 'delegation',
    'DelegationScope': 'delegation',
    'DelegationEntityType': 'delegation',
    # Role
    'CustomRoleResponse': 'role',
    'CustomRoleItem': 'role',
}


def rewrite_refs(obj, schema_file_map):
    """
    Rewrite $ref from '#/components/schemas/X' to relative file paths.
    This is used in path files so they point to the right schema files.
    """
    if isinstance(obj, dict):
        if '$ref' in obj:
            ref = obj['$ref']
            if ref.startswith('#/components/schemas/'):
                schema_name = ref.split('/')[-1]
                if schema_name in schema_file_map:
                    target_file = schema_file_map[schema_name]
                    obj['$ref'] = f'../components/schemas/{target_file}.yaml#/{schema_name}'
            elif ref.startswith('#/components/parameters/'):
                param_name = ref.split('/')[-1]
                obj['$ref'] = f'../components/parameters.yaml#/{param_name}'
        for key, val in obj.items():
            rewrite_refs(val, schema_file_map)
    elif isinstance(obj, list):
        for item in obj:
            rewrite_refs(item, schema_file_map)


def split_main_openapi():
    """Split docs/openapi.yaml into docs/controlplane-openapi/ structure."""
    print("\n=== Splitting openapi.yaml ===")

    src = os.path.join(DOCS_DIR, 'openapi.yaml')
    out_dir = os.path.join(DOCS_DIR, 'controlplane-openapi')

    with open(src, 'r') as f:
        spec = yaml.safe_load(f)

    # ── 1. Group paths by tag → filename ──
    path_groups = defaultdict(dict)
    for path_key, path_data in spec.get('paths', {}).items():
        tag = get_tag_for_path(path_key, path_data)
        filename = TAG_TO_FILE.get(tag, 'misc')
        path_groups[filename][path_key] = path_data

    # ── 2. Group schemas by filename ──
    schema_groups = defaultdict(dict)
    schemas = spec.get('components', {}).get('schemas', {})
    for schema_name, schema_data in schemas.items():
        filename = SCHEMA_TO_FILE.get(schema_name, 'common')
        schema_groups[filename][schema_name] = schema_data

    # Build schema → file map for $ref rewriting
    schema_file_map = {}
    for schema_name in schemas:
        schema_file_map[schema_name] = SCHEMA_TO_FILE.get(schema_name, 'common')

    # ── 3. Write path files ──
    for filename, paths in path_groups.items():
        # Deep copy to avoid mutating original
        paths_copy = copy.deepcopy(paths)
        rewrite_refs(paths_copy, schema_file_map)
        write_yaml(os.path.join(out_dir, 'paths', f'{filename}.yaml'), paths_copy)

    # ── 4. Write schema files ──
    for filename, file_schemas in schema_groups.items():
        # Deep copy and rewrite internal $refs
        schemas_copy = copy.deepcopy(file_schemas)
        rewrite_refs_in_schemas(schemas_copy, schema_file_map, filename)
        write_yaml(os.path.join(out_dir, 'components', 'schemas', f'{filename}.yaml'), schemas_copy)

    # ── 5. Write parameters file ──
    parameters = spec.get('components', {}).get('parameters', {})
    if parameters:
        write_yaml(os.path.join(out_dir, 'components', 'parameters.yaml'), parameters)

    # ── 6. Write security schemes file ──
    security_schemes = spec.get('components', {}).get('securitySchemes', {})
    if security_schemes:
        write_yaml(os.path.join(out_dir, 'components', 'security-schemes.yaml'), security_schemes)

    # ── 7. Write root openapi.yaml with $ref ──
    root = {
        'openapi': spec['openapi'],
        'info': spec['info'],
        'servers': spec['servers'],
    }

    # Build paths with $ref
    root_paths = {}
    for filename, paths in sorted(path_groups.items()):
        for path_key in paths:
            root_paths[path_key] = {'$ref': f'paths/{filename}.yaml#/{yaml_escape_path(path_key)}'}
    root['paths'] = root_paths

    # Build components with $ref
    root_components = {}

    root_components['securitySchemes'] = {'$ref': 'components/security-schemes.yaml'}

    root_params = {}
    for param_name in parameters:
        root_params[param_name] = {'$ref': f'components/parameters.yaml#/{param_name}'}
    root_components['parameters'] = root_params

    root_schemas = {}
    for filename, file_schemas in sorted(schema_groups.items()):
        for schema_name in file_schemas:
            root_schemas[schema_name] = {'$ref': f'components/schemas/{filename}.yaml#/{schema_name}'}
    root_components['schemas'] = root_schemas

    root['components'] = root_components

    write_yaml(os.path.join(out_dir, 'openapi.yaml'), root)


def rewrite_refs_in_schemas(schemas_dict, schema_file_map, current_file):
    """
    Rewrite $refs within schema files. If the target schema is in the same file,
    use a local reference. If in a different file, use a relative path.
    """
    if isinstance(schemas_dict, dict):
        if '$ref' in schemas_dict:
            ref = schemas_dict['$ref']
            if ref.startswith('#/components/schemas/'):
                schema_name = ref.split('/')[-1]
                target_file = schema_file_map.get(schema_name, 'common')
                if target_file == current_file:
                    # Same file — just reference by name at root level
                    schemas_dict['$ref'] = f'#/{schema_name}'
                else:
                    schemas_dict['$ref'] = f'{target_file}.yaml#/{schema_name}'
        for key, val in schemas_dict.items():
            if key != '$ref':
                rewrite_refs_in_schemas(val, schema_file_map, current_file)
    elif isinstance(schemas_dict, list):
        for item in schemas_dict:
            rewrite_refs_in_schemas(item, schema_file_map, current_file)


def yaml_escape_path(path_key):
    """Escape path key for use in JSON pointer / YAML $ref."""
    # Replace / with ~1 for JSON pointer (except leading slash captured differently)
    # Actually for YAML $ref with #/paths/..., we need to escape the slashes
    return path_key.replace('~', '~0').replace('/', '~1')


def split_apd_openapi():
    """Split docs/acl-openapi.yaml into docs/acl-openapi/ structure."""
    print("\n=== Splitting acl-openapi.yaml ===")

    src = os.path.join(DOCS_DIR, 'acl-openapi.yaml')
    out_dir = os.path.join(DOCS_DIR, 'acl-openapi')

    with open(src, 'r') as f:
        spec = yaml.safe_load(f)

    # Tag mapping for openapi2
    apd_tag_to_file = {
        'Access Request': 'access-request',
        'Policies': 'policy',
        'Verify': 'verify',
    }

    # Group paths by tag
    path_groups = defaultdict(dict)
    for path_key, path_data in spec.get('paths', {}).items():
        tag = get_tag_for_path(path_key, path_data)
        filename = apd_tag_to_file.get(tag, 'misc')
        path_groups[filename][path_key] = path_data

    # All schemas in one file (only 11)
    schemas = spec.get('components', {}).get('schemas', {})

    # Write path files
    for filename, paths in path_groups.items():
        paths_copy = copy.deepcopy(paths)
        # Rewrite schema refs to point to ../components/schemas.yaml
        rewrite_refs_apd(paths_copy)
        write_yaml(os.path.join(out_dir, 'paths', f'{filename}.yaml'), paths_copy)

    # Write schemas file
    if schemas:
        schemas_copy = copy.deepcopy(schemas)
        rewrite_refs_apd_schemas(schemas_copy)
        write_yaml(os.path.join(out_dir, 'components', 'schemas.yaml'), schemas_copy)

    # Write security schemes
    security_schemes = spec.get('components', {}).get('securitySchemes', {})
    if security_schemes:
        write_yaml(os.path.join(out_dir, 'components', 'security-schemes.yaml'), security_schemes)

    # Write parameters
    parameters = spec.get('components', {}).get('parameters', {})
    if parameters:
        write_yaml(os.path.join(out_dir, 'components', 'parameters.yaml'), parameters)

    # Write root
    root = {
        'openapi': spec['openapi'],
        'info': spec['info'],
        'servers': spec['servers'],
    }

    root_paths = {}
    for filename, paths in sorted(path_groups.items()):
        for path_key in paths:
            root_paths[path_key] = {'$ref': f'paths/{filename}.yaml#/{yaml_escape_path(path_key)}'}
    root['paths'] = root_paths

    root_components = {}
    if security_schemes:
        root_components['securitySchemes'] = {'$ref': 'components/security-schemes.yaml'}
    if parameters:
        root_params = {}
        for param_name in parameters:
            root_params[param_name] = {'$ref': f'components/parameters.yaml#/{param_name}'}
        root_components['parameters'] = root_params
    if schemas:
        root_schemas = {}
        for schema_name in schemas:
            root_schemas[schema_name] = {'$ref': f'components/schemas.yaml#/{schema_name}'}
        root_components['schemas'] = root_schemas

    root['components'] = root_components

    write_yaml(os.path.join(out_dir, 'openapi.yaml'), root)


def rewrite_refs_apd(obj):
    """Rewrite $refs in APD path files."""
    if isinstance(obj, dict):
        if '$ref' in obj:
            ref = obj['$ref']
            if ref.startswith('#/components/schemas/'):
                schema_name = ref.split('/')[-1]
                obj['$ref'] = f'../components/schemas.yaml#/{schema_name}'
            elif ref.startswith('#/components/parameters/'):
                param_name = ref.split('/')[-1]
                obj['$ref'] = f'../components/parameters.yaml#/{param_name}'
        for key, val in obj.items():
            rewrite_refs_apd(val)
    elif isinstance(obj, list):
        for item in obj:
            rewrite_refs_apd(item)


def rewrite_refs_apd_schemas(obj):
    """Rewrite $refs within APD schemas (all in same file, so local refs)."""
    if isinstance(obj, dict):
        if '$ref' in obj:
            ref = obj['$ref']
            if ref.startswith('#/components/schemas/'):
                schema_name = ref.split('/')[-1]
                obj['$ref'] = f'#/{schema_name}'
        for key, val in obj.items():
            if key != '$ref':
                rewrite_refs_apd_schemas(val)
    elif isinstance(obj, list):
        for item in obj:
            rewrite_refs_apd_schemas(item)


def extract_central_paths():
    """Extract the 3 central-specific paths from central-openapi.yaml."""
    print("\n=== Extracting central-specific paths ===")

    src = os.path.join(DOCS_DIR, 'central-openapi.yaml')

    with open(src, 'r') as f:
        spec = yaml.safe_load(f)

    # Find paths that contain 'central' in the key
    central_paths = {}
    for path_key, path_data in spec.get('paths', {}).items():
        if '/central/' in path_key:
            central_paths[path_key] = path_data

    if central_paths:
        # Rewrite $refs in central paths
        central_copy = copy.deepcopy(central_paths)
        schemas = spec.get('components', {}).get('schemas', {})
        schema_file_map = {}
        for schema_name in schemas:
            schema_file_map[schema_name] = SCHEMA_TO_FILE.get(schema_name, 'common')

        # Rewrite refs to point to openapi/ components
        rewrite_refs_central(central_copy, schema_file_map)
        write_yaml(os.path.join(DOCS_DIR, 'central-openapi', 'paths', 'central-catalogue.yaml'), central_copy)

    print(f"  Found {len(central_paths)} central-specific paths")


def rewrite_refs_central(obj, schema_file_map):
    """Rewrite $refs in central paths to point to controlplane-openapi/ components."""
    if isinstance(obj, dict):
        if '$ref' in obj:
            ref = obj['$ref']
            if ref.startswith('#/components/schemas/'):
                schema_name = ref.split('/')[-1]
                target_file = schema_file_map.get(schema_name, 'common')
                obj['$ref'] = f'../../controlplane-openapi/components/schemas/{target_file}.yaml#/{schema_name}'
            elif ref.startswith('#/components/parameters/'):
                param_name = ref.split('/')[-1]
                obj['$ref'] = f'../../controlplane-openapi/components/parameters.yaml#/{param_name}'
        for key, val in obj.items():
            rewrite_refs_central(val, schema_file_map)
    elif isinstance(obj, list):
        for item in obj:
            rewrite_refs_central(item, schema_file_map)


def create_central_root():
    """Create docs/central-openapi/openapi.yaml that combines controlplane paths + central paths."""
    print("\n=== Creating central-openapi/openapi.yaml ===")

    # Read the original central spec for info/servers
    src = os.path.join(DOCS_DIR, 'central-openapi.yaml')
    with open(src, 'r') as f:
        spec = yaml.safe_load(f)

    # Read the main openapi root to get its paths
    main_root_path = os.path.join(DOCS_DIR, 'controlplane-openapi', 'openapi.yaml')
    with open(main_root_path, 'r') as f:
        main_root = yaml.safe_load(f)

    root = {
        'openapi': spec['openapi'],
        'info': spec['info'],
        'servers': spec['servers'],
    }

    # Include all paths from the main spec (via $ref to ../controlplane-openapi/ paths)
    root_paths = {}
    for path_key, path_ref in main_root.get('paths', {}).items():
        ref = path_ref.get('$ref', '')
        if ref:
            root_paths[path_key] = {'$ref': f'../controlplane-openapi/{ref}'}

    # Add central-specific paths
    central_file = os.path.join(DOCS_DIR, 'central-openapi', 'paths', 'central-catalogue.yaml')
    if os.path.exists(central_file):
        with open(central_file, 'r') as f:
            central_paths = yaml.safe_load(f)
        for path_key in central_paths:
            root_paths[path_key] = {'$ref': f'paths/central-catalogue.yaml#/{yaml_escape_path(path_key)}'}

    root['paths'] = root_paths

    # Components — reference the same ../controlplane-openapi/ components
    root_components = {
        'securitySchemes': {'$ref': '../controlplane-openapi/components/security-schemes.yaml'},
    }

    # Parameters
    params_file = os.path.join(DOCS_DIR, 'controlplane-openapi', 'components', 'parameters.yaml')
    if os.path.exists(params_file):
        with open(params_file, 'r') as f:
            params = yaml.safe_load(f)
        root_params = {}
        for param_name in params:
            root_params[param_name] = {'$ref': f'../controlplane-openapi/components/parameters.yaml#/{param_name}'}
        root_components['parameters'] = root_params

    # Schemas
    root_schemas = {}
    for schema_name, schema_ref in main_root.get('components', {}).get('schemas', {}).items():
        ref = schema_ref.get('$ref', '')
        if ref:
            root_schemas[schema_name] = {'$ref': f'../controlplane-openapi/{ref}'}
    root_components['schemas'] = root_schemas

    root['components'] = root_components

    write_yaml(os.path.join(DOCS_DIR, 'central-openapi', 'openapi.yaml'), root)


if __name__ == '__main__':
    print("OpenAPI Spec Splitter")
    print("=" * 50)

    split_main_openapi()
    split_apd_openapi()
    extract_central_paths()
    create_central_root()

    print("\n" + "=" * 50)
    print("Done! Now run 'make docs' to bundle the specs back.")
