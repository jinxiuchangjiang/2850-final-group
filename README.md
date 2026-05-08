# Game Night — Online Board Games Platform

>**OBG — Online Board Games Platform** —— A full-stack web platform where players can browse board games, create multiplayer rooms, play games live in the browser, track their match history, and manage a friends list.

## Team Members
Haoyan Xing

Yifan Ba

Jincheng Zhou

Fariya Achhab

## Core Function
**For Players:**
| Functional Module | Description |
|---|---|
| Game Browsing | Browse the list of supported board games, search them, view details, rules, and player count. |
| Create / Join Rooms | Create public or private rooms with one click, join the room with strangers, or invite your friends to paly with you. |
| Real‑time Gameplay | WebSocket‑based real‑time game interaction, supporting simultaneous moves across all players. |
| Match History | Every game result are automatically recorded. Review your stats anytime. |
| Friends System | Send friend requests by UID number, see online status, and quickly invite friends to join your game. |

**For Administrators:**
| Functional Module | Description |
|---|---|
| Dashboard | Total users, live online players, live active rooms, total games, tag‑based game pie chart, and daily new accounts over the past 30 days.|
| User Management | Search, ban/unban users, view users' email, time for register, and checke they are online or not. |
| Game Management | Add/remove/edit board games, edit game rules, cover images, max players, etc. |
| Tag Management | Add/remove/edit tags |

## Tech Stack
| Layer | Technology |
|---|---|
| Language | Kotlin 1.9+ |
| Backend framework | Spring Boot 3.x |
| Security | Spring Security + JWT |
| Real-time | Spring WebSocket |
| Persistence | Spring Data JPA + H2 |
| Build tool | Gradle |
| Frontend | Vanilla JS · HTML5 · CSS3 (single-page, no framework) |
| File uploads | Local filesystem via `UploadService` |
 

## Project Structure

```
backend/
|—— gradle/wrapper/  # Gradle wrapper version config
|—— src/main
|  |—— /kotlin/com/obg/
|  |  |—— config/  # Security, WebSocket, resource routing, data seeder
|  |  |—— controller/  # REST API controllers
|  |  |—— model/   # JPA entities and DTOs
|  |  |—— repositry/  # Spring Data JPA repository interfaces
|  |  |—— security/  # JWT generation, validation, auth filter
|  |  |—— service/  # WebSocket relay, game session, online tracker, file upload
|  |  |—— ObgApplication.kt  # Spring Boot entry point
|  |—— src/main/resources/
|  |  |—— static/
|  |  |  |—— css/  # Stylesheets
|  |  |  |—— games/  # Embedded game files
|  |  |  |—— icons/  # Icon assets
|  |  |  |—— js/  # Frontend logic
|  |  |  |—— index.html
|  |  |—— application.yml  # Port, database, JWT and upload path configuration
|—— uploads/
|  |—— couvers/  # Images uploaded by the administrator
|  |—— games/  # Games uploaded by the administrator
|—— build.gradle.kts  # Gradle build script — dependencies and plugins
|—— gradlew  # Startup script of the Gradle Wrapper
|—— gradlew.bat  # Gradle wrapper launch scripts (Linux/Mac & Windows)
|—— settings.gradle.kts  # Project name configuration

```
## Quick Start
**Requirements:**
- JDK 17 or higher ( Java 17, 19, or 21 all work)
- Gradle 8.x or IntelliJ IDEA 2023+

**1, Build & Run**
```bash
cd backend
./gradlew bootRun
```
Or in **IntelliJ IDEA**:
1. Open the `backend/` folder
2. Run `ObgApplication.kt`

The server starts on **http://localhost:8090**

**2, First Login**
| Role   | Username | Password   |
|--------|----------|------------|
| Admin  | admin    | admin123   |
> **Change the admin password immediately after your first login in any non-development environment. Administrator can change the password in the settings.**

- Administrator logs in > **Games** > **Edit Games Gomoku**
> The game Gomoku's details is already in the **Games**.
- Click **Game File** to upload game and save changes.
> Choose the gomoku.html file in the src/main/resources/static/games/

## How Uploading a New Game Works
1. Admin logs in → **Games** → **+ Add Game**
2. Fill title, author, description, rules, tags
3. Upload a cover image
4. Save → game appears in the player game list
5. Players can like it and play online if you wire up WebSocket for it

> Gomoku is the only playable game included. You can add other HTML5 games by uploading their files and adding a room-based launcher (same WebSocket pattern).
