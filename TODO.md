# TODO

## HTTP request path performance: Tier 2

This was deferred from the HTTP performance review on 25 Sep 2026. Tier 1 of that review shipped in 6.1.9 build 1142.

Each item below needs a decision before work starts. Re-check the code first, because it may have moved on.

### 1. Remove finalizers from per-request objects
These classes have a `finalize()`:

- `HTTPSessionContext`, created about twice per request
- `SessionContext`
- `SystemContext`, one per action via `clone()`
- `HTTPRequestManager`, one per connection

Objects with a finalizer are slower to allocate and collect. All their cleanup runs on the JVM's single finalizer thread, and that includes the JDBC release and "connections were not released correctly" reporting in `HTTPSessionContext.finalize()`.

- **Approach:** do explicit end-of-request cleanup, with `java.lang.ref.Cleaner` as a safety net.
- **Risks:**
  - Apps that pass `pSession` to background threads.
  - Every `new HTTPSessionContext(` call site (AGENDA, SOAP, ...) has to be audited first.

### 2. DB pool connects while holding the pool lock
`puakma.pooler.BasePooler.getItem()` holds the pool monitor during `createItem()`. That covers:

- the `DriverManager.getConnection` network I/O
- a random sleep of up to 1.5s when the connect fails

Every get and release on that pool, including other requests' releases, waits behind a slow connect.

- **Approach:** reserve a slot under the lock, then connect outside it.

### 3. Idle keep-alive connections without a thread each
Each connection still holds a worker thread while it is open. Tier 1 shortened the idle wait (`HTTPKeepAliveTimeout`). An NIO selector could park idle keep-alive sockets without tying up workers, but that is a large redesign.

### 4. Minor
- `SystemContext.clone()` is `synchronized` on the shared context, so every action takes a global lock.
- `HTTPRequestManager.customHTTPHeaderProcessing()` loads and constructs each header processor class by reflection (`Class.forName` + `newInstance`) on every request.
  - **Approach:** cache the `Class[]` for the current `getCustomHeaderProcessors()` array, and rebuild it when the array reference changes on a config reload.
  - Keep creating a new instance per request, because processors hold per-request state.

### 5. Request logging runs on the request thread
Found in a second review on 28 Sep 2026.
- **RDB logging:** `HTTP.writeRDBStatLog()` (HTTPSTAT) and `HTTPServer.writeRDBInboundStatLog()` (HTTPSTATIN) each get a pooled system connection and do an INSERT on the request thread. With those logs on, that adds 1–2 DB round trips to every request, and a slow DB slows every request.
  - **Approach:** a bounded queue drained by one background thread that batch-inserts.
  - **Decision needed:** what to drop when the queue is full, and whether losing queued entries on a crash is acceptable.
- **Text log:** `HTTP.writeTextStatLog()` does one unbuffered `FileOutputStream.write` per request inside a server-wide `synchronized`. It's cheap today. Buffering it would cut syscalls, but lines could be lost on a crash unless it is flushed on a timer.

## Done in the second review (28 Sep 2026)
For reference, so these are not re-investigated:
- **Stats counters:** `AddInStatistic` caches the current time bucket instead of building Calendars on every increment. This also fixed a race that could create duplicate buckets.
- **HTTP dates:** use a shared English `DateTimeFormatter` (`Util.toGMTString()` / `getCurrentGMTString()`). `Locale.UK` had been producing "Sept", which is not a valid HTTP date.
- **gzip:** JPEGs are no longer gzipped. The ETag is hashed before gzip, so a 304 skips the gzip. Page bodies are written directly, and headers are sent in one write.
- **`Util.getMIMELine()`:** no longer creates a substring for every header.
- **`sendHTTPResponse()`:** the always-true body condition is fixed.
- **Byte ranges:** a single range sent the rest of the stream after the range. Multi-range skipped one byte too few, overshot each part, and gave a `Content-Length` one byte too many. Suffix (`-500`) and malformed ranges are now handled, and ranges are no longer applied to error responses.
