# Whitelistr Mod Changelog for Commit `..`

This changelog summarizes the changes made to the Whitelistr mod based on our troubleshooting conversation to address issues with players not being able to join even after cache updates.

**[Initial State - Problem Description]**

* Players were being kicked from the server with "You are not on a Whitelist" message despite the server logs indicating successful cache updates.
* Suspected race condition or timing issue related to cache updates and whitelist checks during player join events.

**[Code Changes & Fixes - Iteration 1 (Focus on Race Condition in `syncIsPlayerWhitelisted`)]**

* **`WClient.java`:**
  * **Modified `syncIsPlayerWhitelisted()` to accept `uuid` as an argument.** This allows checking whitelist status for a specific player.
  * **Crucially, `syncIsPlayerWhitelisted()` now performs `whitelistCache.isPlayerWhitelisted(uuid)` check *after* cache update and *within* the synchronized block.** This ensures the whitelist status is checked against the latest updated cache, eliminating a potential race condition.
  * **`syncIsPlayerWhitelisted()` now returns the boolean result of `whitelistCache.isPlayerWhitelisted(uuid)`.** This returns the actual whitelist status from the updated cache.

* **`PlayerEventHandler.java`:**
  * **Modified `onPlayerJoin()` to call `webSocketClient.syncIsPlayerWhitelisted(uuid)` directly.** This utilizes the updated `syncIsPlayerWhitelisted` method in `WClient`.
  * **Removed the separate `whitelistCache.checkRemoteWhitelist(uuid)` call.**  The `WClient` method now encapsulates the entire synchronous check.
  * **`onPlayerJoin()` now checks the return value of `webSocketClient.syncIsPlayerWhitelisted(uuid)` to determine whitelist status.**
  * **Improved logging in `onPlayerJoin()`** to clarify different stages of the whitelist check process.

**[Code Changes & Enhancements - Iteration 2 (Improved Logging, Debug Mode, Robustness)]**

* **`Cache.java`:**
  * **Enhanced Logging:** Added `[Memory Cache]` and `[SQL Cache]` prefixes to log messages to distinguish between memory and SQL cache operations.
  * **Detailed `removeAllExcept` Logging:**  Log message now includes UUIDs being removed from the memory cache.

* **`ConfigHandler.java`:**
  * **Added `DEBUG_MODE` static field (boolean).**  Controls debug logging throughout the mod.
  * **Added `debug` field (boolean) to `Config` class.**  Configurable via `whitelistr.json`.
  * **`loadConfig()` modified to load `debug` value from config and set `DEBUG_MODE`.**
  * **`saveConfig()` modified to save `DEBUG_MODE` to config.**
  * **Added default value for `websocket_url` in `Config` class.**
  * **Added null check for `config.websocket_url` in `loadConfig()` to prevent potential `NullPointerException`.**

* **`whitelistr.json` (default config file):**
  * **Added `"debug": false` configuration option.**

* **Conditional Logging Implementation (across `Cache.java`, `WClient.java`, `PlayerEventHandler.java`, `Whitelistr.java`):**
  * Implemented conditional logging using `ConfigHandler.DEBUG_MODE`. All `FMLLog.info`, `FMLLog.warning`, and `FMLLog.severe` calls (except critical warnings like kick messages and fatal errors) are now wrapped in `if (ConfigHandler.DEBUG_MODE)` blocks. This allows enabling verbose logging via the config file.

* **`Whitelistr.java` (Main Mod Class):**
  * **Conditional Debug Logging:** Added `if (ConfigHandler.DEBUG_MODE)` checks around `FMLLog.info` calls.
  * **Enhanced Shutdown Logging in `serverStopping()`:**
    * Wrapped `webSocketClient.shutdown()` and `whitelistCache.shutdown()` in `try-catch` blocks to handle shutdown errors gracefully.
    * Added `FMLLog.warning` for shutdown errors.
    * Added debug logs around shutdown operations and resource cleanup completion.
  * **Error Logging in `serverStarting()` for Command Registration:**
    * Wrapped `commands.registerCommands(event);` in a `try-catch` block.
    * Added `FMLLog.warning` for command registration errors.
  * **Clarified Log Messages:** Improved clarity of some log messages for better understanding of mod operations.

**[Important Notes]**

* **Debug Mode:** Enable debug logging by setting `"debug": true` in `Whitelistr/whitelistr.json` for detailed logs during troubleshooting. Remember to disable it (`"debug": false` or remove the line) for normal operation.
* **Online Mode Requirement:** The mod enforces server online mode. Ensure your server is in online mode for the mod to function correctly.

