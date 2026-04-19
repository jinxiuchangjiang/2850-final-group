# OBG — Online Board Games Platform

A real, locally-deployable board game platform with:
- **Real-time Gomoku (五子棋)** multiplayer via WebSocket
- Admin panel for managing games, users, tags
- User accounts, friends system, play history
- File uploads (game covers, rule PDFs)
- Persistent H2 database (data survives restarts)

---

## Quick Start

### Requirements
- JDK 17+  (Java 17, 19, or 21 all work)
- Gradle 8.x  **or**  IntelliJ IDEA 2023+

### 1 — Build & Run
```bash
cd backend
./gradlew bootRun          # Linux / Mac
gradlew.bat bootRun        # Windows (if wrapper present)
```

Or in **IntelliJ IDEA**:
1. Open the `backend/` folder
2. Run `ObgApplication.kt`

The server starts on **http://localhost:8090**

### 2 — First Login
| Role   | Username | Password   |
|--------|----------|------------|
| Admin  | admin    | admin123   |

Players register themselves from the login page.

### 3 — Play Gomoku Online
1. Two players log in (different browsers / tabs)
2. Click **Games → Gomoku (五子棋)**
3. Click **▶ Play Online**
4. One player creates a room; the other joins
5. The game starts automatically when both are connected

---

## How Uploading a New Game Works
1. Admin logs in → **Games** → **+ Add Game**
2. Fill title, author, description, rules, tags
3. Upload a cover image
4. Save → game appears in the player game list
5. Players can like it and play online if you wire up WebSocket for it

> Gomoku is the only playable game included. You can add
> other HTML5 games by uploading their files and adding
> a room-based launcher (same WebSocket pattern).

---

## Database
- Dev: H2 file DB at `./data/obgdb.mv.db` — persists across restarts
- H2 console: http://localhost:8090/h2-console
  - JDBC URL: `jdbc:h2:file:./data/obgdb`
  - User: `sa`  Password: *(empty)*

## Production (PostgreSQL)
1. Uncomment PostgreSQL lines in `application.yml` and `build.gradle.kts`
2. Change `ddl-auto: validate`
3. Set a real JWT secret (32+ chars)
4. `./gradlew bootJar` → run the JAR

---

## Project Layout
```
backend/
  src/main/kotlin/com/obg/
    config/         WebSocketConfig, SecurityConfig, AppConfig (seeder)
    controller/     REST endpoints (Auth, Games, Admin, Tags, Rooms, Friends)
    model/          JPA entities + DTOs
    repository/     Spring Data repositories
    security/       JwtService, JwtAuthFilter, UserDetailsService
    service/        GomokuWebSocketHandler, UploadService
  src/main/resources/
    static/         Frontend (HTML/CSS/JS — served by Spring Boot)
    application.yml
```
