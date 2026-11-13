# Security notes

## Supported input

The API accepts publicly accessible HTTP and HTTPS media URLs. It rejects credentials embedded in URLs, localhost/local hostnames and DNS results that are not globally routable.

The service does not accept end-user cookies, passwords or session exports. It does not attempt to bypass DRM, paywalls, authentication or other access controls.

## SSRF defense-in-depth

URL validation performs an initial DNS lookup and rejects private, loopback, link-local, reserved and other non-global IP addresses. This reduces obvious SSRF attempts but does **not** guarantee that all yt-dlp network requests stay on the same validated host. Extractors can follow redirects and resolve additional media/CDN URLs.

For any publicly exposed deployment:

1. Put the engine behind an outbound firewall or container network policy.
2. Block loopback, RFC1918 private ranges, IPv6 unique-local/link-local ranges, cloud metadata endpoints and internal service networks at egress.
3. Apply provider-side CPU, memory, disk, bandwidth and request quotas.
4. Keep the API private or add authentication and per-user quotas before allowing untrusted public traffic.
5. Keep FFmpeg and yt-dlp updated.

Do not treat the initial DNS validation as a substitute for network-level egress controls.

## Task isolation and storage

Each download receives its own temporary directory. Download workers are bounded by configuration, and queued tasks are also bounded. Failed/cancelled task directories are removed. Successful files are retained temporarily so clients can fetch them after polling reports completed.

Task IDs are random UUID-derived identifiers, but the current API has no user authentication or ownership model. Anyone who obtains a task ID may query or fetch that task. Do not expose a shared instance to untrusted users without adding authentication and task ownership.

## Single-process state

Task status and rate limits are in memory. Run a single Gunicorn worker per instance. Multiple workers or replicas can route a status request to a process that never received the task. Process restarts also lose in-memory status and task metadata.

Before horizontal scaling, introduce shared task storage/queue (for example Redis plus a worker service), task ownership, and shared artifact storage.

## Resource limits

Settings include maximum video duration, output file size, concurrent downloads, pending queue size, API request size, per-IP request rate and task retention. Free hosting has strict ephemeral disk and bandwidth limits; this code is not an unlimited public-download service.

## Reporting

Do not open public issues containing credentials, cookies, private URLs or personal data. Report security issues privately to the repository maintainers.
