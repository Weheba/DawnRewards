# DawnRewards

Server-side rewards plugin for Dawn's server affiliate program. Target compatibility: **Paper 1.20.1 through 26.3**. Folia, standalone Spigot, and proxy installations are outside the supported range for this release.

## Server compatibility

The plugin compiles against the Bukkit API from 1.20.1, which Paper implements, and uses `api-version: '1.20'` so older supported servers can load it. Java 17 bytecode preserves the older server baseline. Run each Paper server with its required Java version; the plugin's bytecode target does not mean newer Paper versions run on Java 17. Paper's current guidance recommends Java 21 for 1.20 through 1.21.11 and Java 25 for 26.1 onward.

The build and automated tests validate the 1.20.1 API baseline. The full Paper 1.20.1 through 26.3 runtime matrix has not been tested. The upper endpoint is a compatibility target, not a claim that every server version has been verified.

References: [Paper Java requirements](https://docs.papermc.io/paper/getting-started/), [plugin API version](https://docs.papermc.io/paper/dev/plugin-yml/), and [Paper version format](https://docs.papermc.io/paper/dev/project-setup/).

## Rollout status

This is an integration build. Payout accounting is disabled by default in the companion backend PR. A trusted Dawn proof issuer and client transport must ship before qualified rewards or payable CCU can be enabled. Client brand strings and the existing DawnServerAPI hello are not accepted as proof. Do not advertise this artifact as a working payout program before that integration is verified.

## Install and register

1. Build with `./gradlew test jar`, then copy `build/libs/DawnRewards-0.1.0.jar` into `plugins/` and restart.
2. Sign in at https://dawn.gg/servers/account and verify your registrable root domain with the supplied DNS TXT record. This covers its subdomains. Public statistics remain available without signing in.
3. Generate plugin credentials for your join address in the server account dashboard. Stop the server, then copy the returned hostname, server ID, plugin token and sequence into `hostname`, `server-id`, `credential` and `sequence` in `plugins/DawnRewards/config.yml`.
4. Restart and wait for the plugin's first check-in. You can then publish a Partner Servers listing from the dashboard. Backgrounds must pass image moderation before they can be published. No partner application is required.
5. Configure `rewards.daily` and `rewards.weekly` console commands. `{player}` and `{uuid}` are substituted with the authenticated online player. Empty lists disable a reward. Restart after configuration changes.
6. Players use `/dawnrewards daily` and `/dawnrewards weekly`.

Preserve the configuration and `reward-grants.log` across restarts. Never share the credential. The plugin refuses to start on offline-mode servers. Proxy forwarding requires a future explicit identity adapter and is not supported in this release.

If a credential is lost, explicitly rotate it in the dashboard, stop the server and replace both the credential and sequence with the returned values before restarting. Rotation invalidates the previous credential.

The older console `register` and `verify` commands remain available for integration testing, but do not link a server to a Dawn owner account. Console aliases must stay within the same registrable domain. Shared IPs do not establish ownership.

## Proof transport

The `dawn:affiliate` incoming plugin channel accepts an ASCII opaque signed proof, at most 4096 bytes. The client integration must obtain this audience-scoped proof from Dawn using its own authenticated session. Never transmit the player's Dawn access or refresh token to a Minecraft server. Backend verification binds each proof to the registered server, player UUID, expiry and replay nonce. The plugin snapshots online UUIDs on the main thread, forwards bounded batches every 30 seconds on a separate worker, then discards those proofs. The client must refresh them. It does not trust a plugin-reported device ID, IP or `isDawn` flag.

## Rewards and invoices

The backend owns eligibility and UTC daily/weekly boundaries. A newly granted claim is durably reserved before console commands run, and an already-claimed response never reruns commands. If the process crashes after reservation, the player disconnects, or a reward command fails, an operator must reconcile it manually. Generic console commands cannot provide an atomic transaction with a remote claim service; this design favors preventing duplicates. Use idempotent reward commands when available.

For a completed UTC month, save private payment details and submit your invoice at https://dawn.gg/servers/account. The console `dawnrewards invoice YYYY-MM` command produces an estimate snapshot only; it is not a separate payable submission and does not transfer funds. The owner dashboard and listing workflow require the companion backend and website updates to be deployed.

## Dependencies

Spigot API is compile-only and supplies the server integration. Gson is bundled for the bounded JSON API protocol. JUnit is test-only. Gradle wrapper uses the same pinned 9.3.0 distribution as DawnLauncher. No client secrets are embedded in the artifact.
