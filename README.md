# 🤖 Sokryt.ru Telegram Chat Bot

This is a Spring Boot Telegram bot with a long-polling configuration, built using an event-driven architecture. The bot serves as an administrative tool for managing chats associated with the [Sokryt.ru](https://sokryt.ru) project.

## ⚙️ Features

Currently, the bot has the following capabilities:

- **User Management**:
  - **Ban a User**: Prevent a user from participating in the chat.
  - **Warn a User**: Issue a warning to a user for rule violations.
  - **Auto-Ban on Multiple Warnings**: Automatically ban a user if they accumulate three warnings.
  - **Check Warnings**: View the number of warnings a user has.
  - **Mute/Unmute a User**: Temporarily or permanently silence a user in the chat.

- **Chat Moderation**:
  - **Clear All Messages**: Remove all messages from a chat.
  - **User Verification**: Require users to press a button upon entering to gain chat access.
  - **Auto-Moderate Language**: Mute users for using certain restricted words.

- **Communication**:
  - **Broadcast Messages**: Send messages to all bot users.
  - **Forward Messages to Admin**: Forward messages from users to the bot's creator for review.
  - **Welcome Messages**: Greet new users joining the chat.
  - **Scheduled Messages**: Send recurrent messages based on a defined schedule.

## 🛠 Tech Stack

- **Java Telegram API**: Used for bot communication with the Telegram API.
- **Spring Boot**: Provides a powerful framework for application development and dependency injection.
- **MySQL**: Database to store user and chat information.
- **Redis**: Caching system for faster data access and to improve bot performance.
