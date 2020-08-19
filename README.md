# Serenade for IntelliJ

![Build](https://github.com/serenadeai/intellij/workflows/Build/badge.svg)
[![Version](https://img.shields.io/jetbrains/plugin/v/PLUGIN_ID.svg)](https://plugins.jetbrains.com/plugin/PLUGIN_ID)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/PLUGIN_ID.svg)](https://plugins.jetbrains.com/plugin/PLUGIN_ID)

## Template ToDo list
- [x] Create a new [IntelliJ Platform Plugin Template][template] project.
- [ ] Verify the [pluginGroup](/gradle.properties), [plugin ID](/src/main/resources/META-INF/plugin.xml) and [sources package](/src/main/kotlin).
- [ ] Review the [Legal Agreements](https://plugins.jetbrains.com/docs/marketplace/legal-agreements.html).
- [ ] [Publish a plugin manually](https://www.jetbrains.org/intellij/sdk/docs/basics/getting_started/publishing_plugin.html) for the first time.
- [ ] Set the Plugin ID in the above README badges.
- [ ] Set the [Deployment Token](https://plugins.jetbrains.com/docs/marketplace/plugin-upload.html).
- [ ] Click the <kbd>Watch</kbd> button on the top of the [IntelliJ Platform Plugin Template][template] to be notified about releases containing new features and fixes.

<!-- Plugin description -->
Serenade for IntelliJ includes editor support and tab management.
<!-- Plugin description end -->

## Prerequisites

1. IntelliJ IDEA 2020.2 (latest)
1. 
    ```
    brew install ktlint
    ktlint installGitPreCommitHook
    ```

## Development

1. In IntelliJ IDEA, add the Plugin SDK: https://jetbrains.org/intellij/sdk/docs/basics/getting_started/setting_up_environment.html
1. `ktlint -F "**/*.kt*"` should find and fix any lint issues.
