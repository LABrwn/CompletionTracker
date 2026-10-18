# Hochladen auf CurseForge (Schritt für Schritt)

1. **Offizielle Version erzeugen:** Auf GitHub → *Releases* → *Draft a new release*. Bei *Choose a tag* `v1.0.0` eintippen und „Create new tag“ wählen, Titel `Completion Tracker 1.0.0`, als Text den Abschnitt 1.0.0 aus `CHANGELOG.md`. *Publish release* klicken. Nach etwa 10 Minuten hängt GitHub automatisch `completiontracker-1.0.0+mc26.3.jar` an das Release. Diese Datei herunterladen.
2. **Projekt anlegen:** https://authors.curseforge.com → anmelden → *Create a Project* → Spiel **Minecraft**, Klasse **Mods**.
3. **Angaben:**
   - Name: `Completion Tracker`
   - Summary: Text aus `SUMMARY.txt`
   - Description: Text aus `DESCRIPTION.md` (Editor auf Markdown stellen)
   - Categories: *Map and Information*, *Utility & QoL*
   - License: **MIT License**
   - Source: `https://github.com/LABrwn/MC_All_Mobs_Blocks`, Issues: `…/issues`
   - Avatar: `avatar-400.png`
4. **Datei hochladen:** *Files → Upload File* → die .jar wählen.
   - Release Type: **Release**
   - Game Versions: **26.3**, Mod Loader: **Fabric**, Environment: **Client** und **Server**
   - Changelog: Abschnitt „1.0.0“ aus `CHANGELOG.md`
   - Related Projects: **Fabric API** → *Required Dependency*
5. **Bilder:** Unter *Images* die vier Screenshots hochladen (am besten durch eigene aus deiner Welt ersetzen).
6. **Absenden:** Das Projekt wird von CurseForge geprüft. Bei Änderungswünschen kommt eine Nachricht.

## Neue Versionen später

1. Versionsnummer in `gradle.properties` erhöhen und `CHANGELOG.md` ergänzen.
2. Wie in Schritt 1 ein Release mit dem Tag `v1.0.1` (usw.) anlegen. GitHub hängt die passende .jar automatisch an.
3. Die .jar wie oben bei CurseForge unter *Files* hochladen.
