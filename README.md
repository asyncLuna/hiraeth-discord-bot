# Zenith Discord Bot

Zenith is a Spring Boot Discord bot for community utilities, Overwatch information, and server-specific features. It uses Discord4J for Discord interactions, MongoDB for persistent state, and external APIs for animal content and Overwatch data.

## Features

- Overwatch player summaries, competitive ranks, hero data, hero statistics, maps, and top-hero statistics through the OverFast API.
- Discord-to-Overwatch account linking.
- Community tools including anonymous confessions, message purging, colored role creation, configurable server settings, and button-based workflows.
- Member of the Week voting rounds with MongoDB persistence and Quartz scheduling.
- Random animal images and facts through Animality.
- English and Polish translations.
- Optional Sentry error reporting, tracing, profiling, and logs.
- Docker Compose deployment with MongoDB and resource limits suitable for a small bot host.

## Commands

| Command | Purpose |
| --- | --- |
| `/animal` | Send a random animal image and fact. |
| `/confessions` | Enable or disable confession submissions. |
| `/create_role` | Create a colored role and assign it to a user. |
| `/hero_data` | Get data for an Overwatch hero. |
| `/hero_stats` | Browse statistics for a specific hero. |
| `/link` | Link an Overwatch account to a Discord account. |
| `/map` | Browse maps for an Overwatch game mode. |
| `/member_of_the_week` | Manage Member of the Week voting rounds. |
| `/ping` | Check the bot's latency. |
| `/player_summary` | View an Overwatch player's summary and competitive ranks. |
| `/purge_messages` | Delete messages from a member. |
| `/send_button` | Send a predefined button workflow. |
| `/settings` | Configure Zenith for a server. |
| `/top_heroes` | View a player's most-played Overwatch heroes. |
| `/uwulock` | Manage user uwu blocks. |

## Requirements

- Java 21
- Docker and Docker Compose (recommended for MongoDB), or a MongoDB instance
- A Discord application and bot token
- A Discord bot installation with the permissions required by the enabled commands

The bot uses the `GUILDS`, `GUILD_MEMBERS`, `GUILD_MESSAGES`, `MESSAGE_CONTENT`, and `GUILD_MODERATION` gateway intents. Enable the privileged intents in the Discord Developer Portal when required.

## Configuration

The application reads configuration from environment variables. Use [.env.example](.env.example) as a starting point, copy it to `.env`, and replace the placeholder values. Do not commit `.env` files, bot tokens, passwords, or Sentry DSNs.

| Variable | Required | Description |
| --- | --- | --- |
| `DISCORD_BOT_TOKEN` | Yes | Discord bot token. |
| `SPRING_MONGODB_URI` | Yes | MongoDB connection URI. |
| `SENTRY_DSN` | No | Sentry DSN; leave empty to disable event reporting. |
| `SENTRY_SEND_DEFAULT_PII` | No | Whether Sentry may send default PII; defaults to `false`. |
| `SENTRY_LOGS_ENABLED` | No | Whether Sentry log collection is enabled; defaults to `true` in the base profile. |
| `SPRING_PROFILES_ACTIVE` | No | Use `dev` for development logging or `prod` for production settings. |

Member of the Week is configured in `src/main/resources/application.yaml`. Its guild, voting, channel, role, timezone, cron expression, and voting duration can be changed there or overridden with Spring configuration properties.

## Running locally

Start MongoDB and export the required variables in your shell or IDE. For PowerShell:

```powershell
$env:DISCORD_BOT_TOKEN = "your-discord-bot-token"
$env:SPRING_MONGODB_URI = "mongodb://user:password@localhost:27017/zenith?authSource=admin"
$env:SPRING_PROFILES_ACTIVE = "dev"
.\gradlew.bat bootRun
```

Run the test suite with:

```powershell
.\gradlew.bat test
```

Global slash commands are synchronized when the application starts. Discord may take time to propagate global command changes.

## Docker Compose

Copy `.env.example` to `.env` and set values for `MONGO_DATABASE`, `MONGO_USER`, `MONGO_PASSWORD`, `DISCORD_BOT_TOKEN`, and any desired Sentry variables. Build the application JAR first, because the Dockerfile copies the JAR from `build/libs`:

```powershell
Copy-Item .env.example .env
.\gradlew.bat bootJar
docker compose up --build -d
```

View logs with:

```powershell
docker compose logs -f java
```

Stop the services with:

```powershell
docker compose down
```

The MongoDB data is stored in the named `mongo-data` volume. Remove that volume only when you intentionally want to discard the database.

## Project structure

```text
src/main/java/dev/asyncluna/zenith/
├── core/              Shared integrations, models, repositories, services, and utilities
├── discord/            Discord client, command framework, listeners, and features
├── memberoftheweek/   Voting domain, persistence, notifications, and Quartz jobs
└── ZenithDiscordBotApplication.java
```

## Development notes

- The project is built with Gradle and uses the Gradle wrapper.
- Reactive Spring components are used for MongoDB and HTTP integrations.
- `messages.properties` contains English translations; `messages_pl.properties` contains Polish translations.
- The default Member of the Week schedule is Mondays at 18:00 in `Europe/Warsaw`, with a 24-hour voting period.
- Sentry development settings are intentionally more verbose than production settings.

## Contributing

Changes should include appropriate tests where practical. Before opening a pull request, run `./gradlew test` (or `gradlew.bat test` on Windows) and ensure that secrets and local configuration remain untracked.

## License

This project is licensed under the [MIT License](LICENSE).
