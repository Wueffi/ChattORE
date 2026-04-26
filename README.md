# ChattORE

Because we want to have a chat system that actually wOREks for us.

## Messaging Commands

| Command                         | Permission                | Description                                              | Aliases                                            |
|---------------------------------|---------------------------|----------------------------------------------------------|----------------------------------------------------|
| `/ac <message>`                 | `chattore.helpop`         | Message ORE Staff                                        | No aliases                                         |
| `/confirmmessage`               | `chattore.confirmmessage` | Confirm a flagged chat message                           | No aliases                                         |
| `/mail mailbox`                 | `chattore.mail`           | Manage your mailbox                                      | `/mailbox\|/mail`                                  |
| `/mail send <player> <message>` | `chattore.mail`           | Send a mail message                                      | No aliases                                         |
| `/mail read <mail ID>`          | `chattore.mail`           | Read a mail message (Designed for usage with `/mailbox`) | No aliases                                         |
| `/message <player> <message>`   | `chattore.message`        | Send a message to a player                               | `/m\|/pm\|/msg\|/vmsg\|/vmessage\|/whisper\|/tell` |
| `/reply <message>`              | `chattore.message`        | Reply to a message                                       | `/playerprofile`                                   |

## Funcommands Commands

| Command                       | Permission             | Description                         | Aliases |
|-------------------------------|------------------------|-------------------------------------|---------|
| `/funcommands`                | `chattore.funcommands` | Display current Funcommands version | `fc`    |
| `/funcommands list`           | `chattore.funcommands` | Display list of all Funcommands     | `fc`    |
| `/funcommands info <command>` | `chattore.funcommands` | Display description of <command>    | `fc`    |

## Nickname Commands

| Command                               | Permission                  | Description                                             | Aliases    |
|---------------------------------------|-----------------------------|---------------------------------------------------------|------------|
| `/nick color <color>+`                | `chattore.nick`             | Set your nickname with at least one color (up to three) | No aliases |
| `/nick presets`                       | `chattore.nick.preset`      | View available presets                                  | No aliases |
| `/nick preset <preset>`               | `chattore.nick.preset`      | Apply a nickname preset                                 | No aliases |
| `/nick nick <player> <nickname>`      | `chattore.nick.others`      | Set a player's nickname                                 | No aliases |
| `/nick remove <player>`               | `chattore.nick.remove`      | Remove a player's nickname                              | No aliases |
| `/nick setgradient <player> <color>+` | `chattore.nick.setgradient` | Set a gradient for a user                               | No aliases |

## Profile Commands

| Command                              | Permission                      | Description                | Aliases          |
|--------------------------------------|---------------------------------|----------------------------|------------------|
| `/profile info <player>`             | `chattore.profile`              | View a player's profile    | `/playerprofile` |
| `/profile about <player>`            | `chattore.profile.about`        | Set your about             | `/playerprofile` |
| `/profile setabout <player> <about>` | `chattore.profile.about.others` | Set another player's about | `/playerprofile` |

## Chat-Bubble Commands

| Command                        | Permission               | Description                                                     | Aliases                   |
|--------------------------------|--------------------------|-----------------------------------------------------------------|---------------------------|
| `/bubble create`               | `chattore.bubble`        | Create ("Blow") a Chat-Bubble                                   | `/bb create\|/blow`       |
| `/bubble invite <player>`      | `chattore.bubble`        | Invite Players to your (private) Chat-Bubble                    | `/bb invite`              |
| `/bubble join <player>`        | `chattore.bubble`        | Join a Player's Chat-Bubble                                     | `/bb join`                |
| `/bubble leave`                | `chattore.bubble`        | Leave your current Chat-Bubble                                  | `/bb leave`               |
| `/bubble delete`               | `chattore.bubble`        | Delete ("Pop") your current Chat-Bubble                         | `/bb delete\|/pop`        |
| `/bubble kick <player>`        | `chattore.bubble`        | Kick a Player from your own Chat-Bubble                         | `/bb kick`                |
| `/bubble setPrivate <boolean>` | `chattore.bubble`        | Set the visibility of your own Chat-Bubble                      | `/bb private`             |
| `/bubble list`                 | `chattore.bubble`        | List all Chat-Bubbles                                           | `/bb list\|/bubbles`      |
| `/shout <message>`             | `chattore.bubble`        | Send a message to global chat when inside a Chat-Bubble         | No aliases                |
| `/bubble forcedelete <player>` | `chattore.bubble.manage` | Force-Delete ("Burst") a specific Chat-Bubble                   | `/bb forcedelete\|/burst` |
| `seeGlobalChat <boolean>`      | `chattore.bubble`        | Toggles the visibility of global chat when inside a Chat-Bubble | `/gc`                     |

## Other Commands

| Command                 | Permission            | Description                   | Aliases    |
|-------------------------|-----------------------|-------------------------------|------------|
| `/chattore reload`      | `chattore.manage`     | Reload Chattore configuration | No aliases |
| `/commandspy`           | `chattore.commandspy` | Toggle spying on commands     | No aliases |
| `/chattore version`     | `chattore.manage`     | View the version of Chattore  | No aliases |
| `/emoji <emoji_names>+` | `chattore.emoji`      | View multiple emojis          | No aliases |
