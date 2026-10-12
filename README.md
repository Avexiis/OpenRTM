# OpenRTM

OpenRTM is a Windows and Linux desktop app for managing a modified Xbox 360 and working with its games, profiles, and files. It is part of the [Open360](https://discord.gg/C8CqQPp2A5) project. It is intended for RGH, JTAG, and BadUpdate/aBadAvatar consoles.

## What It Does

- Connect to a console, view its status, manage plugins, and transfer or install files.
- Work with supported games, and edit, compile, or inject MW2 GSC menus.
- Edit gamer profiles, achievements, game records, packages, and game-save assignments.
- Browse Xbox storage, work with ISO images, and check key vaults.
- Preview and record a capture device, load modules, and access advanced console tools.

Many file tools work offline. Connected features need a console on the same local network with `XBDM.xex` and `JRPC.xex` or `JRPC2.xex` installed. Keep backups and use only files and game backups you legally own.

**[User Guide](user-docs/README.md)** | **[Getting Started](user-docs/getting-started.md)**

## Information
- OpenRTM is part of the Open360 project, which aims to provide fully open-source alternatives for existing paid and/or closed-source software.  
- Open360 is currently made up of two parts. This project (OpenRTM), as well as [OpenGSC](https://github.com/BakedMike/OpenGSC-MW2) by [BakedMike](https://github.com/BakedMike)  
- The Open360 Discord is located at: https://discord.gg/C8CqQPp2A5

## Build and Run (for developers who may wish to contribute)

Install a **Java 17 development kit (JDK)** and open a terminal in the downloaded or cloned project folder. Keep the complete project, including its bundled libraries. The included Gradle wrapper downloads build dependencies on first use; a separate Gradle installation is not needed.

### Windows

In PowerShell:

```powershell
.\gradlew.bat :app:shadowJar
java -jar .\app\build\libs\OpenRTM.jar
```

### Linux

```sh
chmod +x gradlew
./gradlew :app:shadowJar
java -jar app/build/libs/OpenRTM.jar
```

Both builds produce `app/build/libs/OpenRTM.jar`. Running it requires Java 17 or newer. Video capture and ISO tools are bundled for 64-bit Windows and Linux (x86-64).
