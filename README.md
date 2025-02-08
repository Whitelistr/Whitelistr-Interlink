# <div align="center"> <br> 🛡️ Whitelistr <br> </div>
<p align="center">
  <a href="https://img.shields.io/badge/Minecraft-1.7.10-brightgreen"><img src="https://img.shields.io/badge/Minecraft-1.7.10-brightgreen" alt="Minecraft 1.7.10"></a>
  <a href="https://img.shields.io/badge/Java-1.8-blue"><img src="https://img.shields.io/badge/Java-1.8-blue" alt="Java 1.8"></a>
  <a href="https://img.shields.io/badge/License-CC_BY_NC_ND_4.0-lightgrey"><img src="https://img.shields.io/badge/License-CC_BY_NC_ND_4.0-lightgrey" alt="License CC BY-NC-ND 4.0"></a>
</p>

<br>

**Advanced Whitelisting for Forge Minecraft 1.7.10 Servers**

Whitelistr is a robust and efficient whitelisting solution designed for Forge Minecraft 1.7.10 servers. It combines the speed of **memory caching**, the reliability of a **local SQL database**, and the real-time synchronization of **WebSockets** to deliver a seamless and secure player whitelisting experience.

## ✨ Key Features



### 🔒 Robust Whitelisting System

*   **⚡ Memory Cache for Speed:**  Provides lightning-fast player verification by storing frequently accessed whitelist data in memory, minimizing latency during player joins.
*   **🗄️ Local SQL Cache for Durability:** Leverages a local SQLite database to ensure persistent storage of your whitelist data, offering reliability and data integrity.
*   **🌐 WebSocket Fallback for Real-time Checks:** When a player isn't found in the local cache, Whitelistr seamlessly performs real-time remote checks via WebSocket, ensuring up-to-date whitelist information.
*   **🔄 Automatic Bi-Directional Synchronization:** Keeps your server's whitelist perfectly synchronized with your central whitelist management system through automatic bi-directional synchronization every 5 minutes.
*   **Web Dashboard Management:** Effortlessly manage your server's whitelist through a user-friendly web dashboard interface, providing centralized control and easy administration. 

### Enhanced Security

*   **Server UUID Authentication:** Securely identifies your server using a unique UUID, preventing unauthorized access and ensuring only verified servers can connect.
*   **🔑 API Key Validation:** Implements API key validation to further secure communication and ensure that only authorized requests are processed.
*   **Unique Player Fingerprinting:** Utilize unique player fingerprinting techniques to enhance whitelist security and prevent bypass attempts.

## ⚙️ Technical Implementation

Whitelistr is built with performance and reliability in mind, leveraging the following core components:

| Component         | Technology                  | Purpose                                     |
| :---------------- | :-------------------------- | :------------------------------------------ |
| **Cache Layer**   | SQLite 3.7.2                | Local and persistent whitelist storage      |
| **Network Layer** | Forked Java-WebSocket 1.6.1 | Lightweight & SLF4J-free WebSocket implementation |
| **Data Sync**     | JSON over WebSocket         | Real-time, efficient data synchronization     |

## 🙋 Support and Issues

For any issues, bug reports, or feature requests, please don't hesitate to open an issue on the [GitHub Issues](https://github.com/Whitelistr/Whitelistr-Interlink/issues) page. We appreciate your feedback and contributions!

---
<p align="center">
  <small><i>Made with ❤️ by Avalanche7CZ</i></small>
</p>
