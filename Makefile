.PHONY: docs docs-lint docs-preview docs-clean

## Bundle split OpenAPI specs into single files for Vert.x
docs:
	npx @redocly/cli bundle docs/controlplane-openapi/openapi.yaml -o docs/openapi.yaml --ext yaml
	npx @redocly/cli bundle docs/acl-openapi/openapi.yaml -o docs/acl-openapi.yaml --ext yaml
	npx @redocly/cli bundle docs/central-openapi/openapi.yaml -o docs/central-openapi.yaml --ext yaml

## Lint all specs
docs-lint:
	npx @redocly/cli lint docs/controlplane-openapi/openapi.yaml
	npx @redocly/cli lint docs/acl-openapi/openapi.yaml
	npx @redocly/cli lint docs/central-openapi/openapi.yaml

## Preview main API docs with Redocly (interactive, hot-reload)
docs-preview:
	npx @redocly/cli preview-docs docs/controlplane-openapi/openapi.yaml

## Preview ACL APD docs
docs-preview-apd:
	npx @redocly/cli preview-docs docs/acl-openapi/openapi.yaml

## Remove bundled output files
docs-clean:
	rm -f docs/openapi.yaml docs/acl-openapi.yaml docs/central-openapi.yaml
