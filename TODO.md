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
- `HTTPRequestManager.customHTTPHeaderProcessing()` loads and constructs each header processor class by reflection (`Class.forName` + `newInstance`) on every request. The `Class` objects could be cached.
- In `HTTPRequestManager.sendHTTPResponse()`, the condition `(http_code>=300 || http_code<400)` is always true, so the default error body is never sent.
