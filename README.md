<p align="center">
  <a href="https://obsidian-java.com/" target="_blank">
    <img src="https://obsidian-java.com/assets/img/logo.png" width="300" alt="Obsidian Framework">
  </a>
</p>

<p align="center">
  <a href="https://github.com/obsidian-framework/core/blob/main/LICENSE">
    <img src="https://img.shields.io/badge/License-MIT-yellow.svg" alt="License: MIT">
  </a>
  <a href="https://www.oracle.com/java/">
    <img src="https://img.shields.io/badge/Java-17+-orange.svg" alt="Java 17+">
  </a>
  <a href="https://jitpack.io/#obsidian-framework/core">
    <img src="https://jitpack.io/v/obsidian-framework/core.svg" alt="JitPack">
  </a>

  <a href="https://jitpack.io/#obsidian-framework/core">
    <img src="https://github.com/obsidian-framework/core/actions/workflows/ci.yml/badge.svg" alt="workflows">
  </a>
</p>

> Core package for the Obsidian Framework

**Full documentation**: [obsidian-java.com](https://obsidian-java.com/)

## Getting Started

To quickly bootstrap a new project, use the official skeleton:

**[obsidian-framework/obsidian](https://github.com/obsidian-framework/obsidian)**

```bash
git clone https://github.com/obsidian-framework/obsidian my-app
cd my-app
mvn clean package exec:java
```

→ App runs on `http://localhost:8888`

## Installation

### Maven

Add the JitPack repository:

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>
```

Add the dependency:

```xml
<dependency>
    <groupId>com.github.obsidian-framework</groupId>
    <artifactId>core</artifactId>
    <version>latest</version>
</dependency>
```

## License

MIT © [Obsidian Framework](https://github.com/obsidian-framework)