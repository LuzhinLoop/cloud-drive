CREATE TABLE IF NOT EXISTS users
(
    id       INT PRIMARY KEY,
    login    VARCHAR(25) UNIQUE NOT NULL,
    password VARCHAR(60)        NOT NULL
);