# Completion Tracker (Sammel-Tracker)

Fabric-Mod für Minecraft 26.3. Verfolgt, welche Mobs du getötet, welche Items du gesammelt und welche Fortschritte du erreicht hast, mit Hinweisen zu allem, was noch fehlt.

## Installation

1. [Fabric Loader](https://fabricmc.net/use/) für Minecraft 26.3 und die Fabric API installieren.
2. Unter **Releases** die neueste Version `completiontracker-…+mc26.3.jar` herunterladen.
3. Die Datei in den `mods`-Ordner ziehen (auf Servern: auf Server **und** Client).
4. Im Spiel **J** drücken.

## Lizenz

MIT, siehe [LICENSE](LICENSE). Entwickelt mit Hilfe von KI (Claude von Anthropic); Idee, Design und Tests von LABrwn.

## Veröffentlichung

Alles für CurseForge liegt in [docs/curseforge](docs/curseforge), die Schritt-für-Schritt-Anleitung in [ANLEITUNG.md](docs/curseforge/ANLEITUNG.md).

## Entwicklung

Jeder Push wird von GitHub Actions gebaut (`.github/workflows/build.yml`). Auf `main` landet die .jar als Entwicklungsversion unter Releases. Ein GitHub-Release mit Tag `v1.2.3` baut die offizielle Version und hängt sie an.
