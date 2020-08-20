<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Serenade for IntelliJ platform Changelog

## [0.0.1] - 2020-08-20
### Added
- Initial scaffold created from [IntelliJ Platform Plugin Template](https://github.com/JetBrains/intellij-platform-plugin-template)
- Support for WebSocket connection to Serenade desktop app
- Tool window showing connection status and retry action
- Support for basic commands including tab navigation, reading and modifying editor state, and clipboard actions.
- Limitations: 
    - Only one project can be connected at once
    - Some commands, such as new file and open file, are not yet implemented
