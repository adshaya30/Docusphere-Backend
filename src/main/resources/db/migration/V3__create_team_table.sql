CREATE TABLE public.team (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
    description TEXT,
    team_name TEXT NOT NULL,
    member_count INTEGER NOT NULL DEFAULT 0,
    document_count INTEGER NOT NULL DEFAULT 0,

    CONSTRAINT team_pkey PRIMARY KEY (id),
    CONSTRAINT team_team_name_key UNIQUE (team_name)
);