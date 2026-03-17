package com.groupproject.database.schema

enum class GameStatus {
    WAITING,
    PLAYING,
    FINISHED
}

enum class PlayerStatus {
    WAITING,
    PLAYING,
    STAND,
    BUST,
    BLACKJACK
}

enum class GameResult {
    WIN,
    LOSE,
    PUSH
}

enum class ActionType {
    HIT,
    STAND,
    DOUBLE,
    SPLIT,
    DEALER_DRAW
}
