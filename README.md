# Root Runner (One-Click Command Executor)

A simple, lightweight Android application designed to execute shell commands with root privileges easily. Built with Kotlin and Jetpack Compose, featuring a HyperOS-inspired UI.

## Features

* **One-Click Execution:** Run complex shell commands instantly.
* **Root Access:** Powered by `libsu` for stable root handling.
* **HyperOS Style UI:** Clean, modern, and adaptive (Light/Dark mode).
* **Import/Export:**
    * Export your configurations to JSON (saved in `Downloads/quickroot`).
    * Import JSON with options to Overwrite or Append.
* **Multi-Language:** Supports English and Indonesian.

## Installation

1.  Go to the **Releases** page of this repository.
2.  Download the latest `RootRunner.apk`.
3.  Install it on your rooted Android device.
4.  Grant Root permission when prompted.

## Usage

* **First Run:** You will be prompted to create a new command or import a JSON.
* **Adding Commands:** Click the `+` button. You can choose to create manually or import.
    * *Tip:* Use the "Create Another" button to add multiple commands quickly.
* **Sharing:** Click the Share icon in the top bar to preview and export your config.

## Disclaimer

This app requires a **Rooted** device. Use it responsibly. The developer is not responsible for any damage caused by executing improper shell commands.
