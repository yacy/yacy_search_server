# Repository Instructions

## Web UI, API, Help, And Localization

When changing YaCy web pages or API endpoints, update all matching user-facing and tool-facing artifacts in the same change.

- For `htroot/**/*.html` changes, update the corresponding localization files under `locales/` when visible text, labels, form controls, messages, or navigation text changes.
- For `htroot/**/*.html` changes, update the corresponding Markdown help file under `help/`.
- For API or servlet behavior changes under `source/net/yacy/htroot/**`, update the related `help/**/*.md` file with changed endpoints, access requirements, parameters, side effects, response fields, and automation guidance.
- Treat `locales` and `help` updates as required checklist items for HTML, servlet, and API changes. Do not leave them for a follow-up unless the change is explicitly internal and has no user-visible page, request parameter, response, or behavior impact.

## Tests During Code Reviews

Do not treat the repository-wide test backlog as a separate mass-rewrite project unless explicitly requested. Improve tests incrementally in the context of individual code reviews.

- During each code review, identify the behavior affected by the reviewed code and add, update, or repair focused tests where they provide useful regression coverage.
- Keep test work scoped to the reviewed area and the changes needed to verify it.
- Report unrelated existing test failures, but do not expand the review into a repository-wide test cleanup solely because those failures exist.
- Over time, use successive reviews to bring the test suite up to date alongside the production code.
