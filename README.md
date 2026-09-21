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
2. Set `hostname` in `plugins/DawnRewards/config.yml` to your server address.
3. Run `dawnrewards register` in the server console. Publish the returned DNS TXT record.
4. Run `dawnrewards verify`. The plugin stores its private credential in `config.yml`. No Dawn website login or application is needed.
5. Configure `rewards.daily` and `rewards.weekly` console commands. `{player}` and `{uuid}` are substituted with the authenticated online player. Empty lists disable a reward. Restart after configuration changes.
6. Players use `/dawnrewards daily` and `/dawnrewards weekly`.

Preserve the configuration and `reward-grants.log` across restarts. Never share the credential. The plugin refuses to start on offline-mode servers. Proxy forwarding requires a future explicit identity adapter and is not supported in this release.

To attach another address to the same server, run `dawnrewards alias other.example.com`, publish its DNS TXT challenge, then run `dawnrewards verifyalias other.example.com`. Shared IPs and arbitrary subdomains are never automatically treated as proof of common ownership.

## Proof transport

The `dawn:affiliate` incoming plugin channel accepts an ASCII opaque signed proof, at most 4096 bytes. The client integration must obtain this audience-scoped proof from Dawn using its own authenticated session. Never transmit the player's Dawn access or refresh token to a Minecraft server. Backend verification binds each proof to the registered server, player UUID, expiry and replay nonce. The plugin snapshots online UUIDs on the main thread, forwards bounded batches every 30 seconds on a separate worker, then discards those proofs. The client must refresh them. It does not trust a plugin-reported device ID, IP or `isDawn` flag.

## Rewards and invoices

The backend owns eligibility and UTC daily/weekly boundaries. A newly granted claim is durably reserved before console commands run, and an already-claimed response never reruns commands. If the process crashes after reservation, the player disconnects, or a reward command fails, an operator must reconcile it manually. Generic console commands cannot provide an atomic transaction with a remote claim service; this design favors preventing duplicates. Use idempotent reward commands when available.

For a completed UTC month, run `dawnrewards invoice YYYY-MM` to create the backend's immutable invoice snapshot. This does not send an invoice or pay funds. Use the snapshot and the public monthly statistics at https://dawn.gg/servers for invoice submission through Dawn support.

## Dependencies

Spigot API is compile-only and supplies the server integration. Gson is bundled for the bounded JSON API protocol. JUnit is test-only. Gradle wrapper uses the same pinned 9.3.0 distribution as DawnLauncher. No client secrets are embedded in the artifact.
