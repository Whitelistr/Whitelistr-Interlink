# Whitelistr - Whitelisting Mod for Minecraft 1.7.10

![Minecraft 1.7.10](https://img.shields.io/badge/Minecraft-1.7.10-brightgreen)

Advanced whitelisting solution combining local caching with real-time WebSocket synchronization for server player whitelisting.

## Key Features

### Whitelisting System
- **Cache-First Architecture**: Local SQLite database for instant player verification
- **WebSocket Fallback**: Real-time remote checks if local cache misses
- **Auto-Sync**: Bi-directional synchronization every 5 minutes
- **Web Dashboard**: Manage whitelist from a web interface

### Enhanced Security
- Server UUID authentication
- API key validation
- Unique player fingerprinting

### Performance Optimizations
- Async WebSocket communication
- Batch database operations
- Connection pooling


## Technical Implementation

### Core Components
| Component              | Technology                  | Purpose                          |
|------------------------|-----------------------------|----------------------------------|
| Cache Layer            | SQLite 3.44                 | Local whitelist storage          |
| Network Layer          | Forked Java-WebSocket 1.6.0 | SLF4J-free WebSocket implementation |
| Data Sync              | JSON over WebSocket         | Real-time updates                |

