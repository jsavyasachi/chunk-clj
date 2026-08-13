# Contributing to chunk-clj

Report bugs, submit fixes, and contribute focused features to `chunk-clj`.

## Before you start

- For any fix that is not trivial, **open an issue first**. We can agree on the
  approach before you spend time.
- Check existing issues and pull requests to avoid duplicate work.

## Development

This is a Clojure library. You need a JDK and [Leiningen](https://leiningen.org/).
A project that migrated to `deps.edn` uses the Clojure CLI instead: see the
README.

```bash
lein test     # run the test suite
lein check    # AOT-compile; must be free of reflection warnings
```

Requirements for a mergeable change:

- **Tests first.** Add or update tests for the behavior you change. For a bug
  fix, include a regression test. It must fail before your fix and pass after it.
- **Green build.** `lein test` passes and `lein check` reports **zero**
  reflection warnings.
- **Keep the scope small.** Keep each pull request to one logical change.

## Commits and pull requests

- Follow [Conventional Commits](https://www.conventionalcommits.org/)
  (`feat:`, `fix:`, `docs:`, `refactor:`, `test:`, `chore:` …).
- Keep the subject in the imperative mood and below about 72 characters.
- Update `CHANGELOG.md` when your change is user-visible.
- Rebase on the latest `main` before opening the pull request.

## License

By contributing, you agree that your contributions will be licensed under the
same license as this project (see `LICENSE` / the README).
