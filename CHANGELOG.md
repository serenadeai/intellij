<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Serenade for IntelliJ platform Changelog

## [0.0.7] - 2021-04-08
### Added
- Updated dependencies to support IntelliJ branch versions 211+ (2021.1+)

## [0.0.6] - 2021-01-24
### Fixed
- Dependency conflict preventing connection in Windows

## [0.0.5] - 2021-01-14
### Added
- Support for IntelliJ branch versions 203+ (2020.3+)
- Automatic reconnect attempts to desktop client app
- Switches to active project if multiple projects are open
- Support for "open [file]" command

## [0.0.4] - 2020-11-05
### Added
- Support for IntelliJ branch versions 201+ (2020.1+), including latest Android Studio 

## [0.0.3] - 2020-10-07
### Added
- Support for debugger commands

## [0.0.2] - 2020-08-25
### Added
- Plugin icon

## [0.0.1] - 2020-08-20
### Added
- Initial scaffold created from [IntelliJ Platform Plugin Template](https://github.com/JetBrains/intellij-platform-plugin-template)
- Support for WebSocket connection to Serenade desktop app
- Tool window showing connection status and retry action
- Support for basic commands including tab navigation, reading and modifying editor state, and clipboard actions
- Limitations: 
    - Only one project can be connected at once
    - Some commands, such as new file and open file, are not yet implemented
