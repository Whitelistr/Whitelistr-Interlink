# Whitelistr - Whitelisting Mod for Minecraft 1.7.10

![Minecraft 1.7.10](https://img.shields.io/badge/Minecraft-1.7.10-brightgreen)
![Java](https://img.shields.io/badge/Java-1.8-blue)
![License](https://img.shields.io/badge/License-CC_BY_NC_ND_4.0-lightgrey)

Advanced whitelisting solution combining memory caching and local SQL with real-time WebSocket synchronization for server player whitelisting.

## Key Features

### Whitelisting System
- **Memory Cache**: In-memory cache for fast player verification
- **SQL Cache**: local SQLite database for durability
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
| Cache Layer            | SQLite 3.7.2                | Local whitelist storage          |
| Network Layer          | Forked Java-WebSocket 1.6.1 | SLF4J-free WebSocket implementation |
| Data Sync              | JSON over WebSocket         | Real-time updates                |

## Support

Please report any issues or feature requests on the [GitHub Issues](https://github.com/Whitelistr/Whitelistr-Interlink/issues) page.

