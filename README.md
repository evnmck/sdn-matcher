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

The automated suite contains 31 tests covering the confidence decision table, name normalization and threshold behavior, aliases, full/year/approximate DOB parsing, CSV loading, XML parser security, SDN caching and reload behavior, conservative candidate selection and fallbacks, ordered bounded parallel execution, service orchestration, JSON responses, and `404` handling.

Then call:

```bash
curl -X POST http://localhost:8080/screenings
curl http://localhost:8080/screenings/1001
```

## Design

Names are Unicode-normalized, stripped of punctuation, whitespace-collapsed, and compared case-insensitively. Every primary SDN name and alias is evaluated using Apache Commons Text Jaro-Winkler similarity with the required `0.90` threshold. DOB comparison follows the supplied LOW/MED/HIGH decision table, and the highest applicable confidence is returned.

The service parses the SDN XML into an immutable in-memory snapshot and normalizes its primary and alias names once. The snapshot is reused until the file modification time or size changes, at which point it is rebuilt atomically. Account names are likewise normalized once per screening operation.

### Bulk-screening strategy

An exhaustive scan of the supplied data requires approximately 970 million account-to-SDN entry evaluations (`50,000 × 19,393`), before accounting for aliases. Bulk processing uses a conservative candidate-selection pipeline:

1. Parse and cache the SDN XML as an immutable snapshot.
2. Normalize every SDN primary name and alias once when that snapshot is built.
3. Normalize each account name once when it is screened.
4. Use a union of 50% trigram overlap, the top 128 trigram candidates, exact tokens, and four-character token prefixes to generate a broad candidate set.
5. Fall back to every SDN entry for names shorter than six characters, names with fewer than three trigrams, or candidate sets smaller than eight entries.
6. Evaluate every selected candidate with the required Jaro-Winkler algorithm and apply the DOB/confidence rules after a name passes the `0.90` threshold.
7. Process independent account batches through a dedicated bounded worker pool while preserving input order.

***Correctness decision: Trigram candidate selection dramatically reduces runtime but can theoretically omit a transposed or otherwise misspelled name that would pass Jaro-Winkler. To reduce that risk while keeping bulk screening practical, the implementation unions several broad retrieval strategies and uses exhaustive fallbacks for short names and suspiciously sparse results. The final Jaro-Winkler and confidence rules remain unchanged.***

Caching and pre-normalization avoid repeated parsing and text cleanup. Candidate selection is deliberately conservative, but unlike an exhaustive scan it cannot provide a mathematical guarantee of identical recall; production use would require recall testing against labeled and adversarial name variants.

On the development machine, a complete 50,000-account request returned 50,000 JSON results in 37.0 seconds from a cold application and 35.2 seconds with the parsed SDN snapshot and candidate index warm. The two responses were byte-for-byte identical. These figures are local measurements rather than service-level guarantees.

The endpoint remains synchronous and returns the requested JSON array. Bulk accounts are partitioned into at most `availableProcessors - 1` batches and processed by a dedicated fixed-size executor. This parallelizes independent account work without pruning SDN candidates or changing matching semantics, while reserving one processor for the operating system and web server. Results are flattened in original account order. A background-job API was not added because it would change the required immediate response contract and introduce job storage, status, failure, expiration, and polling behavior.

The batch size is calculated as `ceil(accountCount / workerCount)`. For example, a machine reporting eight available processors uses seven workers. With 50,000 accounts, each worker receives approximately 7,143 accounts and screens them against the same immutable cached SDN snapshot. The seven result batches are combined in their original account order.

The XML parser disables DTD and external entity access. Full OFAC dates support exact-date and year matching. Values containing one unambiguous four-digit year (including year-only and approximate values) support year matching only; ranges containing multiple years are ignored.

## Production scaling considerations

For production, I would preserve exhaustive matching as the correctness-first default and scale the work around it rather than silently reducing recall:

- Parse each published OFAC list once and persist a versioned, normalized snapshot in an indexed datastore instead of relying only on a single application's memory.
- Partition large submissions and process partitions concurrently with bounded workers sized to available CPU and downstream capacity.
- Move large batches to asynchronous background jobs backed by a durable queue, returning a job ID with status and result endpoints; keep the synchronous endpoint for small requests.
- Cache immutable normalized SDN data and, where appropriate, screening results keyed by the account inputs and SDN-list version so a new list safely invalidates stale results.
- Implement the same conservative candidate retrieval with PostgreSQL trigram indexes or OpenSearch, and measure recall against representative misspellings before setting production thresholds. The exhaustive path should remain available when correctness requirements do not permit candidate pruning.
- Use Lambda for infrequent, bounded internal workloads, or containerized workers such as ECS/Fargate for sustained traffic, larger batches, and predictable resource control.
- Use a relational or search-oriented datastore for sanctions lookup; a key-value store such as DynamoDB may be useful for job state, idempotency records, and high-volume keyed access rather than fuzzy-name search itself.
- Add retries, dead-letter handling, idempotency, audit trails, metrics, alerts, encryption, access controls, and explicit retention policies for sensitive applicant data.

This design allows horizontal worker scaling while keeping matching behavior deterministic, traceable, and tied to the exact SDN-list version used for each decision.

## AI usage

I used OpenAI Codex as a development assistant. It initially inspected the assessment and scaffolded the Spring Boot project, including the API, repositories, matching service, initial tests, and README. I then reviewed the generated implementation, added my own notes, and traced and explained the controller-to-service-to-repository-to-matching flow to verify that it behaved as intended.

I also asked Codex to clarify the supplied account and OFAC data schemas and to validate the downloaded XML against the parser assumptions. That review identified the standard OFAC `<aka>` alias structure and the mix of full, year-only, and approximate birth dates; the parser and documentation were updated accordingly.

During performance review, I directed Codex to first cache and normalize the SDN data, test that change, then add indexing and retest. My proposed strategy was to use a cheap first pass to identify the lowest-distance or closest potential matches and perform the more expensive comparison only on that reduced group. Codex translated that direction into a trigram candidate-index prototype and helped benchmark it. I initially removed candidate filtering because a hard index threshold could omit a name that would pass the assignment's required Jaro-Winkler comparison. When exhaustive bulk execution proved impractically slow, I chose a conservative hybrid: low-threshold and top-N trigram retrieval combined with token and prefix routes, plus exhaustive fallbacks for short names and sparse candidate sets. I also retained bounded account-level parallelism and documented that production adoption would require measured recall.

I reviewed the test cases, one corrected threshold assumption, the caching, indexing, and parallelism tradeoffs, and the benchmark results. The final 31-test suite passes, and I reviewed the final implementation and remain responsible for its design choices and behavior.

## Time spent

Approximately 2 hours total:

- About 30 minutes reviewing the requirements, setting up the data, and scaffolding the application.
- About 45 minutes testing and optimizing bulk processing, evaluating caching, indexing, and parallelism, and ultimately prioritizing matching correctness over the faster trigram approach.
- About 15 minutes reviewing the implementation, improving documentation, and preparing the project for submission.
- About 30 minutes implementing and testing the conservative hybrid candidate selector against the complete local dataset.
