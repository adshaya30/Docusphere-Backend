package com.docusphere.backend.documentAction.service;

import com.docusphere.backend.Common.exception.InvalidRequestException;
import com.docusphere.backend.team.service.TeamService;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class HttpTeamAccessValidator implements TeamAccessValidator {
    private final TeamService teamService;

    public HttpTeamAccessValidator(TeamService teamService) {
        this.teamService = teamService;
    }

    @Override
    public boolean isMember(Long userId, UUID teamId) {
        if (userId == null) {
            throw new InvalidRequestException("Invalid user");
        }
        if (teamId == null) {
            throw new InvalidRequestException("Invalid team");
        }

        return teamService.findTeamById(teamId).isPresent()
                && teamService.isUserInTeam(userId, teamId);
    }
}
