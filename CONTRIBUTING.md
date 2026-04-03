# Contributing to PAL

Thanks for your interest in contributing to PAL!

## Getting Started

1. Fork the repository and clone your fork
2. Set up your environment:
   ```bash
   export PAL_HOME="$(pwd)"
   export PATH="$PAL_HOME/bin:$PAL_HOME/infra/bin:$PATH"
   ```
3. Build the project:
   ```bash
   mvn install -DskipITs
   ```

## Development Requirements

- Java 17
- Maven 3
- Docker (for integration tests)

## Code Conventions

- **Google Java Format**: run `mvn spotless:apply` before committing
- **Javadoc required** for all classes and members (including private/package-private)
- **No fully-qualified names** in code — always use imports
- **JUnit 4** for all tests (not JUnit 5)
- **License headers** must be present — run `build/licensing/license-format.sh`

## Before Submitting a Pull Request

1. Build is clean with no warnings: `mvn install -DskipITs`
2. All unit tests pass
3. Code is formatted: `mvn spotless:apply`
4. License headers are present
5. New functionality has tests
6. Commits follow [Conventional Commits](https://www.conventionalcommits.org/)

## Reporting Bugs

Open an issue at [GitHub Issues](https://github.com/quasientio/pal/issues) with:

- Steps to reproduce
- Expected vs actual behavior
- Java version and OS

## Security Vulnerabilities

Please report security issues privately — see [SECURITY.md](SECURITY.md).

## License

By contributing, you agree that your contributions will be licensed under the same license as the project (see [LICENSE](LICENSE)).
