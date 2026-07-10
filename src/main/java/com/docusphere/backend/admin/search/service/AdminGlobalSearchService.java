package com.docusphere.backend.admin.search.service;

import com.docusphere.backend.admin.search.dto.GlobalSearchResultDTO;
import com.docusphere.backend.admin.search.dto.GlobalSearchResultDTO.UserSearchResult;
import com.docusphere.backend.admin.search.dto.GlobalSearchResultDTO.TeamSearchResult;
import com.docusphere.backend.admin.search.dto.GlobalSearchResultDTO.DocumentSearchResult;
import com.docusphere.backend.authentication.entity.User;
import com.docusphere.backend.authentication.repository.UserRepository;
import com.docusphere.backend.team.entity.Team;
import com.docusphere.backend.team.repository.TeamRepository;
import com.docusphere.backend.document.entity.Document;
import com.docusphere.backend.document.repository.DocumentRepository;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AdminGlobalSearchService {

    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final DocumentRepository documentRepository;

    public AdminGlobalSearchService(UserRepository userRepository,
                                   TeamRepository teamRepository,
                                   DocumentRepository documentRepository) {
        this.userRepository = userRepository;
        this.teamRepository = teamRepository;
        this.documentRepository = documentRepository;
    }

    /**
     * Global search across users, teams, and documents
     */
    public GlobalSearchResultDTO globalSearch(String query) {
        if (query == null || query.trim().isEmpty()) {
            return new GlobalSearchResultDTO();
        }

        String searchTerm = query.toLowerCase();

        // Search users
        List<UserSearchResult> users = searchUsers(searchTerm);

        // Search teams
        List<TeamSearchResult> teams = searchTeams(searchTerm);

        // Search documents
        List<DocumentSearchResult> documents = searchDocuments(searchTerm);

        // Calculate total results
        Long totalResults = (long) (users.size() + teams.size() + documents.size());

        return new GlobalSearchResultDTO(users, teams, documents, totalResults);
    }

    /**
     * Search users by name or email
     */
    private List<UserSearchResult> searchUsers(String searchTerm) {
        List<User> users = userRepository.findAll().stream()
            .filter(u -> (u.getFullName() != null && u.getFullName().toLowerCase().contains(searchTerm))
                      || (u.getEmail() != null && u.getEmail().toLowerCase().contains(searchTerm)))
            .limit(10)
            .collect(Collectors.toList());

        return users.stream()
            .map(user -> {
                UserSearchResult result = new UserSearchResult();
                result.setId(user.getId());
                result.setFullName(user.getFullName());
                result.setEmail(user.getEmail());
                result.setRole(user.getRole() != null ? user.getRole().getName() : "USER");
                return result;
            })
            .collect(Collectors.toList());
    }

    /**
     * Search teams by name
     */
    private List<TeamSearchResult> searchTeams(String searchTerm) {
        List<Team> teams = teamRepository.findAll().stream()
            .filter(t -> t.getTeamName() != null && t.getTeamName().toLowerCase().contains(searchTerm))
            .limit(10)
            .collect(Collectors.toList());

        return teams.stream()
            .map(team -> {
                TeamSearchResult result = new TeamSearchResult();
                result.setId(team.getId().toString());
                result.setTeamName(team.getTeamName());
                result.setMemberCount(team.getMemberCount() != null ? team.getMemberCount() : 0);
                return result;
            })
            .collect(Collectors.toList());
    }

    /**
     * Search documents by file name
     */
    private List<DocumentSearchResult> searchDocuments(String searchTerm) {
        List<Document> documents = documentRepository.findAll().stream()
            .filter(d -> d.getName() != null && d.getName().toLowerCase().contains(searchTerm))
            .limit(10)
            .collect(Collectors.toList());

        return documents.stream()
            .map(doc -> {
                DocumentSearchResult result = new DocumentSearchResult();
                result.setId(doc.getId().toString());
                result.setFileName(doc.getName());
                result.setFileType(doc.getType());
                return result;
            })
            .collect(Collectors.toList());
    }
}
