CREATE TABLE public.team_members (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    team_id UUID NOT NULL,
    user_id BIGINT NOT NULL,
    last_seen TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    joined_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    role VARCHAR(20) NOT NULL,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    active BOOLEAN,
    permissions VARCHAR(255),
    metadata TEXT,
    full_name VARCHAR NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT team_members_pkey PRIMARY KEY (id),

    CONSTRAINT team_members_team_id_fkey
        FOREIGN KEY (team_id) REFERENCES team (id),

    CONSTRAINT team_members_user_id_fkey
        FOREIGN KEY (user_id) REFERENCES users (id),

    CONSTRAINT team_members_full_name_fkey
        FOREIGN KEY (full_name) REFERENCES users (full_name)
);