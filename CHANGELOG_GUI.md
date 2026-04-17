# GUI fork changes

## Added
- Russian GUI for browsing all Bukkit statistics
- `/stat` with no arguments now opens the GUI for players
- `/stat gui` and `/stat menu` shortcuts
- Action menu for:
  - Top 10
  - My statistic
  - Another player's statistic
- Extra selection flow for typed statistics:
  - entities -> category -> entity -> action
  - blocks/items -> category -> block/item -> action
- Player selector with player heads and optional SkinsRestorer profile loading
- GitHub Actions build workflow
- `.gitignore` for GitHub-ready repository use

## Updated
- Build target moved to Java 21
- API target moved to Paper/Purpur 1.21.10
- plugin.yml updated for 1.21 and SkinsRestorer softdepend
- tab completion includes GUI entry points

## Compatibility notes
- Original text command syntax is kept
- GUI uses the existing stat request engine, so chat output formatting and sharing continue to work

## Build fix

- Removed the dead external `com.tchristofferson:ConfigUpdater:2.0-SNAPSHOT` dependency.
- Embedded a local compatible `ConfigUpdater` implementation so CI and local Maven builds do not depend on an unavailable snapshot.
