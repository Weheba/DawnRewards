# DawnRewards

Daily rewards for players who join through the Dawn Client. Runs on Paper and Folia, 1.20.6 through 26.2, with one jar.

## Selling Dawn cosmetics on Tebex

A store purchase can grant a Dawn cosmetic to the buyer's Dawn account.

1. Put the cosmetic grant token from Dawn in `config.yml` under `cosmetic-grant-token`. It is a separate credential from `plugin-token`. Then run `dawnrewards reload`.
2. In the Tebex package, add a command that runs on purchase:

   ```
   dawnrewards grantcosmetic {username} donut-bf-cape {transaction}
   ```

   `{username}` is the buyer's Minecraft name and `{transaction}` is the Tebex transaction id. Replace `donut-bf-cape` with the cosmetic slug Dawn gave you. The command does not need the player to be online.

The command is console only: `dawnrewards grantcosmetic <player> <cosmetic> [delivery-id]`.

Each grant is saved before it is sent, so nothing is lost when Dawn cannot be reached:

- Errors from Dawn's side (rate limits, server errors, timeouts) are retried after 1 minute, doubling up to once an hour.
- If no Dawn account has that name yet, the grant is retried from every 15 minutes up to every 6 hours, in case the buyer signs up later.
- Retries stop after 7 days and the grant is logged as given up.
- A rejected token or a cosmetic Dawn will not grant is logged as an error and not retried.
- The same player, cosmetic and transaction is only granted once, so Tebex resending the command does nothing.
- If the buyer is online when the grant goes through, they get the `cosmetic-granted` message from `messages.yml`.

Admin commands:

- `dawnrewards grants` lists pending, failed and given up grants.
- `dawnrewards grants retry` sends pending grants now, and queues failed and given up ones again for another 7 days. Use it after fixing the token or the slug.
