# Serenade for JetBrains

![Build](https://github.com/serenadeai/intellij/workflows/Build/badge.svg)
[![Version](https://img.shields.io/jetbrains/plugin/v/14939-serenade.svg)](https://plugins.jetbrains.com/plugin/14939-serenade)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/14939-serenade.svg)](https://plugins.jetbrains.com/plugin/14939-serenade)

Serenade lets you edit code, run terminal commands, and write documentation with your voice. The Serenade desktop app is required and is freely available at [serenade.ai](https://serenade.ai/).

## Development

This project uses the IntelliJ Platform Gradle Plugin 2.x and targets IntelliJ Platform 2025.3 and newer JetBrains IDEs. The Gradle wrapper downloads the required Gradle and IDE dependencies automatically.

Useful tasks:

- `gradlew.bat buildPlugin` builds the distributable plugin ZIP.
- `gradlew.bat runIde` launches a development IDE with the plugin installed.
- `gradlew.bat verifyPlugin` runs the IntelliJ Plugin Verifier checks.

The plugin connects to the Serenade desktop app over a local WebSocket on port `17373`.

## Installation

In a JetBrains IDE, open Settings > Plugins, choose the gear menu, select **Install Plugin from Disk**, and choose the ZIP produced by `buildPlugin`.
