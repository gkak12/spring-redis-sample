CREATE TABLE users (
    id       BIGINT                        NOT NULL AUTO_INCREMENT,
    username VARCHAR(50)                   NOT NULL,
    password VARCHAR(255)                  NOT NULL,
    email    VARCHAR(100)                  NOT NULL,
    role     ENUM('USER', 'ADMIN')         NOT NULL DEFAULT 'USER',

    PRIMARY KEY (id),
    UNIQUE KEY uq_users_username (username),
    UNIQUE KEY uq_users_email (email)
);

CREATE TABLE stores (
    id        BIGINT                                                                    NOT NULL AUTO_INCREMENT,
    name      VARCHAR(100)                                                              NOT NULL,
    address   VARCHAR(255)                                                              NOT NULL,
    latitude  DOUBLE                                                                    NOT NULL,
    longitude DOUBLE                                                                    NOT NULL,
    category  ENUM('CONVENIENCE_STORE', 'CAFE', 'RESTAURANT', 'PHARMACY', 'OTHER')    NOT NULL DEFAULT 'CONVENIENCE_STORE',
    phone     VARCHAR(20),

    PRIMARY KEY (id)
);
