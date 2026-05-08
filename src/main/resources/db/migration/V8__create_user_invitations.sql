CREATE TABLE user_invitations (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    token_hash VARCHAR(128) NOT NULL UNIQUE,
    display_name VARCHAR(255),
    role VARCHAR(64) NOT NULL,
    invited_by_user_id UUID NOT NULL REFERENCES users(id),
    expires_at TIMESTAMPTZ NOT NULL,
    accepted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_user_invitations_email ON user_invitations(email);
CREATE INDEX idx_user_invitations_token_hash ON user_invitations(token_hash);
