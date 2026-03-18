<p align="center"><a href="https://obsidian-java.com/" target="_blank"><img src="https://obsidian-java.com/assets/img/logo.png" width="300" alt="Laravel Logo"></a></p>

<p align="center">
    <a href="https://github.com/laravel/framework/actions">
        <img src="https://img.shields.io/badge/License-MIT-yellow.svg" alt="License: MIT">
    </a>
    <a href="(https://www.oracle.com/java/">
        <img src="https://img.shields.io/badge/Java-17+-orange.svg" alt="Java">
    </a>
    <a href="https://jitpack.io/#obsidian-framework/core">
        <img src="https://jitpack.io/v/obsidian-framework/core.svg" alt="Jitpack latest">
    </a>
</p>

> Core package for the Obsidian Framework

**Full documentation**: [obsidian-java.com](https://obsidian-java.com/)

## Getting Started

To quickly bootstrap a new project using Obsidian Core, use the official skeleton:

**[obsidian-framework/skeleton](https://github.com/obsidian-framework/skeleton)**

The skeleton includes a pre-configured project structure, build scripts, and example code to get you up and running in minutes.

```bash
git clone https://github.com/obsidian-framework/skeleton.git
cd skeleton
mvn clean package
mvn exec:java
```

→ The app runs on `http://localhost:8888`

## Installation

### Maven

Add Jitpack Packages repository

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>
```

Add dependency

```xml
<dependency>
    <groupId>com.github.obsidian-framework</groupId>
    <artifactId>core</artifactId>
    <version>latest</version>
</dependency>
```

## License

MIT © [KainoVaii](https://github.com/KainoVaii)