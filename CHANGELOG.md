# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.0.0] - Oct 17, 2021
Add options from scrcpy v1.19 and v1.18 release:

- (Linux Only) Specify V4L2 device (TODO: V4L2Loopback device creation directly from this plugin/tool)
- Display & V4L2 Buffer
- Power off on close (displayed as "Screen off (and lock) on exit" to avoid confusion)
- Now requires minimum IJ v2020.3+
- Minor fixes

## [0.1.4] - Jan 17, 2021

- Auto-select first device from the list (thanks to @rvp-diconium for the suggestion)
- Options for scrcpy v1.17:
    - Added 'Forward all clicks' option
    - Added 'Use legacy paste' and 'Encoder' setting in advanced menu
- Minor fixes here and there

## [0.1.3] - Aug 15, 2020
- Add option to set adb path (use only if scrcpy can't detect ADB Path) (maybe necessary for macOS)

## [0.1.2] - Aug 25, 2020
- Really fix shortcut MOD setting not working (this time I've tested it :P)

## [0.1.1] - Aug 22, 2020
- Fix Shortcut MOD setting not working

## [0.1.0] - Aug 15, 2020
- Added support for "Disable Screensaver" and "Disable repeated keys forward" from scrcpy v1.16
- Added setting for "Shortcut Modifier" in 'Advanced Settings Menu'
- Show scrcpy version info when testing scrcpy path from 'Advanced Settings Menu'
- Fix USB to WiFi not working
- Fix few more bugs

## [0.0.1] - Aug 15, 2020
- Initial Release