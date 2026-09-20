# SDN Matcher

A local Spring Boot API that screens the supplied customer accounts against an OFAC SDN XML file.

## Prerequisites

- JDK 21 (Temurin recommended)
- Maven 3.6.3 or newer

On macOS with Homebrew:

```bash
brew install --cask temurin@21
brew install maven
```

Verify the installation with `java -version` and `mvn -version`.

## Data setup

1. Download the current SDN XML from the [OFAC Sanctions List Service](https://sanctionslist.ofac.treas.gov/Home/SdnList).
2. Save it as `data/sdn.xml`.

The supplied `accounts.csv` is already placed at `data/accounts.csv`. Override either location with the `ACCOUNTS_PATH` or `SDN_PATH` environment variable.

## Run and test

```bash
mvn test
mvn spring-boot:run
```

The automated suite contains 25 tests covering the confidence decision table, name normalization and threshold behavior, aliases, full/year/approximate DOB parsing, CSV loading, XML parser security, SDN caching and reload behavior, ordered bounded parallel execution, service orchestration, JSON responses, and `404` handling.

Then call:

```bash
curl -X POST http://localhost:8080/screenings
curl http://localhost:8080/screenings/1001
```

## Design

Names are Unicode-normalized, stripped of punctuation, whitespace-collapsed, and compared case-insensitively. Every primary SDN name and alias is evaluated using Apache Commons Text Jaro-Winkler similarity with the required `0.90` threshold. DOB comparison follows the supplied LOW/MED/HIGH decision table, and the highest applicable confidence is returned.

The service parses the SDN XML into an immutable in-memory snapshot and normalizes its primary and alias names once. The snapshot is reused until the file modification time or size changes, at which point it is rebuilt atomically. Account names are likewise normalized once per screening operation.

### Bulk-screening strategy

The supplied data requires approximately 970 million account-to-SDN entry evaluations (`50,000 × 19,393`), before accounting for aliases. Bulk processing uses a correctness-first pipeline:

1. Parse and cache the SDN XML as an immutable snapshot.
2. Normalize every SDN primary name and alias once when that snapshot is built.
3. Normalize each account name once when it is screened.
4. Compare that account with every cached SDN primary name and alias using Jaro-Winkler.
5. Apply the DOB and confidence rules after a name passes the `0.90` threshold.
6. Process independent account batches through a dedicated bounded worker pool while preserving input order.

***Correctness decision: A trigram candidate index was prototyped and dramatically reduced the measured bulk runtime to approximately 16 seconds, but it could theoretically discard a transposed or otherwise misspelled name that would pass the required Jaro-Winkler threshold. For a sanctions-screening application, I chose to prioritize correctness and recall over speed, so trigram filtering was removed from the authoritative matching path.***

Caching and pre-normalization were retained because they improve performance without changing which records are evaluated.

The endpoint remains synchronous and returns the requested JSON array. Bulk accounts are partitioned into at most `availableProcessors - 1` batches and processed by a dedicated fixed-size executor. This parallelizes independent account work without pruning SDN candidates or changing matching semantics, while reserving one processor for the operating system and web server. Results are flattened in original account order. A background-job API was not added because it would change the required immediate response contract and introduce job storage, status, failure, expiration, and polling behavior.

The batch size is calculated as `ceil(accountCount / workerCount)`. For example, a machine reporting eight available processors uses seven workers. With 50,000 accounts, each worker receives approximately 7,143 accounts and screens them against the same immutable cached SDN snapshot. The seven result batches are combined in their original account order.

The XML parser disables DTD and external entity access. Full OFAC dates support exact-date and year matching. Values containing one unambiguous four-digit year (including year-only and approximate values) support year matching only; ranges containing multiple years are ignored.

## AI usage

I used OpenAI Codex as a development assistant. It initially inspected the assessment and scaffolded the Spring Boot project, including the API, repositories, matching service, initial tests, and README. I then reviewed the generated implementation, added my own notes, and traced and explained the controller-to-service-to-repository-to-matching flow to verify that it behaved as intended.

I also asked Codex to clarify the supplied account and OFAC data schemas and to validate the downloaded XML against the parser assumptions. That review identified the standard OFAC `<aka>` alias structure and the mix of full, year-only, and approximate birth dates; the parser and documentation were updated accordingly.

During performance review, I directed Codex to first cache and normalize the SDN data, test that change, then add indexing and retest. My proposed strategy was to use a cheap first pass to identify the lowest-distance or closest potential matches and perform the more expensive comparison only on that reduced group. Codex translated that direction into a trigram candidate-index prototype and helped benchmark it. I then prioritized correctness over speed because an index threshold could omit a name that would pass the assignment's required Jaro-Winkler comparison, particularly in a sanctions-screening context. I directed Codex to remove candidate filtering from the authoritative path while retaining correctness-preserving caching and normalization. After the exhaustive sequential bulk benchmark exceeded 60 seconds, I chose bounded account-level parallelism because it improves throughput without excluding any SDN records or changing the required synchronous API.

I reviewed the test cases, one corrected threshold assumption, the caching, indexing, and parallelism tradeoffs, and the benchmark results. The final 25-test suite passes, and I reviewed the final implementation and remain responsible for its design choices and behavior.
